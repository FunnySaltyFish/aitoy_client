package com.funny.aitoy.network.api

import com.funny.aitoy.network.ServiceCreator
import com.funny.aitoy.network.api.service.AsrService
import com.funny.aitoy.network.api.service.AuthService
import com.funny.aitoy.network.api.service.EntitlementService
import com.funny.aitoy.network.api.service.PayService
import com.funny.aitoy.network.api.service.SyncService
import com.funny.aitoy.network.api.service.UploadService
import com.funny.aitoy.network.api.service.UserService

object AiToyServices {
    val authService: AuthService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ServiceCreator.create(AuthService::class.java)
    }

    val userService: UserService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ServiceCreator.create(UserService::class.java)
    }

    val entitlementService: EntitlementService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ServiceCreator.create(EntitlementService::class.java)
    }

    val payService: PayService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ServiceCreator.create(PayService::class.java)
    }

    val uploadService: UploadService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ServiceCreator.create(UploadService::class.java)
    }

    val syncService: SyncService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ServiceCreator.create(SyncService::class.java)
    }

    fun asrService(baseUrlOverride: String? = null): AsrService {
        return ServiceCreator.create(AsrService::class.java, baseUrlOverride)
    }
}
