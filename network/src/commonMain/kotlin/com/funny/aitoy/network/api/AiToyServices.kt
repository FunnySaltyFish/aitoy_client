package com.funny.aitoy.network.api

import com.funny.aitoy.network.ServiceCreator
import com.funny.aitoy.network.api.service.AppService
import com.funny.aitoy.network.api.service.AsrService
import com.funny.aitoy.network.api.service.AuthService
import com.funny.aitoy.network.api.service.DiagnosticsService
import com.funny.aitoy.network.api.service.EntitlementService
import com.funny.aitoy.network.api.service.PayService
import com.funny.aitoy.network.api.service.SyncService
import com.funny.aitoy.network.api.service.UploadService
import com.funny.aitoy.network.api.service.UserService

object AiToyServices {
    val appService: AppService get() = ServiceCreator.create(AppService::class.java)

    val diagnosticsService: DiagnosticsService get() = ServiceCreator.create(DiagnosticsService::class.java)

    val authService: AuthService get() = ServiceCreator.create(AuthService::class.java)

    val userService: UserService get() = ServiceCreator.create(UserService::class.java)

    val entitlementService: EntitlementService get() = ServiceCreator.create(EntitlementService::class.java)

    val payService: PayService get() = ServiceCreator.create(PayService::class.java)

    val uploadService: UploadService get() = ServiceCreator.create(UploadService::class.java)

    val syncService: SyncService get() = ServiceCreator.create(SyncService::class.java)

    fun asrService(baseUrlOverride: String? = null): AsrService {
        return ServiceCreator.create(AsrService::class.java, baseUrlOverride)
    }
}
