package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AudioSourceMode
import com.example.model.RecorderSettings
import com.example.model.RecordingRuntimeState
import com.example.model.RecordingStatus
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.ui.components.D7nLogoBadge
import com.example.ui.components.LiveAudioSpectrum
import com.example.ui.components.formatDurationTimer
import com.example.ui.theme.D7nCrimson
import com.example.ui.theme.D7nCrimsonContainer
import com.example.ui.theme.D7nCrimsonGlow
import com.example.ui.theme.InternalAudioCyan
import com.example.ui.theme.InternalAudioCyanContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberContainer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun StudioScreen(
    settings: RecorderSettings,
    runtimeState: RecordingRuntimeState,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSelectAudioSource: (AudioSourceMode) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (VideoFps) -> Unit,
    onSelectCountdown: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = runtimeState.status == RecordingStatus.RECORDING
    val isPaused = runtimeState.status == RecordingStatus.PAUSED
    val isSaving = runtimeState.status == RecordingStatus.SAVING
    val isSessionActive = isRecording || isPaused

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Brand Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                D7nLogoBadge(isRecording = isRecording)

                StatusPill(status = runtimeState.status)
            }

            // Hero Studio Console
            HeroRecordingConsole(
                settings = settings,
                runtimeState = runtimeState,
                isRecording = isRecording,
                isPaused = isPaused,
                isSaving = isSaving,
                isSessionActive = isSessionActive,
                onStartRecording = onStartRecording,
                onPauseRecording = onPauseRecording,
                onResumeRecording = onResumeRecording,
                onStopRecording = onStopRecording
            )

            // Audio Permission Prompt (if user picked internal/mic audio and permission not yet granted)
            AnimatedVisibility(visible = !hasAudioPermission && settings.audioSource.requiresRecordAudioPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioAmberContainer),
                    border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = StudioAmber
                        )
                        Text(
                            text = stringResource(R.string.permission_audio_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onRequestAudioPermission,
                            modifier = Modifier.testTag("grant_audio_permission_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = StudioAmber,
                                contentColor = StudioObsidian
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.permission_audio_settings),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Audio Source Selector (Internal Audio Highlighted)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioTextPrimary
                    )
                    Surface(
                        color = InternalAudioCyanContainer,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, InternalAudioCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = InternalAudioCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.badge_internal_ready),
                                style = MaterialTheme.typography.labelSmall,
                                color = InternalAudioCyan
                            )
                        }
                    }
                }

                val modes = AudioSourceMode.entries
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AudioModeCard(
                            mode = modes[0],
                            selected = settings.audioSource == modes[0],
                            enabled = !isSessionActive && modes[0].isSupportedOnDevice,
                            onClick = { onSelectAudioSource(modes[0]) },
                            modifier = Modifier.weight(1f)
                        )
                        AudioModeCard(
                            mode = modes[1],
                            selected = settings.audioSource == modes[1],
                            enabled = !isSessionActive && modes[1].isSupportedOnDevice,
                            onClick = { onSelectAudioSource(modes[1]) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AudioModeCard(
                            mode = modes[2],
                            selected = settings.audioSource == modes[2],
                            enabled = !isSessionActive && modes[2].isSupportedOnDevice,
                            onClick = { onSelectAudioSource(modes[2]) },
                            modifier = Modifier.weight(1f)
                        )
                        AudioModeCard(
                            mode = modes[3],
                            selected = settings.audioSource == modes[3],
                            enabled = !isSessionActive && modes[3].isSupportedOnDevice,
                            onClick = { onSelectAudioSource(modes[3]) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Capture Preset Strip
            QuickSpecsCard(
                settings = settings,
                enabled = !isSessionActive,
                onSelectResolution = onSelectResolution,
                onSelectFps = onSelectFps,
                onSelectCountdown = onSelectCountdown
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusPill(status: RecordingStatus) {
    val (labelRes, dotColor, bgColor) = when (status) {
        RecordingStatus.IDLE -> Triple(R.string.status_ready, StudioEmerald, StudioSurfaceElevated)
        RecordingStatus.COUNTDOWN -> Triple(R.string.status_ready, StudioAmber, StudioAmberContainer)
        RecordingStatus.RECORDING -> Triple(R.string.status_recording, D7nCrimson, D7nCrimsonContainer)
        RecordingStatus.PAUSED -> Triple(R.string.status_paused, StudioAmber, StudioAmberContainer)
        RecordingStatus.SAVING -> Triple(R.string.status_saving, InternalAudioCyan, InternalAudioCyanContainer)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = StudioTextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HeroRecordingConsole(
    settings: RecorderSettings,
    runtimeState: RecordingRuntimeState,
    isRecording: Boolean,
    isPaused: Boolean,
    isSaving: Boolean,
    isSessionActive: Boolean,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "record_ring")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    val borderBrush = if (isRecording) {
        Brush.linearGradient(listOf(D7nCrimson, InternalAudioCyan))
    } else {
        Brush.linearGradient(listOf(StudioBorder, StudioSurfaceHighlight))
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.5.dp, borderBrush),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isRecording) D7nCrimson.copy(alpha = 0.14f)
                            else InternalAudioCyan.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Digital Timer
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = formatDurationTimer(runtimeState.elapsedMillis),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        letterSpacing = 1.sp,
                        color = if (isRecording) D7nCrimsonGlow else StudioTextPrimary
                    )
                }

                // Live Audio Waveform Spectrum
                LiveAudioSpectrum(
                    amplitude = runtimeState.audioAmplitude,
                    isRecording = isRecording,
                    isPaused = isPaused,
                    hasAudioEnabled = settings.audioSource != AudioSourceMode.NONE,
                    usesInternalAudio = settings.audioSource.usesInternalAudio
                )

                // Active Spec Chips
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpecMiniChip(text = settings.resolution.label)
                        SpecMiniChip(text = settings.fps.label)
                        SpecMiniChip(text = settings.videoBitrate.label)
                    }
                }

                // Primary Record / Pause / Stop Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSessionActive) {
                        FilledTonalButton(
                            onClick = if (isPaused) onResumeRecording else onPauseRecording,
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("pause_resume_button"),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = StudioSurfaceHighlight,
                                contentColor = StudioTextPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(R.string.cd_pause_button)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (isPaused) R.string.action_resume_recording
                                    else R.string.action_pause_recording
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))
                    }

                    Box(contentAlignment = Alignment.Center) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(ringScale)
                                    .clip(CircleShape)
                                    .background(D7nCrimson.copy(alpha = 0.22f))
                            )
                        }

                        Button(
                            onClick = {
                                if (isSessionActive) onStopRecording() else onStartRecording()
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = D7nCrimson,
                                contentColor = Color.White,
                                disabledContainerColor = StudioSurfaceHighlight
                            ),
                            modifier = Modifier
                                .height(58.dp)
                                .testTag("record_action_button")
                        ) {
                            Icon(
                                imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = stringResource(R.string.cd_record_button),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(
                                    when {
                                        isSaving -> R.string.status_saving
                                        isSessionActive -> R.string.action_stop_recording
                                        else -> R.string.action_start_recording
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecMiniChip(text: String) {
    Surface(
        color = StudioObsidian.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = StudioTextSecondary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AudioModeCard(
    mode: AudioSourceMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (mode) {
        AudioSourceMode.INTERNAL -> Icons.Default.GraphicEq
        AudioSourceMode.INTERNAL_AND_MIC -> Icons.Default.HeadsetMic
        AudioSourceMode.MIC -> Icons.Default.Mic
        AudioSourceMode.NONE -> Icons.AutoMirrored.Filled.VolumeOff
    }
    val titleRes = when (mode) {
        AudioSourceMode.INTERNAL -> R.string.audio_internal_title
        AudioSourceMode.INTERNAL_AND_MIC -> R.string.audio_internal_mic_title
        AudioSourceMode.MIC -> R.string.audio_mic_title
        AudioSourceMode.NONE -> R.string.audio_none_title
    }
    val descRes = when (mode) {
        AudioSourceMode.INTERNAL -> R.string.audio_internal_desc
        AudioSourceMode.INTERNAL_AND_MIC -> R.string.audio_internal_mic_desc
        AudioSourceMode.MIC -> R.string.audio_mic_desc
        AudioSourceMode.NONE -> R.string.audio_none_desc
    }

    val accentColor = if (mode.usesInternalAudio) InternalAudioCyan else D7nCrimsonGlow
    val borderColor = when {
        selected -> accentColor
        else -> StudioBorder
    }
    val containerColor = when {
        selected && mode.usesInternalAudio -> InternalAudioCyanContainer.copy(alpha = 0.75f)
        selected -> D7nCrimsonContainer.copy(alpha = 0.65f)
        else -> StudioSurfaceElevated
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("audio_mode_${mode.key}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) accentColor.copy(alpha = 0.2f)
                            else StudioSurfaceHighlight
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) accentColor else StudioTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (mode.usesInternalAudio) {
                    Surface(
                        color = StudioObsidian.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.badge_android10_plus),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = if (mode.isSupportedOnDevice) InternalAudioCyan else StudioTextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) StudioTextPrimary else StudioTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) StudioTextSecondary else StudioTextMuted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickSpecsCard(
    settings: RecorderSettings,
    enabled: Boolean,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (VideoFps) -> Unit,
    onSelectCountdown: (Int) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_specs_title),
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary
            )

            QuickOptionRow(
                icon = Icons.Outlined.Videocam,
                label = stringResource(R.string.spec_resolution),
                options = VideoResolution.entries.map { it.label to (settings.resolution == it) },
                enabled = enabled,
                onSelect = { idx -> onSelectResolution(VideoResolution.entries[idx]) }
            )

            QuickOptionRow(
                icon = Icons.Outlined.Speed,
                label = stringResource(R.string.spec_fps),
                options = listOf(VideoFps.FPS_60, VideoFps.FPS_30).map { it.label to (settings.fps == it) },
                enabled = enabled,
                onSelect = { idx ->
                    val target = if (idx == 0) VideoFps.FPS_60 else VideoFps.FPS_30
                    onSelectFps(target)
                }
            )

            val countdownChoices = listOf(0, 3, 5)
            QuickOptionRow(
                icon = Icons.Outlined.Timer,
                label = stringResource(R.string.spec_countdown),
                options = countdownChoices.map { sec ->
                    val lbl = if (sec == 0) "0s" else "${sec}s"
                    lbl to (settings.countdownSeconds == sec)
                },
                enabled = enabled,
                onSelect = { idx -> onSelectCountdown(countdownChoices[idx]) }
            )
        }
    }
}

@Composable
private fun QuickOptionRow(
    icon: ImageVector,
    label: String,
    options: List<Pair<String, Boolean>>,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StudioTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { index, (text, selected) ->
                    Surface(
                        color = if (selected) D7nCrimsonContainer else StudioObsidian,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) D7nCrimson else StudioBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onSelect(index) }
                    ) {
                        Text(
                            text = text,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.5.sp,
                            color = if (selected) Color.White else StudioTextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
