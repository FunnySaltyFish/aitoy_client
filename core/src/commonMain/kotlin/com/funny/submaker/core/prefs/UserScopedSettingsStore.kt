package com.funny.submaker.core.prefs

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Clock

object UserScopedSettingsStore {
    private const val KEY_SEPARATOR = ":"

    fun put(ownerId: String, key: String, value: String, updatedAtMs: Long) {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return
        val keys = readKeys(normalizedOwner).toMutableSet()
        keys.add(normalizedKey)
        DataSaverUtils.saveData(keysKey(normalizedOwner), keys.joinToString(KEY_SEPARATOR))
        DataSaverUtils.saveData(valueKey(normalizedOwner, normalizedKey), value)
        DataSaverUtils.saveData(updatedAtKey(normalizedOwner, normalizedKey), updatedAtMs)
    }

    fun get(ownerId: String, key: String, defaultValue: String = ""): String {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return defaultValue
        return DataSaverUtils.readData(valueKey(normalizedOwner, normalizedKey), defaultValue)
    }

    fun getUpdatedAt(ownerId: String, key: String): Long {
        val normalizedOwner = ownerId.trim()
        val normalizedKey = key.trim()
        if (normalizedOwner.isBlank() || normalizedKey.isBlank()) return 0L
        return DataSaverUtils.readData(updatedAtKey(normalizedOwner, normalizedKey), 0L)
    }

    fun queryAll(ownerId: String): List<UserSettingItem> {
        val normalizedOwner = ownerId.trim()
        if (normalizedOwner.isBlank()) return emptyList()
        return readKeys(normalizedOwner).map { key ->
            UserSettingItem(
                key = key,
                value = get(normalizedOwner, key),
                updatedAtMs = getUpdatedAt(normalizedOwner, key),
            )
        }
    }

    fun queryUpdatedSince(ownerId: String, sinceMs: Long): List<UserSettingItem> {
        return queryAll(ownerId).filter { it.updatedAtMs > sinceMs }
    }

    fun mergeOwnerData(fromOwnerId: String, toOwnerId: String) {
        if (fromOwnerId == toOwnerId) return
        val source = queryAll(fromOwnerId)
        source.forEach { item ->
            val oldUpdatedAt = getUpdatedAt(toOwnerId, item.key)
            if (item.updatedAtMs >= oldUpdatedAt) {
                put(toOwnerId, item.key, item.value, item.updatedAtMs)
            }
        }
    }

    fun clearOwnerData(ownerId: String) {
        val normalizedOwner = ownerId.trim()
        if (normalizedOwner.isBlank()) return
        val keys = readKeys(normalizedOwner)
        keys.forEach { key ->
            DataSaverUtils.remove(valueKey(normalizedOwner, key))
            DataSaverUtils.remove(updatedAtKey(normalizedOwner, key))
        }
        DataSaverUtils.remove(keysKey(normalizedOwner))
    }

    private fun readKeys(ownerId: String): List<String> {
        val raw = DataSaverUtils.readData(keysKey(ownerId), "")
        if (raw.isBlank()) return emptyList()
        return raw.split(KEY_SEPARATOR).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun keysKey(ownerId: String): String = "SYNC_SETTINGS_KEYS_$ownerId"

    private fun valueKey(ownerId: String, key: String): String = "SYNC_SETTINGS_VALUE_${ownerId}_$key"

    private fun updatedAtKey(ownerId: String, key: String): String = "SYNC_SETTINGS_UPDATED_AT_${ownerId}_$key"
}

data class UserSettingItem(
    val key: String,
    val value: String,
    val updatedAtMs: Long,
)

class UserScopedSettingDelegate(
    private val ownerProvider: () -> String,
    private val key: String,
    private val defaultValue: String = "",
    private val timeProvider: () -> Long,
) : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return UserScopedSettingsStore.get(ownerProvider(), key, defaultValue)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        UserScopedSettingsStore.put(ownerProvider(), key, value, timeProvider())
    }
}

fun userScopedStringSetting(
    ownerProvider: () -> String,
    key: String,
    defaultValue: String = "",
    timeProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): ReadWriteProperty<Any?, String> {
    return UserScopedSettingDelegate(
        ownerProvider = ownerProvider,
        key = key,
        defaultValue = defaultValue,
        timeProvider = timeProvider,
    )
}
