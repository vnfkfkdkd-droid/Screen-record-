package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RecordingsRepository
import com.example.data.SettingsRepository
import com.example.model.AudioBitrate
import com.example.model.AudioSampleRate
import com.example.model.AudioSourceMode
import com.example.model.RecordedVideo
import com.example.model.RecorderSettings
import com.example.model.RecordingRuntimeState
import com.example.model.RecordingStatus
import com.example.model.VideoBitrate
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.service.RecordingStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val recordingsRepository = RecordingsRepository(application)

    val settings: StateFlow<RecorderSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecorderSettings()
        )

    val runtimeState: StateFlow<RecordingRuntimeState> = RecordingStateHolder.state

    private val _recordings = MutableStateFlow<List<RecordedVideo>>(emptyList())
    val recordings: StateFlow<List<RecordedVideo>> = _recordings.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

    private val _isLoadingRecordings = MutableStateFlow(false)
    val isLoadingRecordings: StateFlow<Boolean> = _isLoadingRecordings.asStateFlow()

    private var countdownJob: Job? = null

    init {
        refreshRecordings()
        viewModelScope.launch {
            runtimeState.collectLatest { state ->
                if (state.lastSavedClipName != null) {
                    delay(350L)
                    refreshRecordings()
                }
            }
        }
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _isLoadingRecordings.value = true
            val list = recordingsRepository.loadRecordings()
            _recordings.value = list
            _isLoadingRecordings.value = false

            list.forEach { video ->
                if (!_thumbnails.value.containsKey(video.id)) {
                    launch {
                        val bmp = recordingsRepository.loadThumbnail(video)
                        if (bmp != null) {
                            _thumbnails.update { current -> current + (video.id to bmp) }
                        }
                    }
                }
            }
        }
    }

    fun beginProjectionAfterPermission(
        resultCode: Int,
        resultData: Intent,
        onLaunchService: (Int, Intent, RecorderSettings) -> Unit
    ) {
        val currentSettings = settings.value
        val countdown = currentSettings.countdownSeconds
        if (countdown <= 0) {
            onLaunchService(resultCode, resultData, currentSettings)
            return
        }

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in countdown downTo 1) {
                RecordingStateHolder.updateCountdown(sec)
                delay(1000L)
            }
            RecordingStateHolder.updateStatus(RecordingStatus.IDLE)
            onLaunchService(resultCode, resultData, settings.value)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        RecordingStateHolder.resetIdle()
    }

    fun reportError(message: String) {
        RecordingStateHolder.resetIdle(message)
    }

    fun clearError() {
        RecordingStateHolder.clearError()
    }

    fun clearLastSaved() {
        RecordingStateHolder.clearLastSaved()
    }

    fun selectAudioSource(mode: AudioSourceMode) {
        viewModelScope.launch { settingsRepository.updateAudioSource(mode) }
    }

    fun selectResolution(resolution: VideoResolution) {
        viewModelScope.launch { settingsRepository.updateResolution(resolution) }
    }

    fun selectFps(fps: VideoFps) {
        viewModelScope.launch { settingsRepository.updateFps(fps) }
    }

    fun selectVideoBitrate(bitrate: VideoBitrate) {
        viewModelScope.launch { settingsRepository.updateVideoBitrate(bitrate) }
    }

    fun selectAudioBitrate(bitrate: AudioBitrate) {
        viewModelScope.launch { settingsRepository.updateAudioBitrate(bitrate) }
    }

    fun selectAudioSampleRate(sampleRate: AudioSampleRate) {
        viewModelScope.launch { settingsRepository.updateAudioSampleRate(sampleRate) }
    }

    fun setMicBoost(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMicBoost(enabled) }
    }

    fun setCountdownSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.updateCountdownSeconds(seconds) }
    }

    fun setAutoMinimize(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoMinimize(enabled) }
    }

    fun deleteRecording(video: RecordedVideo) {
        viewModelScope.launch {
            if (recordingsRepository.deleteRecording(video)) {
                _thumbnails.update { it - video.id }
                refreshRecordings()
            }
        }
    }

    fun renameRecording(video: RecordedVideo, newName: String) {
        viewModelScope.launch {
            if (recordingsRepository.renameRecording(video, newName)) {
                refreshRecordings()
            }
        }
    }
}
