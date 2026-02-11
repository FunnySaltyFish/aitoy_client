package com.funny.submaker.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.core.utils.nowMs
import com.funny.submaker.database.SubtitleProjectRepository
import com.funny.submaker.database.model.SubtitleProjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkspaceViewModel : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var loading by mutableStateOf(true)
    var searchKeyword by mutableStateOf("")
    var statusFilter by mutableStateOf(ProjectStatusFilter.All)
    var projects by mutableStateOf<List<SubtitleProjectEntity>>(emptyList())
    var selectedProjectId by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    init {
        observeWorkspaceProjects()
    }

    val filteredProjects: List<SubtitleProjectEntity>
        get() {
            val keyword = searchKeyword.trim().lowercase()
            return projects.filter { project ->
                val statusOk = when (statusFilter) {
                    ProjectStatusFilter.All -> true
                    ProjectStatusFilter.Draft -> project.status == ProjectStatus.Draft.value
                    ProjectStatusFilter.Running -> project.status == ProjectStatus.Running.value
                    ProjectStatusFilter.Done -> project.status == ProjectStatus.Done.value
                    ProjectStatusFilter.Failed -> project.status == ProjectStatus.Failed.value
                }
                val keywordOk = keyword.isBlank() ||
                        project.name.lowercase().contains(keyword) ||
                        project.sourceFileName.lowercase().contains(keyword)
                statusOk && keywordOk
            }
        }

    fun createProject() {
        val now = nowMs()
        val ownerUid = currentOwnerUid()
        val entity = SubtitleProjectEntity(
            ownerUid = ownerUid,
            id = "local_$now",
            name = "新建项目 ${projects.size + 1}",
            sourceFileName = "",
            durationMs = 0L,
            segmentCount = 0,
            status = ProjectStatus.Draft.value,
            lastExportFormat = "",
            starred = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            deleted = false,
        )
        persistProject(entity)
    }

    fun selectProject(projectId: String) {
        selectedProjectId = projectId
    }

    fun toggleStar(projectId: String) {
        val target = projects.firstOrNull { it.id == projectId } ?: return
        persistProject(
            target.copy(
                starred = !target.starred,
                updatedAtEpochMillis = nowMs(),
            ),
        )
    }

    private fun observeWorkspaceProjects() {
        vmScope.launch {
            val flow = SubtitleProjectRepository.observeWorkspaceByOwner(currentOwnerUid())
            if (flow == null) {
                loading = false
                projects = demoProjects(currentOwnerUid())
                selectedProjectId = projects.firstOrNull()?.id
                return@launch
            }
            flow.collectLatest { latest ->
                loading = false
                projects = latest
                if (selectedProjectId == null || latest.none { it.id == selectedProjectId }) {
                    selectedProjectId = latest.firstOrNull()?.id
                }
            }
        }
    }

    private fun persistProject(entity: SubtitleProjectEntity) {
        vmScope.launch {
            val flow = SubtitleProjectRepository.observeWorkspaceByOwner(currentOwnerUid())
            if (flow == null) {
                val next = projects.filterNot { it.id == entity.id } + entity
                projects = next.sortedWith(
                    compareByDescending<SubtitleProjectEntity> { it.starred }
                        .thenByDescending { it.updatedAtEpochMillis },
                )
                selectedProjectId = entity.id
                return@launch
            }
            SubtitleProjectRepository.upsert(entity)
                .onSuccess {
                    selectedProjectId = entity.id
                }
                .onFailure {
                    errorMessage = it.message ?: "保存项目失败"
                }
        }
    }

    private fun currentOwnerUid(): String {
        val uid = SubMakerPrefs.user.uid
        if (uid.isNotBlank()) return "uid:$uid"
        return "device:${SubMakerPrefs.deviceId}"
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}

enum class ProjectStatus(val value: String, val label: String) {
    Draft("draft", "草稿"),
    Running("running", "识别中"),
    Done("done", "已完成"),
    Failed("failed", "失败"),
}

enum class ProjectStatusFilter(val label: String) {
    All("全部"),
    Draft("草稿"),
    Running("识别中"),
    Done("已完成"),
    Failed("失败"),
}

private fun demoProjects(ownerUid: String): List<SubtitleProjectEntity> {
    val now = nowMs()
    return listOf(
        SubtitleProjectEntity(
            ownerUid = ownerUid,
            id = "demo_1",
            name = "产品发布会回放",
            sourceFileName = "launch_event.mp4",
            durationMs = 2_460_000L,
            segmentCount = 289,
            status = ProjectStatus.Done.value,
            lastExportFormat = "SRT",
            starred = true,
            createdAtEpochMillis = now - 86_400_000L,
            updatedAtEpochMillis = now - 600_000L,
            deleted = false,
        ),
        SubtitleProjectEntity(
            ownerUid = ownerUid,
            id = "demo_2",
            name = "访谈样片-第一期",
            sourceFileName = "interview_ep1.wav",
            durationMs = 1_820_000L,
            segmentCount = 0,
            status = ProjectStatus.Running.value,
            lastExportFormat = "",
            starred = false,
            createdAtEpochMillis = now - 43_200_000L,
            updatedAtEpochMillis = now - 120_000L,
            deleted = false,
        ),
        SubtitleProjectEntity(
            ownerUid = ownerUid,
            id = "demo_3",
            name = "课程试讲",
            sourceFileName = "course_trial.mov",
            durationMs = 930_000L,
            segmentCount = 0,
            status = ProjectStatus.Draft.value,
            lastExportFormat = "",
            starred = false,
            createdAtEpochMillis = now - 18_000_000L,
            updatedAtEpochMillis = now - 180_000L,
            deleted = false,
        ),
    )
}
