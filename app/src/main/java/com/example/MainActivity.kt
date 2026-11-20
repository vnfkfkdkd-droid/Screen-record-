package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.RecordedVideo
import com.example.model.RecorderSettings
import com.example.model.RecordingStatus
import com.example.service.ScreenRecordService
import com.example.ui.RecorderViewModel
import com.example.ui.components.CountdownOverlay
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VideoPlayerOverlay
import com.example.ui.theme.D7nCrimson
import com.example.ui.theme.D7nCrimsonContainer
import com.example.ui.theme.D7nTheme
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

private enum class D7nTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    STUDIO(
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.FiberManualRecord,
        unselectedIcon = Icons.Outlined.FiberManualRecord,
        testTag = "nav_studio"
    ),
    RECORDINGS(
        labelRes = R.string.nav_recordings,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_recordings"
    ),
    SETTINGS(
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            D7nTheme {
                D7nAppRoot()
            }
        }
    }
}

@Composable
fun D7nAppRoot(viewModel: RecorderViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val isLoadingRecordings by viewModel.isLoadingRecordings.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(D7nTab.STUDIO) }
    var activePlayerVideo by remember { mutableStateOf<RecordedVideo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var startAfterAudioPerm by remember { mutableStateOf(false) }

    val launchForegroundRecorder: (Int, Intent, RecorderSettings) -> Unit = remember(context) {
        { resultCode, resultData, activeSettings ->
            val serviceIntent = ScreenRecordService.buildStartIntent(
                context = context,
                resultCode = resultCode,
                resultData = resultData,
                settings = activeSettings
            )
            ContextCompat.startForegroundService(context, serviceIntent)
            if (activeSettings.autoMinimize) {
                (context as? Activity)?.moveTaskToBack(true)
            }
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.beginProjectionAfterPermission(
                resultCode = result.resultCode,
                resultData = data,
                onLaunchService = launchForegroundRecorder
            )
        } else {
            viewModel.reportError(context.getString(R.string.error_projection_denied))
        }
    }

    val launchProjectionPrompt = remember(context, projectionLauncher) {
        {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Proceed with MediaProjection regardless; notification permission only affects shade controls
        launchProjectionPrompt()
    }

    val proceedWithNotificationAndProjection = remember(context, notificationPermissionLauncher, launchProjectionPrompt) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchProjectionPrompt()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted && startAfterAudioPerm) {
            startAfterAudioPerm = false
            proceedWithNotificationAndProjection()
        } else {
            startAfterAudioPerm = false
        }
    }

    val handleStartRecordingClick = remember(
        settings,
        hasAudioPermission,
        audioPermissionLauncher,
        proceedWithNotificationAndProjection
    ) {
        {
            if (settings.audioSource.requiresRecordAudioPermission && !hasAudioPermission) {
                startAfterAudioPerm = true
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                proceedWithNotificationAndProjection()
            }
        }
    }

    val savedSuccessText = stringResource(R.string.recording_saved_message)
    LaunchedEffect(runtimeState.lastSavedClipName) {
        if (runtimeState.lastSavedClipName != null) {
            snackbarHostState.showSnackbar(savedSuccessText)
            viewModel.clearLastSaved()
        }
    }

    LaunchedEffect(runtimeState.errorMessage) {
        val error = runtimeState.errorMessage
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val shareVideo: (RecordedVideo) -> Unit = remember(context) {
        { video ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, video.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, context.getString(R.string.action_share))
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = StudioObsidian,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = StudioSurface,
                            tonalElevation = 0.dp
                        ) {
                            D7nTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = D7nCrimson,
                                        selectedTextColor = StudioTextPrimary,
                                        indicatorColor = D7nCrimsonContainer,
                                        unselectedIconColor = StudioTextSecondary,
                                        unselectedTextColor = StudioTextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = StudioSurface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            D7nTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = D7nCrimson,
                                        selectedTextColor = StudioTextPrimary,
                                        indicatorColor = D7nCrimsonContainer,
                                        unselectedIconColor = StudioTextSecondary,
                                        unselectedTextColor = StudioTextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentTab) {
                            D7nTab.STUDIO -> StudioScreen(
                                settings = settings,
                                runtimeState = runtimeState,
                                hasAudioPermission = hasAudioPermission,
                                onRequestAudioPermission = {
                                    startAfterAudioPerm = false
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStartRecording = handleStartRecordingClick,
                                onPauseRecording = {
                                    ScreenRecordService.sendAction(context, ScreenRecordService.ACTION_PAUSE)
                                },
                                onResumeRecording = {
                                    ScreenRecordService.sendAction(context, ScreenRecordService.ACTION_RESUME)
                                },
                                onStopRecording = {
                                    ScreenRecordService.sendAction(context, ScreenRecordService.ACTION_STOP)
                                },
                                onSelectAudioSource = viewModel::selectAudioSource,
                                onSelectResolution = viewModel::selectResolution,
                                onSelectFps = viewModel::selectFps,
                                onSelectCountdown = viewModel::setCountdownSeconds
                            )

                            D7nTab.RECORDINGS -> RecordingsScreen(
                                recordings = recordings,
                                thumbnails = thumbnails,
                                isLoading = isLoadingRecordings,
                                onRefresh = viewModel::refreshRecordings,
                                onPlayVideo = { activePlayerVideo = it },
                                onShareVideo = shareVideo,
                                onRenameVideo = viewModel::renameRecording,
                                onDeleteVideo = viewModel::deleteRecording,
                                onGoToStudio = { currentTab = D7nTab.STUDIO }
                            )

                            D7nTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                isRecordingActive = runtimeState.status == RecordingStatus.RECORDING ||
                                    runtimeState.status == RecordingStatus.PAUSED,
                                onSelectResolution = viewModel::selectResolution,
                                onSelectFps = viewModel::selectFps,
                                onSelectVideoBitrate = viewModel::selectVideoBitrate,
                                onSelectAudioBitrate = viewModel::selectAudioBitrate,
                                onSelectAudioSampleRate = viewModel::selectAudioSampleRate,
                                onSetMicBoost = viewModel::setMicBoost,
                                onSetCountdownSeconds = viewModel::setCountdownSeconds,
                                onSetAutoMinimize = viewModel::setAutoMinimize
                            )
                        }
                    }
                }
            }
        }

        if (runtimeState.status == RecordingStatus.COUNTDOWN) {
            CountdownOverlay(
                remainingSeconds = runtimeState.countdownRemaining,
                onCancel = viewModel::cancelCountdown
            )
        }

        activePlayerVideo?.let { video ->
            VideoPlayerOverlay(
                video = video,
                onClose = { activePlayerVideo = null },
                onShare = shareVideo
            )
        }
    }
}
