package com.funny.submaker.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.funny.submaker.core.model.DeviceProfile
import com.funny.submaker.core.kmp.appCtx
import com.funny.submaker.core.kmp.openUrl
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.network.api.ApiException
import com.funny.submaker.network.api.apiRequest
import com.funny.submaker.network.api.apiRequestUnit
import com.funny.submaker.network.api.SubMakerServices
import com.funny.submaker.network.api.service.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthPage {
    Login,
    Register,
    ForgotUsername,
    ForgotPassword,
    Account,
}

class AuthViewModel : ViewModel() {
    private val vmScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var page by mutableStateOf(AuthPage.Login)

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var username by mutableStateOf("")
    var verifyCode by mutableStateOf("")

    var newPassword by mutableStateOf("")
    var repeatPassword by mutableStateOf("")

    var device by mutableStateOf(DeviceProfile(deviceId = SubMakerPrefs.deviceId))
    var products by mutableStateOf<List<Product>>(emptyList())

    var busy by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var infoMessage by mutableStateOf<String?>(null)

    var paying by mutableStateOf(false)
    var payingOrderNo by mutableStateOf<String?>(null)

    val isLoggedIn: Boolean get() = SubMakerPrefs.isLoggedIn

    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    fun bootstrap() {
        loadDeviceStatus()
        if (isLoggedIn) refreshMe()
    }

    fun loadDeviceStatus() {
        vmScope.launch {
            runCatching {
                apiRequest { SubMakerServices.entitlementService.deviceStatus(SubMakerPrefs.deviceId) }.device
            }.onSuccess {
                device = it
            }.onFailure {
                errorMessage = it.userMessage()
            }
        }
    }

    fun refreshMe() {
        vmScope.launch {
            runCatching {
                apiRequest { SubMakerServices.userService.me() }.user
            }.onSuccess {
                SubMakerPrefs.user = it
            }.onFailure {
                errorMessage = it.userMessage()
            }
        }
    }

    fun loadProducts() {
        vmScope.launch {
            runCatching { apiRequest { SubMakerServices.payService.products() }.products }
                .onSuccess { products = it }
                .onFailure { errorMessage = it.userMessage() }
        }
    }

    fun sendRegisterCode() = sendCode(purpose = "register")
    fun sendFindUsernameCode() = sendCode(purpose = "find_username")
    fun sendResetPasswordCode() = sendCode(purpose = "reset_password")

    private fun sendCode(purpose: String) {
        if (email.isBlank()) {
            errorMessage = "请先输入邮箱"
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequestUnit {
                    SubMakerServices.authService.sendAuthCode(
                        email = email,
                        purpose = purpose,
                    )
                }
            }.onSuccess {
                infoMessage = "验证码已发送（MVP：未配置 SMTP 时仅输出到服务端日志）"
            }.onFailure {
                errorMessage = it.userMessage()
            }
            busy = false
        }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "请填写邮箱和密码"
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequest {
                    SubMakerServices.authService.loginPassword(
                        email = email,
                        password = password,
                        deviceId = SubMakerPrefs.deviceId,
                    )
                }
            }.onSuccess {
                SubMakerPrefs.authToken = it.token
                SubMakerPrefs.user = it.user
                page = AuthPage.Account
                refreshMe()
                loadDeviceStatus()
            }.onFailure {
                errorMessage = it.userMessage()
            }
            busy = false
        }
    }

    fun register() {
        if (email.isBlank() || password.isBlank() || verifyCode.isBlank()) {
            errorMessage = "请填写邮箱、密码、验证码"
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequest {
                    SubMakerServices.authService.register(
                        email = email,
                        password = password,
                        code = verifyCode,
                        username = username.takeIf { it.isNotBlank() },
                        deviceId = SubMakerPrefs.deviceId,
                    )
                }
            }.onSuccess {
                SubMakerPrefs.authToken = it.token
                SubMakerPrefs.user = it.user
                page = AuthPage.Account
                refreshMe()
                loadDeviceStatus()
            }.onFailure {
                errorMessage = it.userMessage()
            }
            busy = false
        }
    }

    fun findUsername() {
        if (email.isBlank() || verifyCode.isBlank()) {
            errorMessage = "请填写邮箱和验证码"
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequest {
                    SubMakerServices.authService.findUsername(
                        email = email,
                        code = verifyCode,
                    )
                }.username
            }
                .onSuccess { infoMessage = "账号：$it" }
                .onFailure { errorMessage = it.userMessage() }
            busy = false
        }
    }

    fun resetPassword() {
        if (email.isBlank() || verifyCode.isBlank() || newPassword.isBlank()) {
            errorMessage = "请填写邮箱、验证码、新密码"
            return
        }
        if (newPassword != repeatPassword) {
            errorMessage = "两次密码不一致"
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequestUnit {
                    SubMakerServices.authService.resetPassword(
                        email = email,
                        code = verifyCode,
                        newPassword = newPassword,
                    )
                }
            }
                .onSuccess { infoMessage = "密码已重置，请使用新密码登录"; page = AuthPage.Login }
                .onFailure { errorMessage = it.userMessage() }
            busy = false
        }
    }

    fun logout() {
        SubMakerPrefs.logout()
        page = AuthPage.Login
        infoMessage = "已退出登录"
    }

    fun buy(product: Product) {
        if (!isLoggedIn) {
            errorMessage = "请先登录"
            return
        }
        if (paying) return

        paying = true
        payingOrderNo = null
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequest { SubMakerServices.payService.createOrder(product.id) }
            }.onSuccess { order ->
                payingOrderNo = order.orderNo
                infoMessage = "已创建订单：${order.orderNo}，请在浏览器完成支付后返回（MVP：mock_checkout）"
                appCtx.openUrl(order.payUrl)
                pollOrder(order.orderNo)
            }.onFailure {
                paying = false
                errorMessage = it.userMessage()
            }
        }
    }

    private suspend fun pollOrder(orderNo: String) {
        val deadline = System.currentTimeMillis() + 3 * 60_000
        while (paying && payingOrderNo == orderNo && System.currentTimeMillis() < deadline) {
            delay(1_000)
            runCatching { apiRequest { SubMakerServices.payService.queryOrder(orderNo) } }
                .onSuccess { q ->
                    if (q.status == "paid") {
                        paying = false
                        infoMessage = "购买成功，权益已发放"
                        refreshMe()
                        return
                    }
                }
                .onFailure {
                    // ignore transient errors during polling
                }
        }
        if (paying && payingOrderNo == orderNo) {
            paying = false
            infoMessage = "已停止轮询订单状态，你可以手动刷新"
        }
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}

private fun Throwable.userMessage(): String =
    when (this) {
        is ApiException -> message
        else -> message ?: "请求失败"
    }
