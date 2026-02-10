package com.funny.submaker.feature.auth

import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.core.prefs.UserScopedSettingsStore
import com.funny.submaker.database.sync.LocalTableSyncRegistry
import com.funny.submaker.database.sync.LocalTableRow
import com.funny.submaker.database.sync.SyncTableAdapter
import com.funny.submaker.database.sync.SyncTableBootstrap
import com.funny.submaker.network.api.SubMakerServices
import com.funny.submaker.network.api.apiRequest
import com.funny.submaker.network.api.service.SettingSyncItem
import com.funny.submaker.network.api.service.SyncUpsertPayload
import com.funny.submaker.network.api.service.SyncUpsertRequest
import com.funny.submaker.network.api.service.TableRowSyncItem
import com.funny.submaker.network.api.service.TableSyncItem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

object SyncAfterLoginUseCase {
    private const val DEVICE_OWNER_PREFIX = "device:"

    fun currentOwnerId(): String {
        return if (SubMakerPrefs.isLoggedIn) {
            uidOwnerId()
        } else {
            deviceOwnerId()
        }
    }

    private fun lastSyncVersionKey(ownerId: String): String = "SYNC_LAST_VERSION_$ownerId"

    private fun getLastSyncVersion(ownerId: String): Long {
        return com.funny.submaker.core.prefs.DataSaverUtils.readData(lastSyncVersionKey(ownerId), 0L)
    }

    private fun setLastSyncVersion(ownerId: String, version: Long) {
        com.funny.submaker.core.prefs.DataSaverUtils.saveData(lastSyncVersionKey(ownerId), version)
    }

    suspend fun executeAfterLogin(): SyncSummary {
        if (!SubMakerPrefs.isLoggedIn) {
            return SyncSummary(skipped = true, reason = "not_logged_in")
        }
        SyncTableBootstrap.init()

        val uidOwner = uidOwnerId()
        val deviceOwner = deviceOwnerId()
        val adapters = LocalTableSyncRegistry.allAdapters()

        mergeDeviceDataIntoUid(deviceOwner = deviceOwner, uidOwner = uidOwner, adapters = adapters)

        val clientSyncVersion = getLastSyncVersion(uidOwner)
        val settingItems = buildSettingPushItems(uidOwner = uidOwner, sinceMs = clientSyncVersion)
        val tableItems = buildTablePushItems(uidOwner = uidOwner, sinceMs = clientSyncVersion, adapters = adapters)

        val payload = apiRequest {
            SubMakerServices.syncService.upsert(
                SyncUpsertRequest(
                    settings = settingItems,
                    tables = tableItems,
                    clientSyncVersionMs = clientSyncVersion,
                    source = "kmp-client",
                ),
            )
        }

        applyPulledData(uidOwner = uidOwner, payload = payload)

        setLastSyncVersion(uidOwner, payload.serverSyncVersionMs)
        return SyncSummary(
            skipped = false,
            settingsPushed = settingItems.size,
            tablesPushed = tableItems.sumOf { it.rows.size },
            settingsPulled = payload.settings.size,
            tablesPulled = payload.tables.sumOf { it.rows.size },
            serverSyncVersionMs = payload.serverSyncVersionMs,
        )
    }

    private fun uidOwnerId(): String = "uid:${SubMakerPrefs.user.uid}"

    private fun deviceOwnerId(): String = "$DEVICE_OWNER_PREFIX${SubMakerPrefs.deviceId}"

    private suspend fun mergeDeviceDataIntoUid(
        deviceOwner: String,
        uidOwner: String,
        adapters: List<SyncTableAdapter>,
    ) {
        UserScopedSettingsStore.mergeOwnerData(fromOwnerId = deviceOwner, toOwnerId = uidOwner)
        adapters.forEach { adapter ->
            adapter.mergeOwnerData(fromOwnerUid = deviceOwner, toOwnerUid = uidOwner)
        }
        clearDeviceOwnerData(deviceOwner, adapters)
    }

    private fun buildSettingPushItems(uidOwner: String, sinceMs: Long): List<SettingSyncItem> {
        return UserScopedSettingsStore.queryUpdatedSince(uidOwner, sinceMs).map {
            SettingSyncItem(
                key = it.key,
                value = it.value,
                updatedAtMs = it.updatedAtMs,
            )
        }
    }

    private suspend fun buildTablePushItems(
        uidOwner: String,
        sinceMs: Long,
        adapters: List<SyncTableAdapter>,
    ): List<TableSyncItem> {
        return adapters.map { adapter ->
            TableSyncItem(
                table = adapter.tableName,
                rows = adapter.queryUpdatedRows(ownerUid = uidOwner, sinceMs = sinceMs).map { it.toSyncItem() },
            )
        }
    }

    private suspend fun applyPulledData(uidOwner: String, payload: SyncUpsertPayload) {
        payload.tables.forEach { tableSync ->
            val adapter = LocalTableSyncRegistry.getAdapter(tableSync.table) ?: return@forEach
            adapter.applyRows(ownerUid = uidOwner, rows = tableSync.rows.map { it.toLocalRow() })
        }
        payload.settings.forEach { item ->
            UserScopedSettingsStore.put(
                ownerId = uidOwner,
                key = item.key,
                value = item.value,
                updatedAtMs = item.updatedAtMs,
            )
        }
    }

    private suspend fun clearDeviceOwnerData(deviceOwner: String, adapters: List<SyncTableAdapter>) {
        // 设备侧数据一旦并入账号作用域，立即清理源数据，避免后续切换账号时被重复并入。
        UserScopedSettingsStore.clearOwnerData(deviceOwner)
        adapters.forEach { adapter ->
            adapter.clearOwnerData(deviceOwner)
        }
    }
}

private fun LocalTableRow.toSyncItem(): TableRowSyncItem {
    return TableRowSyncItem(
        id = id,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        deleted = deleted,
        data = buildJsonObject {
            data.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        },
    )
}

private fun TableRowSyncItem.toLocalRow(): LocalTableRow {
    return LocalTableRow(
        id = id,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        deleted = deleted,
        data = data.mapValues { it.value.jsonPrimitive.content },
    )
}

data class SyncSummary(
    val skipped: Boolean,
    val reason: String = "",
    val settingsPushed: Int = 0,
    val tablesPushed: Int = 0,
    val settingsPulled: Int = 0,
    val tablesPulled: Int = 0,
    val serverSyncVersionMs: Long = 0,
)
