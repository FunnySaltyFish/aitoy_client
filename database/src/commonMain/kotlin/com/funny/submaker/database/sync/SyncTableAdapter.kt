package com.funny.submaker.database.sync

interface SyncTableAdapter {
    val tableName: String

    suspend fun mergeOwnerData(fromOwnerUid: String, toOwnerUid: String)

    suspend fun clearOwnerData(ownerUid: String)

    suspend fun queryUpdatedRows(ownerUid: String, sinceMs: Long): List<LocalTableRow>

    suspend fun applyRows(ownerUid: String, rows: List<LocalTableRow>)
}

object LocalTableSyncRegistry {
    private val adapters: MutableMap<String, SyncTableAdapter> = linkedMapOf()

    fun register(adapter: SyncTableAdapter) {
        adapters[adapter.tableName] = adapter
    }

    fun allAdapters(): List<SyncTableAdapter> = adapters.values.toList()

    fun getAdapter(tableName: String): SyncTableAdapter? = adapters[tableName]
}

data class LocalTableRow(
    val id: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deleted: Boolean,
    val data: Map<String, String>,
)
