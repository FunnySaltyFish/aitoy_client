package com.funny.submaker.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_project")
data class SubtitleProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

