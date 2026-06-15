package com.funny.aitoy.database.sync

abstract class LastWriteWinsTableSyncAdapter<T>(
    final override val tableName: String,
    private val store: OwnerScopedSyncStore<T>,
) : SyncTableAdapter {
    protected abstract fun entityId(entity: T): String

    protected abstract fun entityUpdatedAtMs(entity: T): Long

    protected abstract fun toSyncRow(entity: T): LocalTableRow

    protected abstract fun toEntity(ownerUid: String, row: LocalTableRow): T

    override suspend fun mergeOwnerData(fromOwnerUid: String, toOwnerUid: String) {
        if (fromOwnerUid == toOwnerUid) return
        val source = store.queryAllByOwner(fromOwnerUid)
        val target = store.queryAllByOwner(toOwnerUid).associateBy { entityId(it) }
        val merged = source.mapNotNull { local ->
            val remote = target[entityId(local)]
            if (remote == null || entityUpdatedAtMs(local) >= entityUpdatedAtMs(remote)) {
                toEntity(ownerUid = toOwnerUid, row = toSyncRow(local))
            } else {
                null
            }
        }
        store.upsertAll(merged)
    }

    override suspend fun clearOwnerData(ownerUid: String) {
        store.clearOwnerData(ownerUid)
    }

    override suspend fun queryUpdatedRows(ownerUid: String, sinceMs: Long): List<LocalTableRow> {
        return store.queryUpdatedSince(ownerUid, sinceMs).map { toSyncRow(it) }
    }

    override suspend fun applyRows(ownerUid: String, rows: List<LocalTableRow>) {
        store.upsertAll(rows.map { toEntity(ownerUid, it) })
    }
}

