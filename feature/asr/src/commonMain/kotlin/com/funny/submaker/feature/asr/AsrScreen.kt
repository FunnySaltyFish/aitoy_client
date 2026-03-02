package com.funny.submaker.feature.asr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.funny.submaker.core.kmp.rememberOpenFileLauncher
import com.funny.submaker.core.utils.FileSize
import kotlinx.coroutines.launch

private enum class WizardStep(
    val index: Int,
    val title: String,
) {
    Upload(1, "视频上传"),
    Language(2, "语言选择"),
    AiConfig(3, "AI配置"),
}

private data class RecentMediaUi(
    val title: String,
    val format: String,
    val duration: String,
    val sizeLabel: String?,
    val path: String,
    val isAudio: Boolean,
) {
    val meta: String
        get() = listOfNotNull(format, duration, sizeLabel).joinToString("  |  ")
}

private data class SelectedMediaPreview(
    val title: String,
    val format: String,
    val duration: String,
    val fps: String,
    val path: String,
    val sizeLabel: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsrScreen(
    projectId: String? = null,
    onOpenAuth: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel { AsrViewModel() }
    var currentStep by rememberSaveable { mutableIntStateOf(WizardStep.Upload.index) }
    var showUploadSheet by rememberSaveable { mutableStateOf(false) }
    var selectedPreview by remember { mutableStateOf<SelectedMediaPreview?>(null) }
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val sheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        vm.bindProject(projectId)
    }
    LaunchedEffect(vm.mediaUri, vm.mediaName, vm.mediaSizeBytes) {
        val fileName = vm.mediaName ?: return@LaunchedEffect
        selectedPreview = fileName.toPickedPreview(vm.mediaSizeBytes, vm.mediaUri?.toString())
        showUploadSheet = true
        sheetState.partialExpand()
    }

    val openFileLauncher = rememberOpenFileLauncher { uri ->
        if (uri != null) vm.onMediaPicked(uri)
    }
    val openMediaPicker = remember(openFileLauncher) {
        { openFileLauncher.launch(arrayOf("video/*", "audio/*")) }
    }
    ProvideAsrUiTokens {
        val tokens = rememberAsrUiTokens()
        val canShowSheet =
            currentStep == WizardStep.Upload.index && showUploadSheet && selectedPreview != null
        val content: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxSize()) {
                WizardTopBar(
                    step = currentStep,
                    onCancel = {
                        if (currentStep == WizardStep.Upload.index) {
                            onCancel?.invoke()
                        } else {
                            currentStep -= 1
                        }
                    },
                    onNext = {
                        if (currentStep < WizardStep.AiConfig.index) currentStep += 1
                    },
                )
                WizardStepRow(currentStep = currentStep)

                when (currentStep) {
                    WizardStep.Upload.index -> UploadStepPage(
                        mediaName = vm.mediaName,
                        mediaSize = vm.mediaSizeBytes,
                        linkedProject = vm.linkedProjectName,
                        recentFiles = vm.recentMedia,
                        stageText = vm.stageText,
                        errorText = vm.errorMessage,
                        onPickMedia = openMediaPicker,
                        onRecentClicked = { recent ->
                            vm.onRecentMediaSelected(recent)
                            selectedPreview = recent.toPreview()
                            showUploadSheet = true
                            scope.launch { sheetState.partialExpand() }
                        },
                        message = message,
                        bottomPadding = if (canShowSheet) 112.dp else 24.dp,
                    )

                    WizardStep.Language.index -> AsrLanguageStep(
                        sourceLanguage = vm.sourceLanguage,
                        targetLanguage = vm.targetLanguage,
                        recentPairs = vm.recentLanguagePairs,
                        onSourceChange = vm::updateSourceLanguage,
                        onTargetChange = vm::updateTargetLanguage,
                        onSwap = vm::swapLanguages,
                        onRecentPairClick = vm::applyRecentLanguagePair,
                        onBack = { currentStep = WizardStep.Upload.index },
                        onNext = {
                            vm.confirmLanguageSelection()
                            currentStep = WizardStep.AiConfig.index
                        },
                    )

                    else -> AsrAiConfigStep(
                        onBack = { currentStep = WizardStep.Language.index },
                        onConfirm = { vm.startAsr() },
                    )
                }
            }
        }

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.pageBackground)
                    .imePadding(),
            ) {
                if (canShowSheet) {
                    val preview = selectedPreview ?: return@Box
                    BottomSheetScaffold(
                        scaffoldState = sheetScaffoldState,
                        sheetPeekHeight = 44.dp,
                        sheetDragHandle = {},
                        sheetContent = {
                            UploadBottomSheet(
                                preview = preview,
                                onReplace = openMediaPicker,
                                onConfirm = { currentStep = WizardStep.Language.index },
                                enabled = vm.mediaUri != null,
                            )
                        },
                        containerColor = Color.Transparent,
                    ) { _ ->
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun WizardTopBar(
    step: Int,
    onCancel: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    val nextLabel = if (step >= WizardStep.AiConfig.index) "完成" else "下一步"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AsrTopBarHeight)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "取消",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.bodyText,
            modifier = Modifier.clickable(onClick = onCancel),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = "新建向导",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.titleText,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = nextLabel,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.bodyText,
            modifier = Modifier.clickable(onClick = onNext),
        )
    }
    HorizontalDivider(color = tokens.topBarBorder)
}

@Composable
private fun WizardStepRow(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        WizardStep.entries.forEach { step ->
            StepItem(
                number = step.index,
                title = step.title,
                isActive = step.index == currentStep,
            )
        }
    }
}

@Composable
private fun StepItem(
    number: Int,
    title: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Column(
        modifier = modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AsrStepShape)
                .background(if (isActive) tokens.activeStepContainer else tokens.inactiveStepContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (number == 1) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleFilled,
                    contentDescription = null,
                    tint = if (isActive) tokens.activeStepText else tokens.inactiveStepText,
                )
            } else {
                Text(
                    text = number.toString(),
                    color = if (isActive) tokens.activeStepText else tokens.inactiveStepText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = title,
            color = tokens.bodyText,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UploadStepPage(
    mediaName: String?,
    mediaSize: Long?,
    linkedProject: String?,
    recentFiles: List<AsrRecentMedia>,
    stageText: String,
    errorText: String?,
    onPickMedia: () -> Unit,
    onRecentClicked: (AsrRecentMedia) -> Unit,
    message: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "上传媒体源",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.titleText,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "选择视频或音频文件以开始提取字幕。",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.mutedText,
            )
        }

        if (linkedProject != null) {
            Text(
                text = "当前项目：$linkedProject",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.bodyText,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(AsrUploadShape)
                .background(tokens.uploadCardContainer)
                .dashedRoundBorder(tokens.uploadCardBorder)
                .clickable(onClick = onPickMedia)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(AsrStepShape)
                        .background(tokens.uploadIconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Upload,
                        contentDescription = null,
                        tint = tokens.activeStepText,
                    )
                }
                Text(
                    text = "点击浏览或拖入文件",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.bodyText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "支持 MP4, MKV, AVI, MOV (最大 2GB)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedText,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Text(
            text = "最近文件",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.mutedText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        if (recentFiles.isEmpty()) {
            Text(
                text = "暂无最近文件",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentFiles.forEach { item ->
                    RecentMediaCard(
                        item = item.toUi(),
                        onClick = { onRecentClicked(item) },
                    )
                }
            }
        }

        Text(
            text = "当前阶段：$stageText",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.mutedText,
        )

        if (mediaName != null) {
            Text(
                text = "已选择：$mediaName${mediaSize?.let { " (${FileSize.fromBytes(it)})" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.bodyText,
            )
        }

        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.bodyText,
            )
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecentMediaCard(
    item: RecentMediaUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.recentCardContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.recentCardLeading),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.isAudio) Icons.Rounded.Mic else Icons.Rounded.Folder,
                contentDescription = null,
                tint = tokens.mutedText,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                color = tokens.bodyText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.meta,
                color = tokens.mutedText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = tokens.mutedText,
        )
    }
}

@Composable
private fun UploadBottomSheet(
    preview: SelectedMediaPreview,
    onReplace: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 112.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.selectedCardContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleFilled,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = tokens.mutedText,
                )
                Text(
                    text = preview.duration,
                    color = tokens.bodyText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = preview.title,
                    color = tokens.bodyText,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaChip(text = preview.format)
                    MediaChip(text = preview.fps)
                }
                Text(
                    text = preview.path,
                    color = tokens.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.sizeLabel != null) {
                    Text(
                        text = "大小 ${preview.sizeLabel}",
                        color = tokens.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onReplace,
                        shape = AsrRoundButtonShape,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = tokens.secondaryButtonBorder,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = tokens.secondaryButtonText,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("更换文件")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = enabled,
                        shape = AsrRoundButtonShape,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primaryButtonContainer,
                            contentColor = tokens.primaryButtonText,
                        ),
                    ) {
                        Text("确认并继续")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.chipContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = tokens.chipText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun Modifier.dashedRoundBorder(color: Color): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                cap = StrokeCap.Round,
            ),
            cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx()),
        )
    }

private fun AsrRecentMedia.toPreview(): SelectedMediaPreview {
    return SelectedMediaPreview(
        title = name,
        format = name.substringAfterLast('.', "媒体").uppercase(),
        duration = "--:--",
        fps = "30fps",
        path = uri,
        sizeLabel = sizeBytes?.let { FileSize.fromBytes(it).toString() },
    )
}

private fun String.toPickedPreview(sizeBytes: Long?, uriText: String?): SelectedMediaPreview {
    val formatText = substringAfterLast('.', "MP4").uppercase()
    return SelectedMediaPreview(
        title = this,
        format = formatText,
        duration = "--:--",
        fps = "30fps",
        path = uriText ?: "-",
        sizeLabel = sizeBytes?.let { FileSize.fromBytes(it).toString() },
    )
}

private fun AsrRecentMedia.toUi(): RecentMediaUi {
    val titleText = name.ifBlank { "未命名文件" }
    return RecentMediaUi(
        title = titleText,
        format = titleText.substringAfterLast('.', "媒体").uppercase(),
        duration = "--:--",
        sizeLabel = sizeBytes?.let { FileSize.fromBytes(it).toString() },
        path = uri,
        isAudio = mimeType?.startsWith("audio/") == true,
    )
}
