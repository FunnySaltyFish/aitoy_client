package com.funny.aitoy.database

actual object AiToyDatabaseFactory {
    private var warned = false

    actual fun createDatabase(): AiToyDatabase {
        error("AiToyDatabaseFactory.createDatabase() is not wired for JVM yet.")
    }

    actual fun createDatabaseIfReady(): AiToyDatabase? {
        if (!warned) {
            warned = true
            println("AiToyDatabaseFactory.createDatabaseIfReady(): JVM 端 Room 尚未接通，已跳过本地 DB 同步。")
        }
        return null
    }
}
