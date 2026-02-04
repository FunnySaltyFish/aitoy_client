package com.funny.submaker.database

actual object SubMakerDatabaseFactory {
    actual fun createDatabase(): SubMakerDatabase {
        error("SubMakerDatabaseFactory.createDatabase() is not wired for JVM yet.")
    }
}

