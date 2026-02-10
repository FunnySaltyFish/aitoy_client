package com.funny.submaker.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.funny.submaker.database.model.SubtitleProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleProjectDao {
    @Query("SELECT * FROM subtitle_project WHERE ownerUid = :ownerUid ORDER BY updatedAtEpochMillis DESC")
    fun observeAllByOwner(ownerUid: String): Flow<List<SubtitleProjectEntity>>

    @Query(
        "SELECT * FROM subtitle_project " +
            "WHERE ownerUid = :ownerUid AND deleted = 0 " +
            "ORDER BY starred DESC, updatedAtEpochMillis DESC",
    )
    fun observeWorkspaceByOwner(ownerUid: String): Flow<List<SubtitleProjectEntity>>

    @Query("SELECT * FROM subtitle_project WHERE ownerUid = :ownerUid")
    suspend fun queryAllByOwner(ownerUid: String): List<SubtitleProjectEntity>

    @Query("SELECT * FROM subtitle_project WHERE ownerUid = :ownerUid AND updatedAtEpochMillis > :sinceMs")
    suspend fun queryUpdatedSince(ownerUid: String, sinceMs: Long): List<SubtitleProjectEntity>

    @Query("SELECT * FROM subtitle_project WHERE id = :id AND ownerUid = :ownerUid LIMIT 1")
    suspend fun queryOneById(ownerUid: String, id: String): SubtitleProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubtitleProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SubtitleProjectEntity>)

    @Query("DELETE FROM subtitle_project WHERE id = :id AND ownerUid = :ownerUid")
    suspend fun deleteById(ownerUid: String, id: String)

    @Query("DELETE FROM subtitle_project WHERE ownerUid = :ownerUid")
    suspend fun deleteByOwner(ownerUid: String)
}
