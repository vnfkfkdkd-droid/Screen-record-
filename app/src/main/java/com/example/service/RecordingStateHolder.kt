package com.example.service

import com.example.model.AudioSourceMode
import com.example.model.RecordingRuntimeState
import com.example.model.RecordingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object RecordingStateHolder {
    private val _state = MutableStateFlow(RecordingRuntimeState())
    val state: StateFlow<RecordingRuntimeState> = _state.asStateFlow()

    fun updateStatus(
        status: RecordingStatus,
        activeAudioSource: AudioSourceMode? = null,
        errorMessage: String? = null
    ) {
        _state.update { current ->
            current.copy(
                status = status,
                activeAudioSource = activeAudioSource ?: current.activeAudioSource,
                errorMessage = errorMessage
            )
        }
    }

    fun updateCountdown(remainingSeconds: Int) {
        _state.update {
            it.copy(
                status = RecordingStatus.COUNTDOWN,
                countdownRemaining = remainingSeconds,
                errorMessage = null
            )
        }
    }

    fun updateElapsed(elapsedMillis: Long) {
        _state.update { it.copy(elapsedMillis = elapsedMillis) }
    }

    fun updateAudioAmplitude(amplitude: Float) {
        _state.update { it.copy(audioAmplitude = amplitude.coerceIn(0f, 1f)) }
    }

    fun markSaved(clipName: String) {
        _state.update {
            it.copy(
                status = RecordingStatus.IDLE,
                elapsedMillis = 0L,
                audioAmplitude = 0f,
                countdownRemaining = 0,
                lastSavedClipName = clipName
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun clearLastSaved() {
        _state.update { it.copy(lastSavedClipName = null) }
    }

    fun resetIdle(errorMessage: String? = null) {
        _state.update {
            it.copy(
                status = RecordingStatus.IDLE,
                elapsedMillis = 0L,
                audioAmplitude = 0f,
                countdownRemaining = 0,
                errorMessage = errorMessage
            )
        }
    }
}
