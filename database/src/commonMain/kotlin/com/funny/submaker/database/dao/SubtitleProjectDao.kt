package com.funny.submaker.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.funny.submaker.database.model.SubtitleProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleProjectDao {
    @Query("SELECT * FROM subtitle_project ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<SubtitleProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubtitleProjectEntity)

    @Query("DELETE FROM subtitle_project WHERE id = :id")
    suspend fun deleteById(id: String)
}

