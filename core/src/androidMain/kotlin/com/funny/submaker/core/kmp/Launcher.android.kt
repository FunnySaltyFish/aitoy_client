package com.funny.submaker.core.kmp

import android.net.Uri as AndroidUri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import com.eygraber.uri.Uri
import com.eygraber.uri.toKmpUri

actual class FileLauncher<Input>(
    private val activityResultLauncher: ManagedActivityResultLauncher<Input, AndroidUri?>,
) : Launcher<Input, Uri?>() {
    actual override fun launch(input: Input) {
        activityResultLauncher.launch(input)
    }
}

actual class MultiFileLauncher<Input>(
    private val activityResultLauncher: ActivityResultLauncher<Input>,
) : Launcher<Input, List<Uri>>() {
    actual override fun launch(input: Input) {
        activityResultLauncher.launch(input)
    }
}

@Composable
actual fun rememberCreateFileLauncher(
    mimeType: String,
    onResult: (Uri?) -> Unit,
): FileLauncher<String> {
    val res: ManagedActivityResultLauncher<String, AndroidUri?> =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) {
            onResult(it?.toKmpUri())
        }
    return remember(mimeType) { FileLauncher(res) }
}

@Composable
actual fun rememberOpenFileLauncher(
    onResult: (Uri?) -> Unit,
): FileLauncher<Array<String>> {
    val res = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        onResult(it?.toKmpUri())
    }
    return remember { FileLauncher(res) }
}

@Composable
actual fun rememberOpenMultiFileLauncher(
    onResult: (List<Uri>) -> Unit,
): MultiFileLauncher<Array<String>> {
    val res = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        onResult(list.map { it.toKmpUri() })
    }
    return remember { MultiFileLauncher(res) }
}

@Composable
actual fun rememberTakePhotoLauncher(
    onResult: (Boolean) -> Unit,
): Launcher<String, Boolean> {
    val res = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        onResult(saved)
    }
    return remember(onResult) {
        object : Launcher<String, Boolean>() {
            override fun launch(input: String) {
                res.launch(input.toUri())
            }
        }
    }
}

@Composable
actual fun rememberRequestPermissionLauncher(
    permission: String,
    onResult: (Boolean) -> Unit,
): Launcher<Unit, Boolean> {
    val res = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(it)
    }
    return remember(permission) {
        object : Launcher<Unit, Boolean>() {
            override fun launch(input: Unit) {
                res.launch(permission)
            }
        }
    }
}

actual val recordPermissionName: String = android.Manifest.permission.RECORD_AUDIO

