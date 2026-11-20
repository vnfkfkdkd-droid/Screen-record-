package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AudioBitrate
import com.example.model.AudioSampleRate
import com.example.model.RecorderSettings
import com.example.model.VideoBitrate
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.ui.theme.D7nCrimson
import com.example.ui.theme.D7nCrimsonContainer
import com.example.ui.theme.InternalAudioCyan
import com.example.ui.theme.InternalAudioCyanContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun SettingsScreen(
    settings: RecorderSettings,
    isRecordingActive: Boolean,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (VideoFps) -> Unit,
    onSelectVideoBitrate: (VideoBitrate) -> Unit,
    onSelectAudioBitrate: (AudioBitrate) -> Unit,
    onSelectAudioSampleRate: (AudioSampleRate) -> Unit,
    onSetMicBoost: (Boolean) -> Unit,
    onSetCountdownSeconds: (Int) -> Unit,
    onSetAutoMinimize: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = StudioTextPrimary
            )

            // Video Quality Section
            SettingsSectionCard(
                title = stringResource(R.string.settings_video_section),
                icon = Icons.Outlined.Videocam,
                accentColor = D7nCrimson
            ) {
                ChipSettingGroup(
                    label = stringResource(R.string.settings_resolution),
                    items = VideoResolution.entries.map { it.label to (settings.resolution == it) },
                    enabled = !isRecordingActive,
                    accentColor = D7nCrimson,
                    onSelect = { onSelectResolution(VideoResolution.entries[it]) }
                )

                ChipSettingGroup(
                    label = stringResource(R.string.settings_fps),
                    items = VideoFps.entries.map { it.label to (settings.fps == it) },
                    enabled = !isRecordingActive,
                    accentColor = D7nCrimson,
                    onSelect = { onSelectFps(VideoFps.entries[it]) }
                )

                ChipSettingGroup(
                    label = stringResource(R.string.settings_bitrate),
                    items = VideoBitrate.entries.map { it.label to (settings.videoBitrate == it) },
                    enabled = !isRecordingActive,
                    accentColor = D7nCrimson,
                    onSelect = { onSelectVideoBitrate(VideoBitrate.entries[it]) }
                )
            }

            // Internal Audio Engine Section
            SettingsSectionCard(
                title = stringResource(R.string.settings_audio_section),
                icon = Icons.Default.GraphicEq,
                accentColor = InternalAudioCyan
            ) {
                ChipSettingGroup(
                    label = stringResource(R.string.settings_audio_bitrate),
                    items = AudioBitrate.entries.map { it.label to (settings.audioBitrate == it) },
                    enabled = !isRecordingActive,
                    accentColor = InternalAudioCyan,
                    onSelect = { onSelectAudioBitrate(AudioBitrate.entries[it]) }
                )

                ChipSettingGroup(
                    label = stringResource(R.string.settings_audio_sample_rate),
                    items = AudioSampleRate.entries.map { it.label to (settings.audioSampleRate == it) },
                    enabled = !isRecordingActive,
                    accentColor = InternalAudioCyan,
                    onSelect = { onSelectAudioSampleRate(AudioSampleRate.entries[it]) }
                )

                ToggleSettingRow(
                    title = stringResource(R.string.settings_mic_boost),
                    subtitle = stringResource(R.string.settings_mic_boost_desc),
                    checked = settings.micBoost,
                    enabled = !isRecordingActive,
                    onCheckedChange = onSetMicBoost
                )

                Surface(
                    color = InternalAudioCyanContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InternalAudioCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = InternalAudioCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.settings_internal_note_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_internal_note_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }
                }
            }

            // Behavior Section
            SettingsSectionCard(
                title = stringResource(R.string.settings_behavior_section),
                icon = Icons.Outlined.Tune,
                accentColor = D7nCrimson
            ) {
                val countdownOptions = listOf(0, 3, 5, 10)
                ChipSettingGroup(
                    label = stringResource(R.string.settings_countdown),
                    items = countdownOptions.map { sec ->
                        val lbl = if (sec == 0) {
                            stringResource(R.string.countdown_off)
                        } else {
                            stringResource(R.string.countdown_seconds, sec)
                        }
                        lbl to (settings.countdownSeconds == sec)
                    },
                    enabled = !isRecordingActive,
                    accentColor = D7nCrimson,
                    onSelect = { onSetCountdownSeconds(countdownOptions[it]) }
                )

                ToggleSettingRow(
                    title = stringResource(R.string.settings_auto_minimize),
                    subtitle = stringResource(R.string.settings_auto_minimize_desc),
                    checked = settings.autoMinimize,
                    enabled = true,
                    onCheckedChange = onSetAutoMinimize
                )
            }

            // About d7n Card
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.d7n_logo_icon_1795134507706),
                        contentDescription = stringResource(R.string.cd_d7n_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.about_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = stringResource(R.string.about_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioTextPrimary
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSettingGroup(
    label: String,
    items: List<Pair<String, Boolean>>,
    enabled: Boolean,
    accentColor: Color,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, (text, selected) ->
                    val activeContainer = if (accentColor == InternalAudioCyan) {
                        InternalAudioCyanContainer
                    } else {
                        D7nCrimsonContainer
                    }
                    Surface(
                        color = if (selected) activeContainer else StudioObsidian,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) accentColor else StudioBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) { onSelect(index) }
                    ) {
                        Text(
                            text = text,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = if (selected) Color.White else StudioTextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = D7nCrimson,
                uncheckedThumbColor = StudioTextSecondary,
                uncheckedTrackColor = StudioSurfaceHighlight
            )
        )
    }
}
