package com.funny.submaker.feature.asr.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funny.submaker.core.subtitle.SubtitleSegment
import com.funny.submaker.feature.asr.AsrCardShape
import com.funny.submaker.feature.asr.AsrLanguage
import com.funny.submaker.feature.asr.AsrRoundButtonShape
import com.funny.submaker.feature.asr.AsrViewModel
import com.funny.submaker.feature.asr.TranslationWorkflowStage
import com.funny.submaker.feature.asr.rememberAsrUiTokens

@Composable
fun AsrTranscriptionWorkspace(
    vm: AsrViewModel,
    onBackToUpload: () -> Unit,
    onReplaceMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    val selectedSegment = vm.selectedSegmentIndex?.let { index -> vm.segments.getOrNull(index) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkspaceHeader(
            stageText = vm.stageText,
            resultText = vm.lastResult,
            onBackToUpload = onBackToUpload,
            onReplaceMedia = onReplaceMedia,
        )

        if (vm.running || vm.segments.isEmpty()) {
            ProcessingPanel(
                progress = vm.transcriptionProgress,
                estimate = vm.estimatedRemainingText,
                stageText = vm.stageText,
                errorText = vm.errorMessage,
                onRetry = vm::retryAsr,
                modifier = Modifier.weight(1f),
            )
        } else {
            PlayerPlaceholder(
                mediaName = vm.mediaName ?: "未命名媒体源",
                positionMs = vm.currentPreviewPositionMs,
                durationMs = vm.segments.lastOrNull()?.endMs ?: 0L,
                subtitleText = selectedSegment?.text ?: "点击下方字幕以定位并开始编辑",
            )

            SubtitleEditorWorkspace(
                segments = vm.segments,
                selectedIndex = vm.selectedSegmentIndex,
                onSelect = vm::selectSegment,
                selectedSegment = selectedSegment,
                onTextChange = vm::updateSelectedSegmentText,
                onNudgeStartBackward = { vm.nudgeSelectedSegmentStart(-500L) },
                onNudgeStartForward = { vm.nudgeSelectedSegmentStart(500L) },
                onNudgeEndBackward = { vm.nudgeSelectedSegmentEnd(-500L) },
                onNudgeEndForward = { vm.nudgeSelectedSegmentEnd(500L) },
                onSplitLines = vm::splitSelectedSegmentIntoTwoLines,
                onMergeNext = vm::mergeSelectedWithNext,
                modifier = Modifier.weight(1f),
            )
        }

        TranslationRecommendationPanel(vm = vm)

        if (vm.errorMessage != null && !vm.running) {
            Text(
                text = vm.errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.dangerText,
            )
        }
    }
}

@Composable
private fun WorkspaceHeader(
    stageText: String,
    resultText: String?,
    onBackToUpload: () -> Unit,
    onReplaceMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shape = AsrCardShape,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "转录处理工作台",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.titleText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = resultText ?: "当前状态：$stageText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedText,
                )
            }
            OutlinedButton(
                onClick = onBackToUpload,
                shape = AsrRoundButtonShape,
                border = BorderStroke(1.dp, tokens.secondaryButtonBorder),
            ) {
                Text("返回上传")
            }
            Button(
                onClick = onReplaceMedia,
                shape = AsrRoundButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("更换媒体")
            }
        }
    }
}

@Composable
private fun ProcessingPanel(
    progress: Float,
    estimate: String,
    stageText: String,
    errorText: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = AsrCardShape,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tokens.heroPanelContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = tokens.activeStepText,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "转录进度预估",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.titleText,
                    )
                    Text(
                        text = stageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.mutedText,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AsrCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = tokens.titleText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = estimate,
                            color = tokens.mutedText,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = tokens.progressIndicator,
                        trackColor = tokens.progressTrack,
                    )
                    Text(
                        text = "识别完成后会自动切换到字幕精修区，并展示智能翻译推荐。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.bodyText,
                    )
                }
            }

            if (errorText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.dangerText,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = onRetry, shape = AsrRoundButtonShape) {
                        Text("重新转录")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPlaceholder(
    mediaName: String,
    positionMs: Long,
    durationMs: Long,
    subtitleText: String,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AsrCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = tokens.editorAccent,
                    )
                    Column {
                        Text(
                            text = "视频预览占位",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.titleText,
                        )
                        Text(
                            text = mediaName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StagePill(text = "后续接入真实播放器")
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(tokens.playerPlaceholder),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(88.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(tokens.playerOverlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                        tint = tokens.activeStepText,
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f),
                ) {
                    Text(
                        text = subtitleText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTimecode(positionMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.titleText,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = formatTimecode(durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.mutedText,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        if (durationMs <= 0L) 0f else {
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = tokens.progressIndicator,
                    trackColor = tokens.progressTrack,
                )
            }
        }
    }
}

@Composable
private fun SubtitleEditorWorkspace(
    segments: List<SubtitleSegment>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    selectedSegment: SubtitleSegment?,
    onTextChange: (String) -> Unit,
    onNudgeStartBackward: () -> Unit,
    onNudgeStartForward: () -> Unit,
    onNudgeEndBackward: () -> Unit,
    onNudgeEndForward: () -> Unit,
    onSplitLines: () -> Unit,
    onMergeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth > 1080.dp
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SubtitleListPanel(
                    segments = segments,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1.2f),
                )
                SubtitleEditorPanel(
                    segment = selectedSegment,
                    onTextChange = onTextChange,
                    onNudgeStartBackward = onNudgeStartBackward,
                    onNudgeStartForward = onNudgeStartForward,
                    onNudgeEndBackward = onNudgeEndBackward,
                    onNudgeEndForward = onNudgeEndForward,
                    onSplitLines = onSplitLines,
                    onMergeNext = onMergeNext,
                    modifier = Modifier.weight(0.9f),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SubtitleListPanel(
                    segments = segments,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                SubtitleEditorPanel(
                    segment = selectedSegment,
                    onTextChange = onTextChange,
                    onNudgeStartBackward = onNudgeStartBackward,
                    onNudgeStartForward = onNudgeStartForward,
                    onNudgeEndBackward = onNudgeEndBackward,
                    onNudgeEndForward = onNudgeEndForward,
                    onSplitLines = onSplitLines,
                    onMergeNext = onMergeNext,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
    }
}

@Composable
private fun SubtitleListPanel(
    segments: List<SubtitleSegment>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = AsrCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "转录字幕",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.titleText,
                )
                StagePill(text = "${segments.size} 条")
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(segments) { index, item ->
                    val selected = index == selectedIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(index) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else tokens.subtitleCardContainer,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.52f) else tokens.subtitleCardBorder,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) tokens.subtitleCardSelected else Brush.verticalGradient(listOf(tokens.subtitleCardContainer, tokens.subtitleCardContainer)))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "${formatTimecode(item.startMs)}  ->  ${formatTimecode(item.endMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else tokens.mutedText,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else tokens.bodyText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleEditorPanel(
    segment: SubtitleSegment?,
    onTextChange: (String) -> Unit,
    onNudgeStartBackward: () -> Unit,
    onNudgeStartForward: () -> Unit,
    onNudgeEndBackward: () -> Unit,
    onNudgeEndForward: () -> Unit,
    onSplitLines: () -> Unit,
    onMergeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = AsrCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "字幕精修",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.titleText,
                )
                StagePill(text = "0.5s 微调")
            }

            if (segment == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "选择一条字幕后可直接定位并编辑",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.mutedText,
                    )
                }
            } else {
                TimeAdjustCard(
                    title = "起始时间",
                    value = formatTimecode(segment.startMs),
                    onBackward = onNudgeStartBackward,
                    onForward = onNudgeStartForward,
                )
                TimeAdjustCard(
                    title = "结束时间",
                    value = formatTimecode(segment.endMs),
                    onBackward = onNudgeEndBackward,
                    onForward = onNudgeEndForward,
                )
                TextField(
                    value = segment.text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("字幕文本") },
                    colors = TextFieldDefaults.colors(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onSplitLines,
                        modifier = Modifier.weight(1f),
                        shape = AsrRoundButtonShape,
                    ) {
                        Text("拆成两行")
                    }
                    Button(
                        onClick = onMergeNext,
                        modifier = Modifier.weight(1f),
                        shape = AsrRoundButtonShape,
                    ) {
                        Text("与下一条合并")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeAdjustCard(
    title: String,
    value: String,
    onBackward: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.mutedText,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.titleText,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackward, shape = AsrRoundButtonShape) {
                        Text("-0.5s")
                    }
                    Button(onClick = onForward, shape = AsrRoundButtonShape) {
                        Text("+0.5s")
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationRecommendationPanel(
    vm: AsrViewModel,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    val languageChoices = listOf(
        AsrLanguage.En,
        AsrLanguage.Zh,
        AsrLanguage.Ja,
        AsrLanguage.Ko,
        AsrLanguage.Es,
        AsrLanguage.Fr,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AsrCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, tokens.recommendationBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.recommendationPanel)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = tokens.editorAccent,
                    )
                    Column {
                        Text(
                            text = "智能翻译推荐",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.titleText,
                        )
                        Text(
                            text = "选择语种、术语库并扫描推荐策略",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.mutedText,
                        )
                    }
                }
                StagePill(
                    text = when (vm.translationPhase) {
                        TranslationWorkflowStage.Idle -> "待扫描"
                        TranslationWorkflowStage.Scanning -> "扫描中"
                        TranslationWorkflowStage.Ready -> "待翻译态"
                        TranslationWorkflowStage.Translating -> "翻译状态"
                    }
                )
            }

            OptionRow(
                label = "语种",
                options = languageChoices.map { it.label },
                selected = vm.targetLanguage.label,
                onSelect = { label ->
                    languageChoices.firstOrNull { it.label == label }?.let(vm::updateTargetLanguage)
                },
            )
            OptionRow(
                label = "术语库",
                options = vm.translationTermbases,
                selected = vm.selectedTermbase,
                onSelect = vm::selectTranslationTermbase,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = vm::scanTranslationRecommendation,
                    enabled = vm.segments.isNotEmpty() && vm.translationPhase != TranslationWorkflowStage.Scanning,
                    shape = AsrRoundButtonShape,
                ) {
                    Text(if (vm.translationPhase == TranslationWorkflowStage.Idle) "扫描推荐" else "重新扫描")
                }
                OutlinedButton(
                    onClick = vm::confirmTranslationRecommendation,
                    enabled = vm.translationPhase == TranslationWorkflowStage.Ready,
                    shape = AsrRoundButtonShape,
                ) {
                    Text("确定并进入翻译")
                }
            }

            when (vm.translationPhase) {
                TranslationWorkflowStage.Idle -> {
                    RecommendationPlaceholder(text = "扫描后会生成术语建议、翻译风格与模型推荐。")
                }

                TranslationWorkflowStage.Scanning -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "AI 智能体正在扫描字幕语义、主题与术语密度…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.bodyText,
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = tokens.progressIndicator,
                                trackColor = tokens.progressTrack,
                            )
                        }
                    }
                }

                TranslationWorkflowStage.Ready,
                TranslationWorkflowStage.Translating,
                -> {
                    val recommendation = vm.translationRecommendation
                    if (recommendation != null) {
                        RecommendationSummaryCard(
                            summary = recommendation.summary,
                            topic = recommendation.topic,
                            style = recommendation.style,
                        )
                    }
                }
            }

            if (vm.translationPhase == TranslationWorkflowStage.Ready || vm.translationPhase == TranslationWorkflowStage.Translating) {
                OptionRow(
                    label = "模型",
                    options = vm.translationModels,
                    selected = vm.selectedTranslationModel,
                    onSelect = vm::selectTranslationModel,
                )

                HorizontalDivider(color = tokens.topBarBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AI 智能推荐术语",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.titleText,
                    )
                    TextButton(onClick = vm::addGlossaryTerm) {
                        Text("新增术语")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    vm.glossaryTerms.forEach { term ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextField(
                                value = term.source,
                                onValueChange = { vm.updateGlossaryTermSource(term.id, it) },
                                modifier = Modifier.weight(1f),
                                label = { Text("术语原文") },
                                singleLine = true,
                            )
                            TextField(
                                value = term.target,
                                onValueChange = { vm.updateGlossaryTermTarget(term.id, it) },
                                modifier = Modifier.weight(1f),
                                label = { Text("推荐译法") },
                                singleLine = true,
                            )
                            TextButton(onClick = { vm.deleteGlossaryTerm(term.id) }) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationSummaryCard(
    summary: String,
    topic: String,
    style: String,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "AI 智能推荐",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.titleText,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.bodyText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StagePill(text = topic)
                StagePill(text = style)
            }
        }
    }
}

@Composable
private fun RecommendationPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.mutedText,
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.mutedText,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val active = option == selected
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else tokens.secondaryButtonBorder,
                    ),
                ) {
                    Text(
                        text = option,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else tokens.bodyText,
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.widthIn(max = 240.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, tokens.secondaryButtonBorder),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.bodyText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val milli = (safe % 1000L).toInt()
    val seconds = (totalSeconds % 60L).toInt()
    val totalMinutes = totalSeconds / 60L
    val minutes = (totalMinutes % 60L).toInt()
    val hours = (totalMinutes / 60L).toInt()
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${milli.toString().padStart(3, '0')}"
}
