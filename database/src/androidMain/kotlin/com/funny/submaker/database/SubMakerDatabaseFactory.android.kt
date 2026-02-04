package com.funny.submaker.database

import android.content.Context
import androidx.room.Room

actual object SubMakerDatabaseFactory {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun createDatabase(): SubMakerDatabase {
        val context = requireNotNull(appContext) { "SubMakerDatabaseFactory.init(context) must be called first." }
        return Room.databaseBuilder(context, SubMakerDatabase::class.java, "submaker.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}

