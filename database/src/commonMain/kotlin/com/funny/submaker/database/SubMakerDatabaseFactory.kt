package com.funny.submaker.database

expect object SubMakerDatabaseFactory {
    fun createDatabase(): SubMakerDatabase
}

