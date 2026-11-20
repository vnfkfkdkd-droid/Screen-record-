package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.D7nCrimson
import com.example.ui.theme.D7nCrimsonGlow
import com.example.ui.theme.InternalAudioCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.sin

@Composable
fun D7nLogoBadge(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "d7n_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.5.dp,
                    color = if (isRecording) D7nCrimson.copy(alpha = pulseAlpha) else StudioBorder,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.d7n_logo_icon_1795134507706),
                contentDescription = stringResource(R.string.cd_d7n_logo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = StudioTextPrimary, fontWeight = FontWeight.Bold)) {
                                append("d")
                            }
                            withStyle(SpanStyle(color = D7nCrimson, fontWeight = FontWeight.Bold)) {
                                append("7")
                            }
                            withStyle(SpanStyle(color = StudioTextPrimary, fontWeight = FontWeight.Bold)) {
                                append("n")
                            }
                        },
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 22.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) D7nCrimson.copy(alpha = pulseAlpha)
                                else InternalAudioCyan
                            )
                    )
                }
            }
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
        }
    }
}

@Composable
fun LiveAudioSpectrum(
    amplitude: Float,
    isRecording: Boolean,
    isPaused: Boolean,
    hasAudioEnabled: Boolean,
    usesInternalAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 22
    val smoothAmp by animateFloatAsState(
        targetValue = if (isRecording && !isPaused) amplitude else 0f,
        animationSpec = spring(stiffness = 500f),
        label = "amp_smooth"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val primaryBarColor = if (usesInternalAudio) InternalAudioCyan else D7nCrimsonGlow

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val totalWidth = size.width
        val maxHeight = size.height
        val slotWidth = totalWidth / barCount
        val barWidth = (slotWidth * 0.52f).coerceAtLeast(4f)

        for (i in 0 until barCount) {
            val centerFactor = 1f - kotlin.math.abs((i - barCount / 2f) / (barCount / 2f)) * 0.45f
            val wave = ((sin(phase + i * 0.55f) + 1f) * 0.5f)
            val level = when {
                !hasAudioEnabled -> 0.12f
                isPaused -> 0.18f
                isRecording -> (0.18f + (smoothAmp * 0.65f + wave * (0.12f + smoothAmp * 0.35f)) * centerFactor)
                    .coerceIn(0.14f, 1f)
                else -> (0.16f + wave * 0.18f * centerFactor)
            }
            val barHeight = maxHeight * level
            val left = i * slotWidth + (slotWidth - barWidth) / 2f
            val top = (maxHeight - barHeight) / 2f

            val barColor = when {
                !hasAudioEnabled -> StudioBorder
                isRecording -> primaryBarColor.copy(alpha = 0.55f + 0.45f * level)
                else -> primaryBarColor.copy(alpha = 0.35f)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun CountdownOverlay(
    remainingSeconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = StudioObsidian.copy(alpha = 0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            D7nCrimson.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .clip(CircleShape)
                        .background(StudioSurfaceElevated)
                        .border(3.dp, D7nCrimson, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = remainingSeconds.toString(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 68.sp,
                        color = StudioTextPrimary
                    )
                }

                Text(
                    text = stringResource(R.string.status_countdown, remainingSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioTextPrimary
                )

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("cancel_countdown_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioTextPrimary)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel_countdown),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

fun formatDurationTimer(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000.0) {
        String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
