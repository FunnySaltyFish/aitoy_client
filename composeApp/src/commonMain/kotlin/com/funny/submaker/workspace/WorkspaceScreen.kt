package com.funny.submaker.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.funny.submaker.core.utils.TimeUtils
import com.funny.submaker.database.model.SubtitleProjectEntity

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun WorkspaceScreen(
    vm: WorkspaceViewModel,
    onOpenAsr: () -> Unit,
    onOpenProjectAsr: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberWorkspaceUiTokens()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val useSideNavigation = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    var currentPage by rememberSaveable { mutableStateOf(WorkspaceMainPage.Projects) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.pageBackground),
    ) {
        if (useSideNavigation) {
            Row(modifier = Modifier.fillMaxSize()) {
                WorkspaceNavigationRail(
                    selectedPage = currentPage,
                    onSelect = { currentPage = it },
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
                )
                WorkspacePageContent(
                    page = currentPage,
                    vm = vm,
                    onOpenAsr = onOpenAsr,
                    onOpenProjectAsr = onOpenProjectAsr,
                    onOpenAccount = onOpenAccount,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                bottomBar = {
                    WorkspaceBottomBar(
                        selectedPage = currentPage,
                        onSelect = { currentPage = it },
                    )
                },
            ) { innerPadding ->
                WorkspacePageContent(
                    page = currentPage,
                    vm = vm,
                    onOpenAsr = onOpenAsr,
                    onOpenProjectAsr = onOpenProjectAsr,
                    onOpenAccount = onOpenAccount,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceBottomBar(
    selectedPage: WorkspaceMainPage,
    onSelect: (WorkspaceMainPage) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
    ) {
        WorkspaceMainPage.entries.forEach { page ->
            NavigationBarItem(
                selected = selectedPage == page,
                onClick = { onSelect(page) },
                icon = { Text(text = page.badge) },
                label = { Text(page.label) },
            )
        }
    }
}

@Composable
private fun WorkspaceNavigationRail(
    selectedPage: WorkspaceMainPage,
    onSelect: (WorkspaceMainPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier
            .clip(WorkspacePanelShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        WorkspaceMainPage.entries.forEach { page ->
            NavigationRailItem(
                selected = selectedPage == page,
                onClick = { onSelect(page) },
                icon = { Text(text = page.badge) },
                label = { Text(page.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun WorkspacePageContent(
    page: WorkspaceMainPage,
    vm: WorkspaceViewModel,
    onOpenAsr: () -> Unit,
    onOpenProjectAsr: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        WorkspaceMainPage.Projects -> WorkspaceProjectsPage(
            vm = vm,
            onOpenAsr = onOpenAsr,
            onOpenProjectAsr = onOpenProjectAsr,
            modifier = modifier,
        )

        WorkspaceMainPage.Settings -> WorkspaceSettingsPage(
            onOpenAsr = onOpenAsr,
            modifier = modifier,
        )

        WorkspaceMainPage.Profile -> WorkspaceProfilePage(
            vm = vm,
            onOpenAccount = onOpenAccount,
            modifier = modifier,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WorkspaceProjectsPage(
    vm: WorkspaceViewModel,
    onOpenAsr: () -> Unit,
    onOpenProjectAsr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberWorkspaceUiTokens()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                ),
                title = {
                    Text(
                        text = "我的项目",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    Button(onClick = onOpenAsr) {
                        Text("新建识别")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::createProject) {
                Text("+")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clip(WorkspacePanelShape)
                .background(tokens.sidePanelColor)
                .border(1.dp, tokens.sidePanelBorder, WorkspacePanelShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.searchKeyword,
                onValueChange = { vm.searchKeyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索项目") },
                placeholder = { Text("项目名 / 文件名") },
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProjectStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = vm.statusFilter == filter,
                        onClick = { vm.statusFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }

            val list = vm.filteredProjects
            if (vm.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在加载项目...", color = tokens.weakTextColor)
                }
            } else if (list.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无项目，点击右下角“+”开始", color = tokens.weakTextColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            selected = vm.selectedProjectId == project.id,
                            onSelect = { vm.selectProject(project.id) },
                            onOpenAsr = { onOpenProjectAsr(project.id) },
                            onToggleStar = { vm.toggleStar(project.id) },
                        )
                    }
                }
            }

            val err = vm.errorMessage
            if (err != null) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceSettingsPage(
    onOpenAsr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberWorkspaceUiTokens()
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(WorkspacePanelShape)
            .background(tokens.sidePanelColor)
            .border(1.dp, tokens.sidePanelBorder, WorkspacePanelShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.titleColor,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "这里将逐步接入识别参数、导出偏好和云服务配置。",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.weakTextColor,
        )
        Card(shape = WorkspaceCardShape, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.heroCardColor)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "快速入口",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.titleColor
                )
                Button(onClick = onOpenAsr) {
                    Text("打开 ASR 页面")
                }
            }
        }
    }
}

@Composable
private fun WorkspaceProfilePage(
    vm: WorkspaceViewModel,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberWorkspaceUiTokens()
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(WorkspacePanelShape)
            .background(tokens.sidePanelColor)
            .border(1.dp, tokens.sidePanelBorder, WorkspacePanelShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "个人信息",
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.titleColor,
            fontWeight = FontWeight.SemiBold,
        )
        Card(shape = WorkspaceCardShape, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.heroCardColor)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "项目总数 ${vm.projects.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.titleColor,
                )
                Text(
                    text = "已星标 ${vm.projects.count { it.starred }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.weakTextColor,
                )
                Button(onClick = onOpenAccount) {
                    Text("账号与权益")
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: SubtitleProjectEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenAsr: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val tokens = rememberWorkspaceUiTokens()
    val borderColor = if (selected) tokens.selectedCardBorder else tokens.listCardBorder
    Card(
        shape = WorkspaceCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(WorkspaceCardShape)
            .clickable(onClick = onSelect)
            .border(1.dp, borderColor, WorkspaceCardShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.listCardColor)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.titleColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .clip(WorkspaceChipShape)
                        .background(tokens.primaryChipContainer)
                        .clickable(onClick = onToggleStar)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (project.starred) "已星标" else "星标",
                        color = tokens.primaryChipText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Text(
                text = if (project.sourceFileName.isBlank()) "未关联源文件" else project.sourceFileName,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.weakTextColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusBadge(status = project.status)
                MetadataBadge(text = "片段 ${project.segmentCount}")
                MetadataBadge(text = formatDuration(project.durationMs))
                val export = project.lastExportFormat
                if (export.isNotBlank()) {
                    MetadataBadge(text = export)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenAsr) {
                    Text("打开识别")
                }
            }

            Text(
                text = "更新时间 ${
                    TimeUtils.formatTime(
                        project.updatedAtEpochMillis,
                        "%02d-%02d %02d:%02d"
                    )
                }",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.weakTextColor,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val tokens = rememberWorkspaceUiTokens()
    val (label, container, textColor) = when (status) {
        ProjectStatus.Done.value -> Triple(
            ProjectStatus.Done.label,
            tokens.successContainer,
            tokens.successText
        )

        ProjectStatus.Running.value -> Triple(
            ProjectStatus.Running.label,
            tokens.warningContainer,
            tokens.warningText
        )

        ProjectStatus.Failed.value -> Triple(
            ProjectStatus.Failed.label,
            tokens.dangerContainer,
            tokens.dangerText
        )

        else -> Triple(
            ProjectStatus.Draft.label,
            tokens.primaryChipContainer,
            tokens.primaryChipText
        )
    }
    Box(
        modifier = Modifier
            .clip(WorkspaceChipShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = textColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MetadataBadge(text: String) {
    val tokens = rememberWorkspaceUiTokens()
    Box(
        modifier = Modifier
            .clip(WorkspaceChipShape)
            .background(tokens.primaryChipContainer.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = tokens.primaryChipText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%02d:%02d".format(min, sec)
}

private enum class WorkspaceMainPage(
    val label: String,
    val badge: String,
) {
    Projects(label = "项目", badge = "项"),
    Settings(label = "设置", badge = "设"),
    Profile(label = "个人", badge = "我"),
}
