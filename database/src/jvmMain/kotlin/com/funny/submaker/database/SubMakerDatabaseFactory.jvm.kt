package com.funny.submaker.database

actual object SubMakerDatabaseFactory {
    private var warned = false

    actual fun createDatabase(): SubMakerDatabase {
        error("SubMakerDatabaseFactory.createDatabase() is not wired for JVM yet.")
    }

    actual fun createDatabaseIfReady(): SubMakerDatabase? {
        if (!warned) {
            warned = true
            println("SubMakerDatabaseFactory.createDatabaseIfReady(): JVM 端 Room 尚未接通，已跳过本地 DB 同步。")
        }
        return null
    }
}
