package com.funny.submaker.database

import com.funny.submaker.database.model.SubtitleProjectEntity
import com.funny.submaker.database.sync.OwnerScopedSyncStore

object SubtitleProjectSyncStore : OwnerScopedSyncStore<SubtitleProjectEntity> {
    override suspend fun queryAllByOwner(ownerUid: String): List<SubtitleProjectEntity> {
        val db = SubMakerDatabaseFactory.createDatabaseIfReady() ?: return emptyList()
        return db.subtitleProjectDao().queryAllByOwner(ownerUid)
    }

    override suspend fun queryUpdatedSince(ownerUid: String, sinceMs: Long): List<SubtitleProjectEntity> {
        val db = SubMakerDatabaseFactory.createDatabaseIfReady() ?: return emptyList()
        return db.subtitleProjectDao().queryUpdatedSince(ownerUid, sinceMs)
    }

    override suspend fun upsertAll(items: List<SubtitleProjectEntity>) {
        if (items.isEmpty()) return
        val db = SubMakerDatabaseFactory.createDatabaseIfReady() ?: return
        db.subtitleProjectDao().upsertAll(items)
    }

    override suspend fun clearOwnerData(ownerUid: String) {
        val db = SubMakerDatabaseFactory.createDatabaseIfReady() ?: return
        db.subtitleProjectDao().deleteByOwner(ownerUid)
    }
}
