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
import com.funny.submaker.feature.asr.components.AsrTranscriptionWorkspace
import kotlinx.coroutines.launch

private enum class AsrWorkflowPage {
    Upload,
    Transcription,
}

private data class RecentMediaUi(
    val title: String,
    val format: String,
    val sizeLabel: String?,
    val isAudio: Boolean,
) {
    val meta: String
        get() = listOfNotNull(format, sizeLabel).joinToString("  |  ")
}

private data class SelectedMediaPreview(
    val title: String,
    val format: String,
    val duration: String,
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
    var currentPage by rememberSaveable { mutableStateOf(AsrWorkflowPage.Upload) }
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
        currentPage = AsrWorkflowPage.Upload
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
        val canShowSheet = currentPage == AsrWorkflowPage.Upload && showUploadSheet && selectedPreview != null
        val uploadContent: @Composable () -> Unit = {
            UploadPage(
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
                onCancel = onCancel,
                message = message,
                onOpenAuth = onOpenAuth,
                bottomPadding = if (canShowSheet) 116.dp else 28.dp,
            )
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
                when (currentPage) {
                    AsrWorkflowPage.Upload -> {
                        if (canShowSheet) {
                            val preview = selectedPreview ?: return@Box
                            BottomSheetScaffold(
                                scaffoldState = sheetScaffoldState,
                                sheetPeekHeight = 46.dp,
                                sheetDragHandle = {},
                                sheetContent = {
                                    UploadBottomSheet(
                                        preview = preview,
                                        onReplace = openMediaPicker,
                                        onConfirm = {
                                            showUploadSheet = false
                                            currentPage = AsrWorkflowPage.Transcription
                                            vm.startAsr()
                                        },
                                        enabled = vm.mediaUri != null,
                                    )
                                },
                                containerColor = Color.Transparent,
                            ) { _ ->
                                uploadContent()
                            }
                        } else {
                            uploadContent()
                        }
                    }

                    AsrWorkflowPage.Transcription -> {
                        AsrTranscriptionWorkspace(
                            vm = vm,
                            onBackToUpload = {
                                currentPage = AsrWorkflowPage.Upload
                                showUploadSheet = vm.mediaUri != null
                                if (showUploadSheet) {
                                    scope.launch { sheetState.partialExpand() }
                                }
                            },
                            onReplaceMedia = {
                                currentPage = AsrWorkflowPage.Upload
                                openMediaPicker()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadPage(
    mediaName: String?,
    mediaSize: Long?,
    linkedProject: String?,
    recentFiles: List<AsrRecentMedia>,
    stageText: String,
    errorText: String?,
    onPickMedia: () -> Unit,
    onRecentClicked: (AsrRecentMedia) -> Unit,
    onCancel: (() -> Unit)?,
    message: String?,
    onOpenAuth: (() -> Unit)?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        UploadHeader(
            linkedProject = linkedProject,
            onCancel = onCancel,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AsrCardShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "上传媒体源",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.titleText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "只保留最短路径：选择媒体文件，确认后直接进入转录处理工作台。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.mutedText,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(AsrUploadShape)
                        .background(tokens.uploadCardContainer)
                        .dashedRoundBorder(tokens.uploadCardBorder)
                        .clickable(onClick = onPickMedia)
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
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
                            text = "点击上传媒体源",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.bodyText,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "支持视频与音频文件，确认后立即进入转录处理。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.mutedText,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "MP4 / MKV / MOV / WAV / MP3",
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.bodyText,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "最近选择的媒体源",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.titleText,
                    )
                    Text(
                        text = "当前状态：$stageText",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.mutedText,
                    )
                }

                if (recentFiles.isEmpty()) {
                    EmptyRecentMediaState()
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

                if (mediaName != null) {
                    Text(
                        text = "当前已选：$mediaName${mediaSize?.let { " (${FileSize.fromBytes(it)})" } ?: ""}",
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
                        color = tokens.dangerText,
                    )
                }

                if (onOpenAuth != null) {
                    HorizontalDivider(color = tokens.topBarBorder)
                    Text(
                        text = "如需切换账号或补充权限，可先前往登录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.mutedText,
                    )
                    OutlinedButton(
                        onClick = onOpenAuth,
                        shape = AsrRoundButtonShape,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("打开登录")
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadHeader(
    linkedProject: String?,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AsrCardShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "新建项目",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.titleText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = linkedProject?.let { "已绑定项目：$it" } ?: "先上传媒体，再进入转录处理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedText,
                )
            }
            if (onCancel != null) {
                Text(
                    text = "关闭",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.bodyText,
                    modifier = Modifier.clickable(onClick = onCancel),
                )
            }
        }
    }
}

@Composable
private fun EmptyRecentMediaState(
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = "暂无历史媒体源，上传后会在这里保留最近记录。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.mutedText,
        )
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
            .clip(RoundedCornerShape(18.dp))
            .background(tokens.recentCardContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    .clip(RoundedCornerShape(12.dp))
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
                Text(
                    text = preview.format,
                    color = tokens.mutedText,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
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
                        Text("选择并继续")
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
        path = uri,
        sizeLabel = sizeBytes?.let { FileSize.fromBytes(it).toString() },
    )
}

private fun String.toPickedPreview(sizeBytes: Long?, uriText: String?): SelectedMediaPreview {
    return SelectedMediaPreview(
        title = this,
        format = substringAfterLast('.', "媒体").uppercase(),
        duration = "--:--",
        path = uriText ?: "-",
        sizeLabel = sizeBytes?.let { FileSize.fromBytes(it).toString() },
    )
}

private fun AsrRecentMedia.toUi(): RecentMediaUi {
    val titleText = name.ifBlank { "未命名文件" }
    return RecentMediaUi(
        title = titleText,
        format = titleText.substringAfterLast('.', "媒体").uppercase(),
        sizeLabel = sizeBytes?.let { FileSize.fromBytes(it).toString() },
        isAudio = mimeType?.startsWith("audio/") == true,
    )
}
