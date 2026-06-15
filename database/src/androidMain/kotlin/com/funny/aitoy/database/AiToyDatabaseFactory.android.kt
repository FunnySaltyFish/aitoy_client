package com.funny.aitoy.database

import android.content.Context
import androidx.room.Room

actual object AiToyDatabaseFactory {
    private var appContext: Context? = null
    private var cachedDb: AiToyDatabase? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun createDatabase(): AiToyDatabase {
        cachedDb?.let { return it }
        val context = requireNotNull(appContext) { "AiToyDatabaseFactory.init(context) must be called first." }
        return Room.databaseBuilder(context, AiToyDatabase::class.java, "aitoy.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
            .also { cachedDb = it }
    }

    actual fun createDatabaseIfReady(): AiToyDatabase? {
        if (appContext == null) return null
        return createDatabase()
    }
}
