package com.funny.aitoy.database.sync

object SyncTableBootstrap {
    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            LocalTableSyncRegistry.register(SubtitleProjectTableSyncAdapter)
            initialized = true
        }
    }
}
