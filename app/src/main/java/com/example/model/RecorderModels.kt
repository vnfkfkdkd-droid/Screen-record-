package com.example.model

import android.net.Uri
import android.os.Build

enum class AudioSourceMode(val key: String) {
    INTERNAL("internal"),
    INTERNAL_AND_MIC("internal_and_mic"),
    MIC("mic"),
    NONE("none");

    val usesInternalAudio: Boolean
        get() = this == INTERNAL || this == INTERNAL_AND_MIC

    val usesMic: Boolean
        get() = this == MIC || this == INTERNAL_AND_MIC

    val requiresRecordAudioPermission: Boolean
        get() = this != NONE

    val isSupportedOnDevice: Boolean
        get() = if (usesInternalAudio) Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q else true

    companion object {
        fun fromKey(key: String?): AudioSourceMode =
            entries.firstOrNull { it.key == key } ?: defaultForDevice()

        fun defaultForDevice(): AudioSourceMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) INTERNAL else MIC
    }
}

enum class VideoResolution(val shortSidePx: Int, val label: String) {
    P1080(1080, "1080p"),
    P720(720, "720p"),
    P480(480, "480p");

    companion object {
        fun fromShortSide(value: Int): VideoResolution =
            entries.firstOrNull { it.shortSidePx == value } ?: P1080
    }
}

enum class VideoFps(val fps: Int, val label: String) {
    FPS_60(60, "60 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_24(24, "24 FPS");

    companion object {
        fun fromValue(value: Int): VideoFps =
            entries.firstOrNull { it.fps == value } ?: FPS_60
    }
}

enum class VideoBitrate(val bps: Int, val label: String) {
    MBPS_16(16_000_000, "16 Mbps"),
    MBPS_12(12_000_000, "12 Mbps"),
    MBPS_8(8_000_000, "8 Mbps"),
    MBPS_4(4_000_000, "4 Mbps");

    companion object {
        fun fromBps(bps: Int): VideoBitrate =
            entries.firstOrNull { it.bps == bps } ?: MBPS_12
    }
}

enum class AudioBitrate(val bps: Int, val label: String) {
    KBPS_192(192_000, "192 kbps"),
    KBPS_128(128_000, "128 kbps");

    companion object {
        fun fromBps(bps: Int): AudioBitrate =
            entries.firstOrNull { it.bps == bps } ?: KBPS_192
    }
}

enum class AudioSampleRate(val hz: Int, val label: String) {
    HZ_48000(48_000, "48 kHz"),
    HZ_44100(44_100, "44.1 kHz");

    companion object {
        fun fromHz(hz: Int): AudioSampleRate =
            entries.firstOrNull { it.hz == hz } ?: HZ_48000
    }
}

data class RecorderSettings(
    val audioSource: AudioSourceMode = AudioSourceMode.defaultForDevice(),
    val resolution: VideoResolution = VideoResolution.P1080,
    val fps: VideoFps = VideoFps.FPS_60,
    val videoBitrate: VideoBitrate = VideoBitrate.MBPS_12,
    val audioBitrate: AudioBitrate = AudioBitrate.KBPS_192,
    val audioSampleRate: AudioSampleRate = AudioSampleRate.HZ_48000,
    val micBoost: Boolean = true,
    val countdownSeconds: Int = 3,
    val autoMinimize: Boolean = false
)

enum class RecordingStatus {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    SAVING
}

data class RecordingRuntimeState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val elapsedMillis: Long = 0L,
    val countdownRemaining: Int = 0,
    val audioAmplitude: Float = 0f,
    val activeAudioSource: AudioSourceMode = AudioSourceMode.defaultForDevice(),
    val errorMessage: String? = null,
    val lastSavedClipName: String? = null
)

data class RecordedVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val width: Int,
    val height: Int
)
