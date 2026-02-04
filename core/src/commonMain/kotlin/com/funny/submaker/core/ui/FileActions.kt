package com.funny.submaker.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.eygraber.uri.Uri
import com.funny.submaker.core.kmp.rememberCreateFileLauncher
import com.funny.submaker.core.kmp.rememberOpenFileLauncher
import com.funny.submaker.core.kmp.rememberOpenMultiFileLauncher
import com.funny.submaker.core.kmp.writeText

@Composable
fun rememberPickSingleFileAction(
    mimeTypes: Array<String>,
    onPicked: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberOpenFileLauncher { uri ->
        if (uri != null) onPicked(uri)
    }
    return remember(mimeTypes, launcher) {
        { launcher.launch(mimeTypes) }
    }
}

@Composable
fun rememberPickMultiFileAction(
    mimeTypes: Array<String>,
    onPicked: (List<Uri>) -> Unit,
): () -> Unit {
    val launcher = rememberOpenMultiFileLauncher { list ->
        onPicked(list)
    }
    return remember(mimeTypes, launcher) {
        { launcher.launch(mimeTypes) }
    }
}

@Composable
fun rememberExportTextFileAction(
    mimeType: String = "text/plain",
    fileName: () -> String,
    onExported: (Uri) -> Unit = {},
    onFailed: (Throwable) -> Unit = {},
): (String) -> Unit {
    var pendingText by remember { mutableStateOf<String?>(null) }
    val launcher = rememberCreateFileLauncher(mimeType = mimeType) { uri ->
        val text = pendingText
        pendingText = null
        if (uri == null || text == null) return@rememberCreateFileLauncher
        runCatching {
            uri.writeText(text)
            onExported(uri)
        }.onFailure(onFailed)
    }

    return remember(launcher, fileName) {
        { text ->
            pendingText = text
            launcher.launch(fileName())
        }
    }
}

