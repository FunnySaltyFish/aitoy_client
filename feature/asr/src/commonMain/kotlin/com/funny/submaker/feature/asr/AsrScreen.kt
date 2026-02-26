package com.funny.submaker.feature.asr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.submaker.core.subtitle.SrtWriter
import com.funny.submaker.core.subtitle.VttWriter
import com.funny.submaker.core.ui.rememberExportTextFileAction
import com.funny.submaker.core.ui.rememberPickSingleFileAction
import com.funny.submaker.core.utils.FileSize

@Composable
fun AsrScreen(
    projectId: String? = null,
    onOpenAuth: (() -> Unit)? = null,
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel { AsrViewModel() }
    LaunchedEffect(projectId) {
        vm.bindProject(projectId)
    }
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "字幕生成（ASR）",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Qwen3-ASR FileTrans：本地文件直传 OSS，后端负责任务提交与轮询。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val pickMedia = rememberPickSingleFileAction(
                    mimeTypes = arrayOf("video/*", "audio/*"),
                    onPicked = vm::onMediaPicked,
                )
                val exportSrt = rememberExportTextFileAction(
                    mimeType = "text/plain",
                    fileName = { vm.suggestedExportFileName("srt") },
                    onExported = {
                        vm.lastResult = "SRT 导出成功：${vm.suggestedExportFileName("srt")}"
                        vm.onExportSuccess("SRT")
                    },
                    onFailed = { vm.errorMessage = it.message ?: "SRT 导出失败" },
                )
                val exportVtt = rememberExportTextFileAction(
                    mimeType = "text/vtt",
                    fileName = { vm.suggestedExportFileName("vtt") },
                    onExported = {
                        vm.lastResult = "VTT 导出成功：${vm.suggestedExportFileName("vtt")}"
                        vm.onExportSuccess("VTT")
                    },
                    onFailed = { vm.errorMessage = it.message ?: "VTT 导出失败" },
                )

                val linkedProjectName = vm.linkedProjectName
                if (linkedProjectName != null) {
                    Text(
                        text = "当前项目：$linkedProjectName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = pickMedia,
                    enabled = !vm.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.mediaUri == null) "导入媒体文件" else "重新选择媒体")
                }

                val fileName = vm.mediaName
                val fileSize = vm.mediaSizeBytes
                val fileMime = vm.mediaMimeType
                if (fileName != null || fileSize != null || fileMime != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow(label = "文件", value = fileName ?: "（未知）")
                        if (fileSize != null) {
                            InfoRow(label = "大小", value = FileSize.fromBytes(fileSize).toString())
                        }
                        if (fileMime != null) {
                            InfoRow(label = "MIME", value = fileMime)
                        }
                    }
                }

                OutlinedTextField(
                    value = vm.apiBaseUrl,
                    onValueChange = {
                        vm.apiBaseUrl = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Base URL（可选）") },
                    placeholder = { Text("默认使用 SubMakerPrefs 的服务地址") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vm.apiKey,
                    onValueChange = {
                        vm.apiKey = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("DashScope API Key（可选）") },
                    placeholder = { Text("为空时使用后端已配置 Key") },
                    singleLine = true,
                )
                Text(
                    text = "当前阶段：${vm.stageText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = vm::startAsr,
                    enabled = !vm.running && vm.mediaUri != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.running) "处理中…" else "开始识别")
                }
                if (onOpenAuth != null) {
                    Button(
                        onClick = onOpenAuth,
                        enabled = !vm.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开账号与权益")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { exportSrt(SrtWriter.write(vm.segments)) },
                        enabled = vm.segments.isNotEmpty() && !vm.running,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("导出 SRT")
                    }
                    Button(
                        onClick = { exportVtt(VttWriter.write(vm.segments)) },
                        enabled = vm.segments.isNotEmpty() && !vm.running,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("导出 VTT")
                    }
                }

                val result = vm.lastResult
                if (result != null) {
                    Text(result, style = MaterialTheme.typography.bodyMedium)
                }
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }

                val errorMessage = vm.errorMessage
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
