package com.funny.aitoy.network.api.service
import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import retrofit2.http.Body
import retrofit2.http.POST

interface SyncService {
    @POST("sync/upsert")
    suspend fun upsert(@Body request: SyncUpsertRequest): ApiResp<SyncUpsertPayload>
}

@Serializable
data class SyncUpsertRequest(
    val settings: List<SettingSyncItem> = emptyList(),
    val tables: List<TableSyncItem> = emptyList(),
    val projects: List<ProjectSyncItem> = emptyList(),
    val clientSyncVersionMs: Long = 0,
    val source: String = "kmp-client",
)

@Serializable
data class SyncUpsertPayload(
    val serverSyncVersionMs: Long = 0,
    val settings: List<SettingSyncItem> = emptyList(),
    val tables: List<TableSyncItem> = emptyList(),
    val projects: List<ProjectSyncItem> = emptyList(),
    val stats: SyncStats = SyncStats(),
)

@Serializable
data class SyncStats(
    val settingsWritten: Int = 0,
    val settingsSkipped: Int = 0,
    val tablesWritten: Int = 0,
    val tablesSkipped: Int = 0,
)

@Serializable
data class SettingSyncItem(
    val key: String,
    val value: String,
    val updatedAtMs: Long,
    val syncVersionMs: Long = 0,
)

@Serializable
data class ProjectSyncItem(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deleted: Boolean = false,
    val syncVersionMs: Long = 0,
)

@Serializable
data class TableSyncItem(
    val table: String,
    val rows: List<TableRowSyncItem> = emptyList(),
)

@Serializable
data class TableRowSyncItem(
    val id: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
    val data: JsonObject = buildJsonObject { },
    val syncVersionMs: Long = 0,
)
