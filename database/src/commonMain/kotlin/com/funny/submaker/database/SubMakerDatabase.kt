package com.funny.submaker.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.funny.submaker.database.dao.SubtitleProjectDao
import com.funny.submaker.database.model.SubtitleProjectEntity

@Database(
    entities = [SubtitleProjectEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class SubMakerDatabase : RoomDatabase() {
    abstract fun subtitleProjectDao(): SubtitleProjectDao
}
