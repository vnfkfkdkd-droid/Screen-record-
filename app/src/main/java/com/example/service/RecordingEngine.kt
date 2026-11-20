package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.example.data.RecordingsRepository
import com.example.data.RecordingOutputTarget
import com.example.model.AudioSourceMode
import com.example.model.RecorderSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RecordingEngine(
    private val context: Context,
    private val recordingsRepository: RecordingsRepository,
    private val onAudioAmplitude: (Float) -> Unit,
    private val onSystemStopped: () -> Unit
) {
    companion object {
        private const val TAG = "D7nRecordingEngine"
        private const val TIMEOUT_US = 10_000L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var internalAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null
    private var mediaMuxer: MediaMuxer? = null
    private var outputTarget: RecordingOutputTarget? = null

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val activeAudioEnabled = AtomicBoolean(false)

    private val muxerLock = Object()
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val wroteVideoFrames = AtomicInteger(0)

    private var pauseStartNs = 0L
    @Volatile
    private var totalPausedUs = 0L
    private var lastVideoPtsUs = -1L

    private var videoDrainThread: Thread? = null
    private var audioCaptureThread: Thread? = null
    private var audioDrainThread: Thread? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system")
            if (isRunning.get()) {
                onSystemStopped()
            }
        }
    }

    fun start(projection: MediaProjection, settings: RecorderSettings): RecordingOutputTarget {
        mediaProjection = projection
        val target = recordingsRepository.createOutputTarget()
            ?: throw IllegalStateException("Unable to create output video file")
        outputTarget = target

        val (width, height, densityDpi) = resolveVideoGeometry(settings.resolution.shortSidePx)

        val muxer = when (target) {
            is RecordingOutputTarget.MediaStoreTarget -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    MediaMuxer(target.pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                } else {
                    throw IllegalStateException("MediaStore target requires API 26+")
                }
            }
            is RecordingOutputTarget.FileTarget -> {
                MediaMuxer(target.file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
        }
        mediaMuxer = muxer

        // Configure H.264 Video Encoder
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, settings.videoBitrate.bps)
            setInteger(MediaFormat.KEY_FRAME_RATE, settings.fps.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val vEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        videoEncoder = vEncoder
        inputSurface = vEncoder.createInputSurface()
        vEncoder.start()

        // Configure Audio Capture + AAC Encoder if requested and permitted
        val shouldRecordAudio = setupAudioPipeline(projection, settings)
        activeAudioEnabled.set(shouldRecordAudio)

        // Register MediaProjection callback BEFORE creating VirtualDisplay (required on Android 14+)
        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        virtualDisplay = projection.createVirtualDisplay(
            "d7n_screen_capture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        )

        isRunning.set(true)
        isPaused.set(false)
        totalPausedUs = 0L
        lastVideoPtsUs = -1L
        wroteVideoFrames.set(0)
        videoTrackIndex = -1
        audioTrackIndex = -1
        muxerStarted = false

        videoDrainThread = Thread({ drainVideoEncoder() }, "d7n-video-drain").apply { start() }

        if (shouldRecordAudio) {
            audioDrainThread = Thread({ drainAudioEncoder() }, "d7n-audio-drain").apply { start() }
            audioCaptureThread = Thread({
                captureAndEncodeAudio(settings)
            }, "d7n-audio-capture").apply { start() }
        }

        return target
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioPipeline(projection: MediaProjection, settings: RecorderSettings): Boolean {
        val mode = settings.audioSource
        if (mode == AudioSourceMode.NONE) return false

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPerm) {
            Log.w(TAG, "RECORD_AUDIO permission missing; recording video only")
            return false
        }

        val sampleRate = settings.audioSampleRate.hz
        val channelCount = 2

        return try {
            if (mode.usesInternalAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(8192)

                val playbackFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()

                internalAudioRecord = AudioRecord.Builder()
                    .setAudioFormat(playbackFormat)
                    .setBufferSizeInBytes(minBuf * 2)
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build()

                if (internalAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    internalAudioRecord?.release()
                    internalAudioRecord = null
                }
            }

            if (mode.usesMic) {
                val minMicBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                micAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minMicBuf * 2
                )

                if (micAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    micAudioRecord?.release()
                    micAudioRecord = null
                }
            }

            if (internalAudioRecord == null && micAudioRecord == null) {
                return false
            }

            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, settings.audioBitrate.bps)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
            }

            val aEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            audioEncoder = aEncoder
            internalAudioRecord?.startRecording()
            micAudioRecord?.startRecording()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to setup audio pipeline, falling back to silent capture", t)
            runCatching { internalAudioRecord?.release() }
            runCatching { micAudioRecord?.release() }
            runCatching { audioEncoder?.release() }
            internalAudioRecord = null
            micAudioRecord = null
            audioEncoder = null
            false
        }
    }

    fun pause() {
        if (!isRunning.get() || isPaused.get()) return
        pauseStartNs = System.nanoTime()
        isPaused.set(true)
        onAudioAmplitude(0f)
    }

    fun resume() {
        if (!isRunning.get() || !isPaused.get()) return
        val pausedNs = (System.nanoTime() - pauseStartNs).coerceAtLeast(0L)
        totalPausedUs += pausedNs / 1000L
        isPaused.set(false)
        runCatching {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            videoEncoder?.setParameters(params)
        }
    }

    fun stop(elapsedMillis: Long): Boolean {
        if (!isRunning.getAndSet(false)) return false
        if (isPaused.getAndSet(false)) {
            val pausedNs = (System.nanoTime() - pauseStartNs).coerceAtLeast(0L)
            totalPausedUs += pausedNs / 1000L
        }

        onAudioAmplitude(0f)

        runCatching { videoEncoder?.signalEndOfInputStream() }
        runCatching { audioCaptureThread?.join(1500) }
        runCatching { videoDrainThread?.join(2500) }
        runCatching { audioDrainThread?.join(1500) }

        synchronized(muxerLock) {
            muxerLock.notifyAll()
        }

        releaseResources()

        val success = wroteVideoFrames.get() > 0
        outputTarget?.let { target ->
            recordingsRepository.finalizeOutputTarget(target, elapsedMillis, success)
        }
        outputTarget = null
        return success
    }

    private fun drainVideoEncoder() {
        val encoder = videoEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val status = try {
                encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            } catch (e: Exception) {
                Log.e(TAG, "Video dequeue error", e)
                break
            }

            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isRunning.get()) continue
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(muxerLock) {
                    if (videoTrackIndex == -1) {
                        videoTrackIndex = mediaMuxer?.addTrack(encoder.outputFormat) ?: -1
                        tryStartMuxerLocked()
                    }
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null) {
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && bufferInfo.size > 0 && !isPaused.get()) {
                        waitForMuxerStart()
                        if (muxerStarted && videoTrackIndex >= 0) {
                            val adjustedPts = (bufferInfo.presentationTimeUs - totalPausedUs).coerceAtLeast(0L)
                            val monotonicPts = if (adjustedPts <= lastVideoPtsUs) {
                                lastVideoPtsUs + 1_000L
                            } else {
                                adjustedPts
                            }
                            lastVideoPtsUs = monotonicPts
                            bufferInfo.presentationTimeUs = monotonicPts

                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    runCatching {
                                        mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                        wroteVideoFrames.incrementAndGet()
                                    }
                                }
                            }
                        }
                    }
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun captureAndEncodeAudio(settings: RecorderSettings) {
        val encoder = audioEncoder ?: return
        val internal = internalAudioRecord
        val mic = micAudioRecord
        val sampleRate = settings.audioSampleRate.hz
        val framesPerChunk = 1024
        val stereoShortCount = framesPerChunk * 2

        val internalShorts = if (internal != null) ShortArray(stereoShortCount) else null
        val micShorts = if (mic != null) ShortArray(framesPerChunk) else null
        val mixedShorts = ShortArray(stereoShortCount)
        val byteChunk = ByteArray(stereoShortCount * 2)

        var totalFramesEncoded = 0L
        var lastUiMeterTime = 0L
        val micGain = if (settings.micBoost) 1.45f else 1.0f

        while (isRunning.get()) {
            val internalRead = if (internal != null && internalShorts != null) {
                internal.read(internalShorts, 0, stereoShortCount)
            } else 0

            val micRead = if (mic != null && micShorts != null) {
                mic.read(micShorts, 0, framesPerChunk)
            } else 0

            if (isPaused.get()) {
                continue
            }

            val framesAvailable = when {
                internal != null && mic != null -> min(internalRead / 2, micRead)
                internal != null -> internalRead / 2
                mic != null -> micRead
                else -> 0
            }
            if (framesAvailable <= 0) continue

            var sumSquares = 0.0
            for (i in 0 until framesAvailable) {
                val inL = if (internalShorts != null && i * 2 < internalRead) internalShorts[i * 2].toInt() else 0
                val inR = if (internalShorts != null && i * 2 + 1 < internalRead) internalShorts[i * 2 + 1].toInt() else 0
                val m = if (micShorts != null && i < micRead) (micShorts[i] * micGain).roundToInt() else 0

                val mixedL = (inL + m).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                val mixedR = (inR + m).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                mixedShorts[i * 2] = mixedL
                mixedShorts[i * 2 + 1] = mixedR

                val norm = mixedL / 32768.0
                sumSquares += norm * norm
            }

            val now = System.currentTimeMillis()
            if (now - lastUiMeterTime >= 65L) {
                lastUiMeterTime = now
                val rms = sqrt(sumSquares / framesAvailable).toFloat()
                onAudioAmplitude((rms * 3.8f).coerceIn(0f, 1f))
            }

            val byteCount = framesAvailable * 4
            ByteBuffer.wrap(byteChunk, 0, byteCount)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .put(mixedShorts, 0, framesAvailable * 2)

            val inputIndex = try {
                encoder.dequeueInputBuffer(TIMEOUT_US)
            } catch (_: Exception) {
                -1
            }

            if (inputIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val writeLen = min(byteCount, inputBuffer.remaining())
                    inputBuffer.put(byteChunk, 0, writeLen)
                    val ptsUs = (totalFramesEncoded * 1_000_000L) / sampleRate
                    encoder.queueInputBuffer(inputIndex, 0, writeLen, ptsUs, 0)
                    totalFramesEncoded += (writeLen / 4)
                }
            }
        }

        // Send EOS to AAC encoder
        val eosIndex = runCatching { encoder.dequeueInputBuffer(TIMEOUT_US) }.getOrDefault(-1)
        if (eosIndex >= 0) {
            val ptsUs = (totalFramesEncoded * 1_000_000L) / sampleRate
            runCatching {
                encoder.queueInputBuffer(eosIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }
    }

    private fun drainAudioEncoder() {
        val encoder = audioEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val status = try {
                encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            } catch (e: Exception) {
                Log.e(TAG, "Audio dequeue error", e)
                break
            }

            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isRunning.get()) continue
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(muxerLock) {
                    if (audioTrackIndex == -1) {
                        audioTrackIndex = mediaMuxer?.addTrack(encoder.outputFormat) ?: -1
                        tryStartMuxerLocked()
                    }
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null) {
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && bufferInfo.size > 0) {
                        waitForMuxerStart()
                        if (muxerStarted && audioTrackIndex >= 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    runCatching {
                                        mediaMuxer?.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                                    }
                                }
                            }
                        }
                    }
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun tryStartMuxerLocked() {
        val audioReady = !activeAudioEnabled.get() || audioTrackIndex != -1
        if (!muxerStarted && videoTrackIndex != -1 && audioReady) {
            runCatching {
                mediaMuxer?.start()
                muxerStarted = true
            }
            muxerLock.notifyAll()
        }
    }

    private fun waitForMuxerStart() {
        synchronized(muxerLock) {
            val deadline = System.currentTimeMillis() + 1500L
            while (!muxerStarted && isRunning.get() && System.currentTimeMillis() < deadline) {
                try {
                    muxerLock.wait(100L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun releaseResources() {
        runCatching { internalAudioRecord?.stop() }
        runCatching { internalAudioRecord?.release() }
        internalAudioRecord = null

        runCatching { micAudioRecord?.stop() }
        runCatching { micAudioRecord?.release() }
        micAudioRecord = null

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { inputSurface?.release() }
        inputSurface = null

        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null

        runCatching { audioEncoder?.stop() }
        runCatching { audioEncoder?.release() }
        audioEncoder = null

        synchronized(muxerLock) {
            if (muxerStarted) {
                runCatching { mediaMuxer?.stop() }
            }
            runCatching { mediaMuxer?.release() }
            mediaMuxer = null
            muxerStarted = false
        }

        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    private fun resolveVideoGeometry(targetShortSide: Int): Triple<Int, Int, Int> {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val rawWidth: Int
        val rawHeight: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            rawWidth = bounds.width()
            rawHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            rawWidth = metrics.widthPixels
            rawHeight = metrics.heightPixels
        }

        val densityDpi = context.resources.displayMetrics.densityDpi
        val actualShort = min(rawWidth, rawHeight).coerceAtLeast(360)
        val scale = (targetShortSide.toFloat() / actualShort.toFloat()).coerceAtMost(1f)

        val scaledW = ((rawWidth * scale).roundToInt() / 16) * 16
        val scaledH = ((rawHeight * scale).roundToInt() / 16) * 16

        return Triple(scaledW.coerceAtLeast(320), scaledH.coerceAtLeast(320), densityDpi)
    }
}
