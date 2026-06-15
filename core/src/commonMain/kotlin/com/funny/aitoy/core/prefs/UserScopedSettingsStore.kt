package com.funny.aitoy.core.prefs

import com.funny.aitoy.core.utils.fromJson
import com.funny.aitoy.core.utils.toJson

object UserScopedSettingsStore {
    private const val EMPTY_KEYS_JSON = "[]"

    fun put(ownerId: String, key: String, value: String, updatedAtMs: Long) {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return
        val ownerDataSaver = getUserDataSaverByOwner(normalizedOwner)
        ownerDataSaver.saveData(normalizedKey, value)
        ownerDataSaver.saveData(updatedAtKey(normalizedKey), updatedAtMs)
        markKeyUpdated(normalizedOwner, normalizedKey)
    }

    fun get(ownerId: String, key: String, defaultValue: String = ""): String {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return defaultValue
        return getUserDataSaverByOwner(normalizedOwner).readData(normalizedKey, defaultValue)
    }

    fun getUpdatedAt(ownerId: String, key: String): Long {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return 0L
        return getUserDataSaverByOwner(normalizedOwner).readData(updatedAtKey(normalizedKey), 0L)
    }

    fun queryAll(ownerId: String): List<UserSettingItem> {
        val normalizedOwner = ownerId.trim()
        if (normalizedOwner.isBlank()) return emptyList()
        val ownerDataSaver = getUserDataSaverByOwner(normalizedOwner)
        return readKeys(normalizedOwner).mapNotNull { key ->
            if (!ownerDataSaver.contains(key)) return@mapNotNull null
            UserSettingItem(
                key = key,
                value = ownerDataSaver.readData(key, ""),
                updatedAtMs = ownerDataSaver.readData(updatedAtKey(key), 0L),
            )
        }
    }

    fun queryUpdatedSince(ownerId: String, sinceMs: Long): List<UserSettingItem> {
        return queryAll(ownerId).filter { it.updatedAtMs > sinceMs }
    }

    fun mergeOwnerData(fromOwnerId: String, toOwnerId: String) {
        if (fromOwnerId == toOwnerId) return
        val fromOwner = fromOwnerId.trim()
        val toOwner = toOwnerId.trim()
        if (fromOwner.isBlank() || toOwner.isBlank()) return
        val fromDataSaver = getUserDataSaverByOwner(fromOwner)
        val toDataSaver = getUserDataSaverByOwner(toOwner)
        readKeys(fromOwner).forEach { key ->
            if (!fromDataSaver.contains(key)) return@forEach
            val sourceUpdatedAt = fromDataSaver.readData(updatedAtKey(key), 0L)
            val targetUpdatedAt = toDataSaver.readData(updatedAtKey(key), 0L)
            if (sourceUpdatedAt < targetUpdatedAt) return@forEach
            val value = fromDataSaver.readData(key, "")
            toDataSaver.saveData(key, value)
            toDataSaver.saveData(updatedAtKey(key), sourceUpdatedAt)
            markKeyUpdated(toOwner, key)
        }
    }

    fun clearOwnerData(ownerId: String) {
        val normalizedOwner = ownerId.trim()
        if (normalizedOwner.isBlank()) return
        val ownerDataSaver = getUserDataSaverByOwner(normalizedOwner)
        val keys = readKeys(normalizedOwner)
        keys.forEach { key ->
            ownerDataSaver.remove(key)
            ownerDataSaver.remove(updatedAtKey(key))
        }
        DataSaverUtils.remove(keysKey(normalizedOwner))
    }

    fun markKeyUpdated(ownerId: String, key: String) {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return
        val keys = readKeys(normalizedOwner).toMutableSet()
        if (keys.add(normalizedKey)) {
            DataSaverUtils.saveData(keysKey(normalizedOwner), keys.toList().toJson())
        }
    }

    private fun readKeys(ownerId: String): List<String> {
        val raw = DataSaverUtils.readData(keysKey(ownerId), EMPTY_KEYS_JSON)
        return runCatching { raw.fromJson<List<String>>() }
            .getOrElse { emptyList() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun keysKey(ownerId: String): String = "SYNC_SETTINGS_KEYS_$ownerId"

    private fun updatedAtKey(key: String): String = "_UPDATED_AT_$key"
}

data class UserSettingItem(
    val key: String,
    val value: String,
    val updatedAtMs: Long,
)