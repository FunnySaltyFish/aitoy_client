package com.funny.submaker.database.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "subtitle_project",
    primaryKeys = ["ownerUid", "id"],
    indices = [
        Index(value = ["ownerUid", "updatedAtEpochMillis"]),
        Index(value = ["ownerUid", "starred", "updatedAtEpochMillis"]),
    ],
)
data class SubtitleProjectEntity(
    val ownerUid: String,
    val id: String,
    val name: String,
    val sourceFileName: String = "",
    val durationMs: Long = 0L,
    val segmentCount: Int = 0,
    val status: String = "draft",
    val lastExportFormat: String = "",
    val starred: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deleted: Boolean = false,
)
