package com.funny.aitoy.database.sync

interface OwnerScopedSyncStore<T> {
    suspend fun queryAllByOwner(ownerUid: String): List<T>

    suspend fun queryUpdatedSince(ownerUid: String, sinceMs: Long): List<T>

    suspend fun upsertAll(items: List<T>)

    suspend fun clearOwnerData(ownerUid: String)
}

