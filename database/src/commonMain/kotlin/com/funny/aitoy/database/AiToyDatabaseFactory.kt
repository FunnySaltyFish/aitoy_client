package com.funny.aitoy.database

expect object AiToyDatabaseFactory {
    fun createDatabase(): AiToyDatabase
    fun createDatabaseIfReady(): AiToyDatabase?
}
