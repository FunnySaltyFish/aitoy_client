package com.funny.submaker.database

import android.content.Context
import androidx.room.Room

actual object SubMakerDatabaseFactory {
    private var appContext: Context? = null
    private var cachedDb: SubMakerDatabase? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun createDatabase(): SubMakerDatabase {
        cachedDb?.let { return it }
        val context = requireNotNull(appContext) { "SubMakerDatabaseFactory.init(context) must be called first." }
        return Room.databaseBuilder(context, SubMakerDatabase::class.java, "submaker.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
            .also { cachedDb = it }
    }

    actual fun createDatabaseIfReady(): SubMakerDatabase? {
        if (appContext == null) return null
        return createDatabase()
    }
}
