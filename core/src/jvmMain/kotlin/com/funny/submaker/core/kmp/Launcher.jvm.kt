package com.funny.submaker.core.kmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eygraber.uri.Uri
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

actual class FileLauncher<Input>(
    private val mimeType: String = "*/*",
    private val onResult: (Uri?) -> Unit,
) : Launcher<Input, Uri?>() {
    actual override fun launch(input: Input) {
        when (input) {
            is String -> {
                val (suggestedName, extension) = splitFileName(input, mimeType)
                val file: PlatformFile? = runBlocking {
                    FileKit.openFileSaver(
                        suggestedName = suggestedName,
                        extension = extension,
                    )
                }
                onResult(file?.let { Uri.parse(Paths.get(it.path).toUri().toString()) })
            }

            is Array<*> -> {
                val mimes = input.filterIsInstance<String>()
                val file: PlatformFile? = runBlocking {
                    FileKit.openFilePicker(
                        mode = FileKitMode.Single,
                        type = resolveFileKitType(mimes),
                    )
                }
                onResult(file?.let { Uri.parse(Paths.get(it.path).toUri().toString()) })
            }

            else -> onResult(null)
        }
    }
}

actual class MultiFileLauncher<Input>(
    private val onResult: (List<Uri>) -> Unit,
) : Launcher<Input, List<Uri>>() {
    actual override fun launch(input: Input) {
        if (input is Array<*> && input.isArrayOf<String>()) {
            val mimes = input.filterIsInstance<String>()
            val files: List<PlatformFile>? = runBlocking {
                FileKit.openFilePicker(
                    mode = FileKitMode.Multiple(),
                    type = resolveFileKitType(mimes),
                )
            }
            val uris = files.orEmpty().map { Uri.parse(Paths.get(it.path).toUri().toString()) }
            onResult(uris)
        } else {
            onResult(emptyList())
        }
    }
}

@Composable
actual fun rememberCreateFileLauncher(
    mimeType: String,
    onResult: (Uri?) -> Unit,
): FileLauncher<String> = remember(mimeType) { FileLauncher(mimeType, onResult) }

@Composable
actual fun rememberOpenFileLauncher(
    onResult: (Uri?) -> Unit,
): FileLauncher<Array<String>> = remember { FileLauncher("", onResult) }

@Composable
actual fun rememberOpenMultiFileLauncher(
    onResult: (List<Uri>) -> Unit,
): MultiFileLauncher<Array<String>> = remember { MultiFileLauncher(onResult) }

@Composable
actual fun rememberTakePhotoLauncher(
    onResult: (Boolean) -> Unit,
): Launcher<String, Boolean> {
    return remember(onResult) {
        object : Launcher<String, Boolean>() {
            override fun launch(input: String) {
                onResult(false)
            }
        }
    }
}

@Composable
actual fun rememberRequestPermissionLauncher(
    permission: String,
    onResult: (Boolean) -> Unit,
): Launcher<Unit, Boolean> {
    return remember(permission) {
        object : Launcher<Unit, Boolean>() {
            override fun launch(input: Unit) {
                onResult(true)
            }
        }
    }
}

actual val recordPermissionName: String = ""

private fun mimeToSuffixList(mimeType: String) = when (mimeType) {
    "image/*" -> listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif")
    "audio/*" -> listOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    "video/*" -> listOf("mp4", "avi", "mkv", "mov", "flv", "f4v", "m4v", "rmvb", "rm", "3gp", "dat", "ts", "mts", "vob")
    "text/*" -> listOf("txt", "log", "xml", "html", "htm", "css", "js", "json", "java", "kt", "c", "cpp", "h", "hpp", "py", "sh", "bat", "md")
    "application/*" -> listOf("apk", "exe", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "zip", "rar", "7z")
    else -> listOf(mimeType.split("/").last())
}

private fun resolveFileKitType(mimes: List<String>): FileKitType {
    val normalized = mimes.map { it.lowercase() }
    val hasImage = normalized.any { it == "image/*" }
    val hasVideo = normalized.any { it == "video/*" }
    if (hasImage && hasVideo) return FileKitType.ImageAndVideo
    if (hasImage) return FileKitType.Image
    if (hasVideo) return FileKitType.Video

    val extensions = normalized
        .filter { it != "*/*" }
        .flatMap { mimeToSuffixList(it) }
        .map { it.lowercase().removePrefix(".") }
        .distinct()

    return if (extensions.isEmpty()) {
        FileKitType.File()
    } else {
        FileKitType.File(extensions)
    }
}

private fun splitFileName(fileName: String, mimeType: String): Pair<String, String?> {
    val normalized = fileName.replace("\\", "/")
    val baseName = normalized.substringAfterLast("/")
    val dotIndex = baseName.lastIndexOf('.')
    if (dotIndex > 0 && dotIndex < baseName.length - 1) {
        return baseName.take(dotIndex) to baseName.substring(dotIndex + 1)
    }
    val fallbackExt = mimeToSuffixList(mimeType).firstOrNull()?.lowercase()?.removePrefix(".")
    return baseName to fallbackExt
}

