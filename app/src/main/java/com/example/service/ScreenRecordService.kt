package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RecordingsRepository
import com.example.model.AudioBitrate
import com.example.model.AudioSampleRate
import com.example.model.AudioSourceMode
import com.example.model.RecorderSettings
import com.example.model.RecordingStatus
import com.example.model.VideoBitrate
import com.example.model.VideoFps
import com.example.model.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_RECORDING"
        const val ACTION_PAUSE = "com.example.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.example.action.RESUME_RECORDING"
        const val ACTION_STOP = "com.example.action.STOP_RECORDING"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_AUDIO_SOURCE = "extra_audio_source"
        const val EXTRA_RESOLUTION = "extra_resolution"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_AUDIO_BITRATE = "extra_audio_bitrate"
        const val EXTRA_AUDIO_SAMPLE_RATE = "extra_audio_sample_rate"
        const val EXTRA_MIC_BOOST = "extra_mic_boost"

        private const val CHANNEL_ID = "d7n_recording_channel"
        private const val NOTIFICATION_ID = 7001

        fun buildStartIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            settings: RecorderSettings
        ): Intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_RESULT_DATA, resultData)
            putExtra(EXTRA_AUDIO_SOURCE, settings.audioSource.key)
            putExtra(EXTRA_RESOLUTION, settings.resolution.shortSidePx)
            putExtra(EXTRA_FPS, settings.fps.fps)
            putExtra(EXTRA_VIDEO_BITRATE, settings.videoBitrate.bps)
            putExtra(EXTRA_AUDIO_BITRATE, settings.audioBitrate.bps)
            putExtra(EXTRA_AUDIO_SAMPLE_RATE, settings.audioSampleRate.hz)
            putExtra(EXTRA_MIC_BOOST, settings.micBoost)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recordingsRepository: RecordingsRepository
    private var recordingEngine: RecordingEngine? = null

    private var timerJob: Job? = null
    private var activeRecordingMillis = 0L
    private var currentSegmentStartMs = 0L
    private var isPaused = false
    private var activeClipName: String = "d7n_recording.mp4"

    override fun onCreate() {
        super.onCreate()
        recordingsRepository = RecordingsRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (recordingEngine != null) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            RecordingStateHolder.resetIdle(getString(R.string.error_projection_denied))
            stopSelf()
            return
        }

        val settings = RecorderSettings(
            audioSource = AudioSourceMode.fromKey(intent.getStringExtra(EXTRA_AUDIO_SOURCE)),
            resolution = VideoResolution.fromShortSide(intent.getIntExtra(EXTRA_RESOLUTION, VideoResolution.P1080.shortSidePx)),
            fps = VideoFps.fromValue(intent.getIntExtra(EXTRA_FPS, VideoFps.FPS_60.fps)),
            videoBitrate = VideoBitrate.fromBps(intent.getIntExtra(EXTRA_VIDEO_BITRATE, VideoBitrate.MBPS_12.bps)),
            audioBitrate = AudioBitrate.fromBps(intent.getIntExtra(EXTRA_AUDIO_BITRATE, AudioBitrate.KBPS_192.bps)),
            audioSampleRate = AudioSampleRate.fromHz(intent.getIntExtra(EXTRA_AUDIO_SAMPLE_RATE, AudioSampleRate.HZ_48000.hz)),
            micBoost = intent.getBooleanExtra(EXTRA_MIC_BOOST, true)
        )

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(paused = false), fgsType)

        try {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection unavailable")

            val engine = RecordingEngine(
                context = applicationContext,
                recordingsRepository = recordingsRepository,
                onAudioAmplitude = { amp -> RecordingStateHolder.updateAudioAmplitude(amp) },
                onSystemStopped = { handleStop() }
            )
            val target = engine.start(projection, settings)
            recordingEngine = engine
            activeClipName = target.displayName

            isPaused = false
            activeRecordingMillis = 0L
            currentSegmentStartMs = System.currentTimeMillis()
            RecordingStateHolder.updateStatus(RecordingStatus.RECORDING, settings.audioSource)
            startTimerLoop()
        } catch (t: Throwable) {
            recordingEngine = null
            RecordingStateHolder.resetIdle(getString(R.string.error_recording_failed, t.localizedMessage ?: ""))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handlePause() {
        val engine = recordingEngine ?: return
        if (isPaused) return
        isPaused = true
        activeRecordingMillis += (System.currentTimeMillis() - currentSegmentStartMs).coerceAtLeast(0L)
        engine.pause()
        RecordingStateHolder.updateElapsed(activeRecordingMillis)
        RecordingStateHolder.updateStatus(RecordingStatus.PAUSED)
        updateNotification(paused = true)
    }

    private fun handleResume() {
        val engine = recordingEngine ?: return
        if (!isPaused) return
        isPaused = false
        currentSegmentStartMs = System.currentTimeMillis()
        engine.resume()
        RecordingStateHolder.updateStatus(RecordingStatus.RECORDING)
        updateNotification(paused = false)
    }

    private fun handleStop() {
        val engine = recordingEngine ?: run {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        recordingEngine = null
        timerJob?.cancel()
        timerJob = null

        val finalElapsed = if (isPaused) {
            activeRecordingMillis
        } else {
            activeRecordingMillis + (System.currentTimeMillis() - currentSegmentStartMs).coerceAtLeast(0L)
        }

        RecordingStateHolder.updateStatus(RecordingStatus.SAVING)

        serviceScope.launch(Dispatchers.IO) {
            val saved = engine.stop(finalElapsed)
            if (saved) {
                RecordingStateHolder.markSaved(activeClipName)
            } else {
                RecordingStateHolder.resetIdle()
            }
            ServiceCompat.stopForeground(this@ScreenRecordService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val total = if (isPaused) {
                    activeRecordingMillis
                } else {
                    activeRecordingMillis + (System.currentTimeMillis() - currentSegmentStartMs).coerceAtLeast(0L)
                }
                RecordingStateHolder.updateElapsed(total)
                delay(200L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(paused: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(paused))
    }

    private fun buildNotification(paused: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeAction = if (paused) {
            val resumePending = PendingIntent.getService(
                this,
                1,
                Intent(this, ScreenRecordService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                getString(R.string.notif_action_resume),
                resumePending
            ).build()
        } else {
            val pausePending = PendingIntent.getService(
                this,
                2,
                Intent(this, ScreenRecordService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                getString(R.string.notif_action_pause),
                pausePending
            ).build()
        }

        val stopPending = PendingIntent.getService(
            this,
            3,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.notif_action_stop),
            stopPending
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (paused) getString(R.string.notif_title_paused)
                else getString(R.string.notif_title_recording)
            )
            .setContentText(getString(R.string.app_subtitle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(!paused)
            .setContentIntent(openAppIntent)
            .addAction(pauseOrResumeAction)
            .addAction(stopAction)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
