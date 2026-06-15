package com.funny.aitoy.database

import com.funny.aitoy.database.model.SubtitleProjectEntity
import kotlinx.coroutines.flow.Flow

object SubtitleProjectRepository {
    fun observeWorkspaceByOwner(ownerUid: String): Flow<List<SubtitleProjectEntity>>? {
        val db = AiToyDatabaseFactory.createDatabaseIfReady() ?: return null
        return db.subtitleProjectDao().observeWorkspaceByOwner(ownerUid)
    }

    suspend fun upsert(entity: SubtitleProjectEntity): Result<Unit> {
        return runCatching {
            val db = AiToyDatabaseFactory.createDatabaseIfReady()
                ?: error("本地数据库尚未初始化")
            db.subtitleProjectDao().upsert(entity)
        }
    }

    suspend fun queryOneById(ownerUid: String, id: String): Result<SubtitleProjectEntity?> {
        return runCatching {
            val db = AiToyDatabaseFactory.createDatabaseIfReady()
                ?: return@runCatching null
            db.subtitleProjectDao().queryOneById(ownerUid, id)
        }
    }
}
