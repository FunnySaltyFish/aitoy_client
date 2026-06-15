package com.funny.aitoy.database.sync

import com.funny.aitoy.database.SubtitleProjectSyncStore
import com.funny.aitoy.database.model.SubtitleProjectEntity

object SubtitleProjectTableSyncAdapter : LastWriteWinsTableSyncAdapter<SubtitleProjectEntity>(
    tableName = "subtitle_project",
    store = SubtitleProjectSyncStore,
) {
    override fun entityId(entity: SubtitleProjectEntity): String {
        return entity.id
    }

    override fun entityUpdatedAtMs(entity: SubtitleProjectEntity): Long {
        return entity.updatedAtEpochMillis
    }

    override fun toSyncRow(entity: SubtitleProjectEntity): LocalTableRow {
        return entity.toSyncRow()
    }

    override fun toEntity(ownerUid: String, row: LocalTableRow): SubtitleProjectEntity {
        return row.toEntity(ownerUid)
    }
}

private fun SubtitleProjectEntity.toSyncRow(): LocalTableRow {
    return LocalTableRow(
        id = id,
        createdAtMs = createdAtEpochMillis,
        updatedAtMs = updatedAtEpochMillis,
        deleted = deleted,
        data = mapOf(
            "name" to name,
            "sourceFileName" to sourceFileName,
            "durationMs" to durationMs.toString(),
            "segmentCount" to segmentCount.toString(),
            "status" to status,
            "lastExportFormat" to lastExportFormat,
            "starred" to starred.toString(),
        ),
    )
}

private fun LocalTableRow.toEntity(ownerUid: String): SubtitleProjectEntity {
    return SubtitleProjectEntity(
        id = id,
        ownerUid = ownerUid,
        name = data["name"] ?: "",
        sourceFileName = data["sourceFileName"] ?: "",
        durationMs = data["durationMs"]?.toLongOrNull() ?: 0L,
        segmentCount = data["segmentCount"]?.toIntOrNull() ?: 0,
        status = data["status"] ?: "draft",
        lastExportFormat = data["lastExportFormat"] ?: "",
        starred = data["starred"]?.toBooleanStrictOrNull() ?: false,
        createdAtEpochMillis = createdAtMs,
        updatedAtEpochMillis = updatedAtMs,
        deleted = deleted,
    )
}
