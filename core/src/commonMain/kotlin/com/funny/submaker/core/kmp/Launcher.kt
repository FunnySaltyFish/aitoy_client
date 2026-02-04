package com.funny.submaker.core.kmp

import androidx.compose.runtime.Composable
import com.eygraber.uri.Uri

/**
 * KMP Launcher（文件选择/权限/拍照等）
 */
abstract class Launcher<Input, Output> {
    abstract fun launch(input: Input)
}

expect class FileLauncher<Input> : Launcher<Input, Uri?> {
    override fun launch(input: Input)
}

expect class MultiFileLauncher<Input> : Launcher<Input, List<Uri>> {
    override fun launch(input: Input)
}

@Composable
expect fun rememberCreateFileLauncher(
    mimeType: String = "*/*",
    onResult: (Uri?) -> Unit = {},
): FileLauncher<String>

@Composable
expect fun rememberOpenFileLauncher(
    onResult: (Uri?) -> Unit = {},
): FileLauncher<Array<String>>

@Composable
expect fun rememberOpenMultiFileLauncher(
    onResult: (List<Uri>) -> Unit = {},
): MultiFileLauncher<Array<String>>

@Composable
expect fun rememberTakePhotoLauncher(
    onResult: (Boolean) -> Unit = {},
): Launcher<String, Boolean>

@Composable
expect fun rememberRequestPermissionLauncher(
    permission: String,
    onResult: (Boolean) -> Unit = {},
): Launcher<Unit, Boolean>

expect val recordPermissionName: String

