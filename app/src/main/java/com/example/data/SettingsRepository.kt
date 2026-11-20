package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioBitrate
import com.example.model.AudioSampleRate
import com.example.model.AudioSourceMode
import com.example.model.RecorderSettings
import com.example.model.VideoBitrate
import com.example.model.VideoFps
import com.example.model.VideoResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recorderDataStore: DataStore<Preferences> by preferencesDataStore(name = "d7n_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val VIDEO_RESOLUTION = intPreferencesKey("video_resolution")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val AUDIO_SAMPLE_RATE = intPreferencesKey("audio_sample_rate")
        val MIC_BOOST = booleanPreferencesKey("mic_boost")
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val AUTO_MINIMIZE = booleanPreferencesKey("auto_minimize")
    }

    val settingsFlow: Flow<RecorderSettings> = context.recorderDataStore.data.map { prefs ->
        val rawSource = AudioSourceMode.fromKey(prefs[Keys.AUDIO_SOURCE])
        val safeSource = if (rawSource.isSupportedOnDevice) rawSource else AudioSourceMode.MIC

        RecorderSettings(
            audioSource = safeSource,
            resolution = VideoResolution.fromShortSide(prefs[Keys.VIDEO_RESOLUTION] ?: VideoResolution.P1080.shortSidePx),
            fps = VideoFps.fromValue(prefs[Keys.VIDEO_FPS] ?: VideoFps.FPS_60.fps),
            videoBitrate = VideoBitrate.fromBps(prefs[Keys.VIDEO_BITRATE] ?: VideoBitrate.MBPS_12.bps),
            audioBitrate = AudioBitrate.fromBps(prefs[Keys.AUDIO_BITRATE] ?: AudioBitrate.KBPS_192.bps),
            audioSampleRate = AudioSampleRate.fromHz(prefs[Keys.AUDIO_SAMPLE_RATE] ?: AudioSampleRate.HZ_48000.hz),
            micBoost = prefs[Keys.MIC_BOOST] ?: true,
            countdownSeconds = prefs[Keys.COUNTDOWN_SECONDS] ?: 3,
            autoMinimize = prefs[Keys.AUTO_MINIMIZE] ?: false
        )
    }

    suspend fun updateAudioSource(mode: AudioSourceMode) {
        if (!mode.isSupportedOnDevice) return
        context.recorderDataStore.edit { it[Keys.AUDIO_SOURCE] = mode.key }
    }

    suspend fun updateResolution(resolution: VideoResolution) {
        context.recorderDataStore.edit { it[Keys.VIDEO_RESOLUTION] = resolution.shortSidePx }
    }

    suspend fun updateFps(fps: VideoFps) {
        context.recorderDataStore.edit { it[Keys.VIDEO_FPS] = fps.fps }
    }

    suspend fun updateVideoBitrate(bitrate: VideoBitrate) {
        context.recorderDataStore.edit { it[Keys.VIDEO_BITRATE] = bitrate.bps }
    }

    suspend fun updateAudioBitrate(bitrate: AudioBitrate) {
        context.recorderDataStore.edit { it[Keys.AUDIO_BITRATE] = bitrate.bps }
    }

    suspend fun updateAudioSampleRate(sampleRate: AudioSampleRate) {
        context.recorderDataStore.edit { it[Keys.AUDIO_SAMPLE_RATE] = sampleRate.hz }
    }

    suspend fun updateMicBoost(enabled: Boolean) {
        context.recorderDataStore.edit { it[Keys.MIC_BOOST] = enabled }
    }

    suspend fun updateCountdownSeconds(seconds: Int) {
        context.recorderDataStore.edit { it[Keys.COUNTDOWN_SECONDS] = seconds }
    }

    suspend fun updateAutoMinimize(enabled: Boolean) {
        context.recorderDataStore.edit { it[Keys.AUTO_MINIMIZE] = enabled }
    }
}
