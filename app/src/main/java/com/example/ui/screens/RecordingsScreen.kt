package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.RecordedVideo
import com.example.ui.components.formatDurationTimer
import com.example.ui.components.formatFileSize
import com.example.ui.theme.D7nCrimson
import com.example.ui.theme.InternalAudioCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingsScreen(
    recordings: List<RecordedVideo>,
    thumbnails: Map<Long, Bitmap>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onPlayVideo: (RecordedVideo) -> Unit,
    onShareVideo: (RecordedVideo) -> Unit,
    onRenameVideo: (RecordedVideo, String) -> Unit,
    onDeleteVideo: (RecordedVideo) -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<RecordedVideo?>(null) }
    var pendingRename by remember { mutableStateOf<RecordedVideo?>(null) }
    var renameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.recordings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = StudioTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.recordings_count_format, recordings.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextSecondary
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(StudioSurfaceElevated)
                        .testTag("refresh_recordings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.recordings_title),
                        tint = StudioTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading && recordings.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = D7nCrimson)
                    }
                }
                recordings.isEmpty() -> {
                    EmptyRecordingsState(
                        onGoToStudio = onGoToStudio,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(recordings, key = { it.id }) { video ->
                            RecordingItemCard(
                                video = video,
                                thumbnail = thumbnails[video.id],
                                onPlay = { onPlayVideo(video) },
                                onShare = { onShareVideo(video) },
                                onRename = {
                                    renameInput = video.displayName.removeSuffix(".mp4")
                                    pendingRename = video
                                },
                                onDelete = { pendingDelete = video }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = StudioSurfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_title),
                    color = StudioTextPrimary
                )
            },
            text = {
                Text(
                    text = "${video.displayName}\n${stringResource(R.string.dialog_delete_message)}",
                    color = StudioTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVideo(video)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = D7nCrimson)
                ) {
                    Text(stringResource(R.string.action_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel_countdown), color = StudioTextSecondary)
                }
            }
        )
    }

    pendingRename?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            containerColor = StudioSurfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.dialog_rename_title),
                    color = StudioTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_rename_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameVideo(video, renameInput)
                        pendingRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = D7nCrimson)
                ) {
                    Text(stringResource(R.string.action_save), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text(stringResource(R.string.action_cancel_countdown), color = StudioTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyRecordingsState(
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioSurfaceElevated)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.d7n_logo_icon_1795134507706),
                    contentDescription = stringResource(R.string.cd_d7n_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Text(
                text = stringResource(R.string.recordings_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = StudioTextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.recordings_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onGoToStudio,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = D7nCrimson,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .height(50.dp)
                    .testTag("empty_start_recording_button")
            ) {
                Icon(imageVector = Icons.Default.MovieCreation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.recordings_start_first),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun RecordingItemCard(
    video: RecordedVideo,
    thumbnail: Bitmap?,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(video.dateAddedSeconds) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(video.dateAddedSeconds * 1000L))
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onPlay)
            .testTag("recording_card_${video.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Box
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioObsidian),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_video_thumbnail),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(D7nCrimson.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (video.durationMillis > 0L) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                        ) {
                            Text(
                                text = formatDurationTimer(video.durationMillis),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Metadata & Actions
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatFileSize(video.sizeBytes),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = InternalAudioCyan
                        )
                        if (video.width > 0 && video.height > 0) {
                            Text(
                                text = "${video.width}×${video.height}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = StudioTextSecondary
                            )
                        }
                    }
                }

                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = StudioTextSecondary,
                    maxLines = 1
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = StudioTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = stringResource(R.string.action_rename),
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = D7nCrimson,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerOverlay(
    video: RecordedVideo,
    onClose: () -> Unit,
    onShare: (RecordedVideo) -> Unit
) {
    BackHandler(onBack = onClose)

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var durationMs by remember { mutableIntStateOf(video.durationMillis.toInt().coerceAtLeast(1)) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, videoViewRef) {
        while (true) {
            val view = videoViewRef
            if (view != null && isPlaying && view.isPlaying) {
                currentPosMs = view.currentPosition
            }
            delay(250L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StudioObsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_back),
                        tint = StudioTextPrimary
                    )
                }

                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = { onShare(video) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = StudioTextPrimary
                    )
                }
            }

            // Video surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (playbackError) {
                    Text(
                        text = stringResource(R.string.player_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = StudioTextSecondary
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(video.uri)
                                setOnPreparedListener { mp ->
                                    durationMs = mp.duration.coerceAtLeast(1)
                                    isPlaying = true
                                    start()
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPosMs = durationMs
                                }
                                setOnErrorListener { _, _, _ ->
                                    playbackError = true
                                    true
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom playback controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurfaceElevated)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { pos ->
                            currentPosMs = pos.toInt()
                            videoViewRef?.seekTo(currentPosMs)
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = D7nCrimson,
                            activeTrackColor = D7nCrimson,
                            inactiveTrackColor = StudioSurfaceHighlight
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDurationTimer(currentPosMs.toLong()),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = StudioTextSecondary
                        )

                        IconButton(
                            onClick = {
                                val view = videoViewRef ?: return@IconButton
                                if (isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    if (currentPosMs >= durationMs - 200) {
                                        view.seekTo(0)
                                    }
                                    view.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(D7nCrimson)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.action_play),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = formatDurationTimer(durationMs.toLong()),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = StudioTextSecondary
                        )
                    }
                }
            }
        }
    }
}
