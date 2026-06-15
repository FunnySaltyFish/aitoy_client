package com.funny.aitoy.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.funny.aitoy.database.dao.SubtitleProjectDao
import com.funny.aitoy.database.model.SubtitleProjectEntity

@Database(
    entities = [SubtitleProjectEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AiToyDatabase : RoomDatabase() {
    abstract fun subtitleProjectDao(): SubtitleProjectDao
}
