package com.funny.submaker.feature.asr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AsrScreen(
    vm: AsrViewModel,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "字幕生成（ASR）",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "MVP：直连云端 ASR（BYO KEY），优先稳定与质量。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = vm.apiBaseUrl,
                    onValueChange = {
                        vm.apiBaseUrl = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Base URL（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vm.apiKey,
                    onValueChange = {
                        vm.apiKey = it
                        vm.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                )
                Button(
                    onClick = onStart,
                    enabled = !vm.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.running) "处理中…" else "开始识别（占位）")
                }

                val result = vm.lastResult
                if (result != null) {
                    Text(result, style = MaterialTheme.typography.bodyMedium)
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

