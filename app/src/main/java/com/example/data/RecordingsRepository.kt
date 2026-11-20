package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.FileProvider
import com.example.model.RecordedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface RecordingOutputTarget {
    val displayName: String

    data class MediaStoreTarget(
        val uri: Uri,
        override val displayName: String,
        val pfd: ParcelFileDescriptor
    ) : RecordingOutputTarget

    data class FileTarget(
        val file: File,
        val uri: Uri,
        override val displayName: String
    ) : RecordingOutputTarget
}

class RecordingsRepository(private val context: Context) {

    fun createOutputTarget(): RecordingOutputTarget? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "d7n_$timestamp.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/d7n")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val pfd = resolver.openFileDescriptor(uri, "rw")
            if (pfd == null) {
                resolver.delete(uri, null, null)
                return null
            }
            RecordingOutputTarget.MediaStoreTarget(uri = uri, displayName = fileName, pfd = pfd)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "d7n")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            RecordingOutputTarget.FileTarget(file = file, uri = uri, displayName = fileName)
        }
    }

    fun finalizeOutputTarget(target: RecordingOutputTarget, durationMillis: Long, success: Boolean) {
        when (target) {
            is RecordingOutputTarget.MediaStoreTarget -> {
                runCatching { target.pfd.close() }
                val resolver = context.contentResolver
                if (success) {
                    val values = ContentValues().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.IS_PENDING, 0)
                            if (durationMillis > 0L) {
                                put(MediaStore.Video.Media.DURATION, durationMillis)
                            }
                        }
                    }
                    runCatching { resolver.update(target.uri, values, null, null) }
                } else {
                    runCatching { resolver.delete(target.uri, null, null) }
                }
            }
            is RecordingOutputTarget.FileTarget -> {
                if (!success && target.file.exists()) {
                    target.file.delete()
                }
            }
        }
    }

    suspend fun loadRecordings(): List<RecordedVideo> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStoreRecordings()
        } else {
            queryLegacyFileRecordings()
        }
    }

    private fun queryMediaStoreRecordings(): List<RecordedVideo> {
        val results = mutableListOf<RecordedVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${Environment.DIRECTORY_MOVIES}/d7n%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    results.add(
                        RecordedVideo(
                            id = id,
                            uri = uri,
                            displayName = cursor.getString(nameCol) ?: "d7n_recording.mp4",
                            durationMillis = cursor.getLong(durCol),
                            sizeBytes = size,
                            dateAddedSeconds = cursor.getLong(dateCol),
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol)
                        )
                    )
                }
            }
        }
        return results
    }

    private fun queryLegacyFileRecordings(): List<RecordedVideo> {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "d7n")
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()

        return files.mapIndexed { index, file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            var duration = 0L
            var width = 0
            var height = 0
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }
            RecordedVideo(
                id = file.hashCode().toLong() + index,
                uri = uri,
                displayName = file.name,
                durationMillis = duration,
                sizeBytes = file.length(),
                dateAddedSeconds = file.lastModified() / 1000L,
                width = width,
                height = height
            )
        }
    }

    suspend fun loadThumbnail(video: RecordedVideo): Bitmap? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = runCatching {
                context.contentResolver.loadThumbnail(video.uri, Size(480, 270), null)
            }.getOrNull()
            if (loaded != null) return@withContext loaded
        }

        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(context, video.uri)
            retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun deleteRecording(video: RecordedVideo): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(video.uri, null, null) > 0
            } else {
                val file = File(
                    File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "d7n"),
                    video.displayName
                )
                file.exists() && file.delete()
            }
        }.getOrDefault(false)
    }

    suspend fun renameRecording(video: RecordedVideo, newNameInput: String): Boolean = withContext(Dispatchers.IO) {
        val sanitized = newNameInput.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
        if (sanitized.isBlank()) return@withContext false
        val finalName = if (sanitized.endsWith(".mp4", ignoreCase = true)) sanitized else "$sanitized.mp4"

        return@withContext runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
                }
                context.contentResolver.update(video.uri, values, null, null) > 0
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "d7n")
                val oldFile = File(dir, video.displayName)
                val newFile = File(dir, finalName)
                oldFile.exists() && !newFile.exists() && oldFile.renameTo(newFile)
            }
        }.getOrDefault(false)
    }
}
