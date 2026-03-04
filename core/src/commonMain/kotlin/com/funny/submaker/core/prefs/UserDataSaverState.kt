package com.funny.submaker.core.prefs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.funny.data_saver.core.DataSaverConverter
import com.funny.data_saver.core.DataSaverConverter.typeConverters
import com.funny.data_saver.core.DataSaverInterface
import com.funny.data_saver.core.DataSaverMutableState
import com.funny.data_saver.core.ITypeConverter
import com.funny.data_saver.core.SavePolicy
import com.funny.submaker.core.utils.nowMs
import kotlinx.coroutines.CoroutineScope

private const val DEVICE_OWNER_PREFIX = "device:"
private const val UID_OWNER_PREFIX = "uid:"
private const val UPDATED_AT_PREFIX = "_UPDATED_AT_"

fun currentUserDataOwnerId(): String {
    return if (SubMakerPrefs.isLoggedIn) {
        "$UID_OWNER_PREFIX${SubMakerPrefs.user.uid}"
    } else {
        "$DEVICE_OWNER_PREFIX${SubMakerPrefs.deviceId}"
    }
}

private object UserDataSaverOwnerSwitcher {
    private var activeOwnerId: String = currentUserDataOwnerId()

    fun ensureOwnerUpToDate(ownerId: String) {
        if (ownerId == activeOwnerId) return
        if (activeOwnerId.startsWith(DEVICE_OWNER_PREFIX) && ownerId.startsWith(UID_OWNER_PREFIX)) {
            UserScopedSettingsStore.mergeOwnerData(fromOwnerId = activeOwnerId, toOwnerId = ownerId)
        }
        activeOwnerId = ownerId
    }
}

private object UserScopedDataSaverRegistry {
    private val stores = mutableMapOf<String, DataSaverInterface>()

    fun get(ownerId: String): DataSaverInterface {
        return stores.getOrPut(ownerId) {
            getUserDataSaverByOwner(ownerId)
        }
    }
}

expect fun getUserDataSaverByOwner(ownerId: String): DataSaverInterface

fun <T> userDataSaverState(
    key: String,
    initialValue: T,
    ownerProvider: () -> String = ::currentUserDataOwnerId,
    savePolicy: SavePolicy = SavePolicy.IMMEDIATELY,
    async: Boolean = true,
    coroutineScope: CoroutineScope? = null,
): MutableState<T> {
    return object : MutableState<T> {
        private var cachedOwnerId = ""
        private var cachedRawState: DataSaverMutableState<String>? = null

        override var value: T
            get() = decodeUserValue(rawState().value, initialValue) ?: initialValue
            set(value) {
                val encodedValue = encodeUserValue(value) ?: return
                val ownerId = ownerProvider()
                val state = rawState()
                state.value = encodedValue

                val updatedAt = nowMs()
                val ownerDataSaver = UserScopedDataSaverRegistry.get(ownerId)
                ownerDataSaver.saveData(updatedAtKey(key), updatedAt)
                UserScopedSettingsStore.markKeyUpdated(ownerId = ownerId, key = key)
            }

        override fun component1(): T {
            return value
        }

        override fun component2(): (T) -> Unit {
            return { value = it }
        }

        private fun rawState(): DataSaverMutableState<String> {
            val ownerId = ownerProvider()
            UserDataSaverOwnerSwitcher.ensureOwnerUpToDate(ownerId)
            if (ownerId == cachedOwnerId && cachedRawState != null) return cachedRawState!!

            val dataSaver = UserScopedDataSaverRegistry.get(ownerId)
            syncFromFallbackStore(ownerId, dataSaver)
            return DataSaverMutableState(
                dataSaverInterface = dataSaver,
                key = key,
                initialValue = readInitialRawValue(dataSaver),
                savePolicy = savePolicy,
                async = async,
                coroutineScope = coroutineScope,
            ).also {
                cachedOwnerId = ownerId
                cachedRawState = it
            }
        }

        private fun syncFromFallbackStore(ownerId: String, dataSaver: DataSaverInterface) {
            if (dataSaver.contains(key)) {
                UserScopedSettingsStore.markKeyUpdated(ownerId = ownerId, key = key)
                return
            }
            if (!ownerId.startsWith(UID_OWNER_PREFIX)) return

            val fallbackOwnerId = "$DEVICE_OWNER_PREFIX${SubMakerPrefs.deviceId}"
            val fallbackDataSaver = UserScopedDataSaverRegistry.get(fallbackOwnerId)
            if (!fallbackDataSaver.contains(key)) return

            val rawValue = fallbackDataSaver.readData(key, "")
            val updatedAt = fallbackDataSaver.readData(updatedAtKey(key), nowMs())
            dataSaver.saveData(key, rawValue)
            dataSaver.saveData(updatedAtKey(key), updatedAt)
            UserScopedSettingsStore.markKeyUpdated(ownerId = ownerId, key = key)
        }

        private fun readInitialRawValue(dataSaver: DataSaverInterface): String {
            if (!dataSaver.contains(key)) {
                return encodeUserValue(initialValue) ?: ""
            }
            return dataSaver.readData(key, "")
        }
    }
}

@Composable
fun <T> rememberUserDataSaverState(
    key: String,
    initialValue: T,
    ownerProvider: () -> String = ::currentUserDataOwnerId,
    savePolicy: SavePolicy = SavePolicy.IMMEDIATELY,
    async: Boolean = true,
    coroutineScope: CoroutineScope? = null,
): MutableState<T> {
    return remember(key, initialValue, savePolicy, async, coroutineScope) {
        userDataSaverState(
            key = key,
            initialValue = initialValue,
            ownerProvider = ownerProvider,
            savePolicy = savePolicy,
            async = async,
            coroutineScope = coroutineScope,
        )
    }
}

fun <T> dataSaverState(
    key: String,
    initialValue: T,
    dataSaver: DataSaverInterface = DataSaverUtils,
    savePolicy: SavePolicy = SavePolicy.IMMEDIATELY,
    async: Boolean = true,
    coroutineScope: CoroutineScope? = null,
): MutableState<T> {
    return DataSaverMutableState(
        dataSaverInterface = dataSaver,
        key = key,
        initialValue = initialValue,
        savePolicy = savePolicy,
        async = async,
        coroutineScope = coroutineScope,
    )
}

@Composable
fun <T> rememberDataSaverState(
    key: String,
    initialValue: T,
    dataSaver: DataSaverInterface = DataSaverUtils,
    savePolicy: SavePolicy = SavePolicy.IMMEDIATELY,
    async: Boolean = true,
    coroutineScope: CoroutineScope? = null,
): MutableState<T> {
    return remember(key, initialValue, savePolicy, async, coroutineScope, dataSaver) {
        dataSaverState(
            key = key,
            initialValue = initialValue,
            dataSaver = dataSaver,
            savePolicy = savePolicy,
            async = async,
            coroutineScope = coroutineScope,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> decodeUserValue(raw: String, defaultValue: T): T? {
    val value: Any = when (defaultValue) {
        is String -> raw
        is Int -> raw.toIntOrNull()
        is Long -> raw.toLongOrNull()
        is Boolean -> raw.toBooleanStrictOrNull()
        is Float -> raw.toFloatOrNull()
        is Double -> raw.toDoubleOrNull()
        else -> {
            val restorer = findRestorer(defaultValue) ?: return null
            runCatching { restorer(raw) }.getOrNull()
        }
    } ?: return defaultValue
    return value as T
}

fun <T> findRestorer(data: T): ((String) -> Any?)? {
    return findTypeConverter(data)?.let {
        it::restore
    }
}

fun <T> findTypeConverter(data: T): ITypeConverter? {
    return typeConverters.findLast { it.accept(data) }
}

private fun <T> encodeUserValue(value: T): String? {
    return when (value) {
        is String -> value
        is Int, is Long, is Boolean, is Float, is Double -> value.toString()
        null -> null
        else -> {
            val saver = DataSaverConverter.findSaver(value) ?: return null
            saver(value)
        }
    }
}

private fun updatedAtKey(key: String): String = "$UPDATED_AT_PREFIX$key"
