package com.funny.submaker.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.funny.submaker.core.kmp.appCtx
import com.funny.submaker.core.kmp.openUrl
import com.funny.submaker.core.kmp.ToastType
import com.funny.submaker.core.kmp.toast
import com.funny.submaker.core.model.DeviceProfile
import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.network.api.ApiException
import com.funny.submaker.network.api.SubMakerServices
import com.funny.submaker.network.api.apiRequest
import com.funny.submaker.network.api.apiRequestUnit
import com.funny.submaker.network.api.service.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class AuthViewModel : ViewModel() {
    private val vmScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var email by mutableStateOf("")
    var verifyCode by mutableStateOf("")

    var device by mutableStateOf(DeviceProfile(deviceId = SubMakerPrefs.deviceId))
    var products by mutableStateOf<List<Product>>(emptyList())

    var busy by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var infoMessage by mutableStateOf<String?>(null)

    var paying by mutableStateOf(false)
    var payingOrderNo by mutableStateOf<String?>(null)

    val isLoggedIn: Boolean get() = SubMakerPrefs.isLoggedIn
    val trialSecondsRemaining: Int get() = max(device.entitlement.trialSecondsRemaining, 0)

    fun clearMessages() {
        errorMessage = null
        infoMessage = null
    }

    private fun setError(message: String?) {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return
        errorMessage = text
        toast(text, type = ToastType.Error)
    }

    private fun setInfo(message: String?) {
        val text = message?.trim().orEmpty()
        if (text.isBlank()) return
        infoMessage = text
        toast(text, type = ToastType.Info)
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
                setError(it.userMessage())
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
                setError(it.userMessage())
            }
        }
    }

    fun loadProducts() {
        vmScope.launch {
            runCatching {
                apiRequest { SubMakerServices.payService.products() }.products
            }.onSuccess {
                products = it
            }.onFailure {
                setError(it.userMessage())
            }
        }
    }

    fun emailFormatError(input: String = email): String? {
        val normalized = input.trim().lowercase()
        if (normalized.isBlank()) return "请输入邮箱"
        if (!EMAIL_REGEX.matches(normalized)) return "邮箱格式不正确"
        val domain = normalized.substringAfter('@', "")
        if (domain !in SUPPORTED_EMAIL_DOMAINS) {
            return "暂只支持主流邮箱（如 QQ/Gmail/Outlook/163）"
        }
        return null
    }

    fun sendLoginCode(onSuccess: (() -> Unit)? = null) = sendCode(purpose = "login", onSuccess = onSuccess)

    private fun sendCode(
        purpose: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        val emailError = emailFormatError()
        if (emailError != null) {
            setError(emailError)
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequestUnit {
                    SubMakerServices.authService.sendAuthCode(
                        email = email.trim(),
                        purpose = purpose,
                    )
                }
            }.onSuccess {
                setInfo("验证码已发送")
                onSuccess?.invoke()
            }.onFailure {
                setError(it.userMessage())
            }
            busy = false
        }
    }

    fun loginWithCode(onSuccess: (() -> Unit)? = null) {
        val emailError = emailFormatError()
        if (emailError != null || verifyCode.length != 6) {
            setError(emailError ?: "请输入 6 位验证码")
            return
        }
        busy = true
        clearMessages()
        vmScope.launch {
            runCatching {
                apiRequest {
                    SubMakerServices.authService.loginCode(
                        email = email.trim(),
                        code = verifyCode,
                        deviceId = SubMakerPrefs.deviceId,
                    )
                }
            }.onSuccess {
                SubMakerPrefs.authToken = it.token
                SubMakerPrefs.user = it.user
                runCatching {
                    SyncAfterLoginUseCase.executeAfterLogin()
                }.onFailure {
                    setError(it.userMessage())
                }
                refreshMe()
                loadDeviceStatus()
                onSuccess?.invoke()
            }.onFailure {
                setError(it.userMessage())
            }
            busy = false
        }
    }

    fun logout() {
        SubMakerPrefs.logout()
        setInfo("已退出登录")
    }

    fun buy(product: Product) {
        if (!isLoggedIn) {
            setError("请先登录")
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
                setInfo("已创建订单：${order.orderNo}，请在浏览器完成支付后返回")
                appCtx.openUrl(order.payUrl)
                pollOrder(order.orderNo)
            }.onFailure {
                paying = false
                setError(it.userMessage())
            }
        }
    }

    private suspend fun pollOrder(orderNo: String) {
        val deadline = System.currentTimeMillis() + 3 * 60_000
        while (paying && payingOrderNo == orderNo && System.currentTimeMillis() < deadline) {
            delay(1_000)
            runCatching { apiRequest { SubMakerServices.payService.queryOrder(orderNo) } }
                .onSuccess { query ->
                    if (query.status == "paid") {
                        paying = false
                        setInfo("购买成功，权益已发放")
                        refreshMe()
                        return
                    }
                }
        }
        if (paying && payingOrderNo == orderNo) {
            paying = false
            setInfo("已停止轮询订单状态，你可以手动刷新")
        }
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val SUPPORTED_EMAIL_DOMAINS = setOf(
    "qq.com", "foxmail.com", "163.com", "126.com", "yeah.net",
    "gmail.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
    "icloud.com", "yahoo.com", "proton.me", "protonmail.com",
)

private fun Throwable.userMessage(): String =
    when (this) {
        is ApiException -> message
        else -> message ?: "请求失败"
    }
