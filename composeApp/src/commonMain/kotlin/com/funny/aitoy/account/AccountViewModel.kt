package com.funny.aitoy.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funny.aitoy.BridgePlatform
import com.funny.aitoy.core.model.UserProfile
import com.funny.aitoy.core.prefs.AiToyPrefs
import com.funny.aitoy.network.OkHttpUtils
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.Product
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal class AccountHomeViewModel : ViewModel() {
    val user: UserProfile
        get() = AiToyPrefs.user
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set

    var products by mutableStateOf<List<Product>>(emptyList())
        private set

    init {
        bootstrapAccount()
    }

    fun showMessage(message: String) {
        this.message = message
    }

    fun bootstrapAccount() {
        if (AiToyPrefs.userToken.isBlank()) {
            refreshProducts()
            return
        }
        loading = true
        viewModelScope.launch {
            runCatching {
                bootstrapAccountFromToken()
            }.onSuccess {
                message = ""
                refreshProducts()
            }.onFailure {
                message = "账号同步失败，请稍后重试。"
            }
            loading = false
        }
    }

    fun refreshAccount() {
        loading = true
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.userService.me() }.user
            }.onSuccess { payloadUser ->
                saveUser(payloadUser)
                message = ""
            }.onFailure {
                if (AiToyPrefs.authToken.isBlank()) {
                    runCatching { bootstrapAccountFromToken() }
                        .onSuccess {
                            message = ""
                        }
                        .onFailure { message = "刷新失败，请稍后再试。" }
                } else {
                    message = "刷新失败，请稍后再试。"
                }
            }
            loading = false
        }
    }

    fun refreshProducts() {
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.payService.products() }.products
            }.onSuccess {
                products = it
            }
        }
    }

    fun saveProfile(displayName: String) {
        val name = displayName.trim()
        if (name.isBlank()) {
            message = "请填写昵称。"
            return
        }
        if (name.length > 12) {
            message = "昵称最多 12 个字。"
            return
        }
        loading = true
        viewModelScope.launch {
            runCatching {
                apiRequest {
                    AiToyServices.userService.updateProfile(displayName = name)
                }.user
            }.onSuccess { payloadUser ->
                saveUser(payloadUser)
                message = "资料已保存。"
            }.onFailure {
                message = "保存失败，请稍后再试。"
            }
            loading = false
        }
    }

    fun uploadAvatar(fileName: String, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            message = "请选择可用的头像图片。"
            return
        }
        loading = true
        viewModelScope.launch {
            runCatching {
                val safeName = fileName.ifBlank { "avatar.jpg" }
                val contentType = when (safeName.substringAfterLast('.', "").lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val part = MultipartBody.Part.createFormData(
                    name = "file",
                    filename = safeName,
                    body = bytes.toRequestBody(contentType.toMediaType()),
                )
                apiRequest { AiToyServices.userService.uploadAvatar(part) }.user
            }.onSuccess { payloadUser ->
                saveUser(payloadUser)
                message = "头像已更新。"
            }.onFailure {
                message = "头像更新失败，请稍后再试。"
            }
            loading = false
        }
    }
}

internal class AccountUsageViewModel : ViewModel() {
    val user: UserProfile
        get() = AiToyPrefs.user
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set

    init {
        refreshAccount()
    }

    fun refreshAccount() {
        loading = true
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.userService.me() }.user
            }.onSuccess { payloadUser ->
                saveUser(payloadUser)
                message = ""
            }.onFailure {
                message = "刷新失败，请稍后再试。"
            }
            loading = false
        }
    }
}

internal class AccountBillingViewModel : ViewModel() {
    var purchaseMode by mutableStateOf(PurchaseMode.Monthly)
        private set
    var selectedMonthlyId by mutableStateOf("")
        private set
    var selectedAddonId by mutableStateOf("")
        private set
    var selectedMonths by mutableIntStateOf(1)
        private set
    var selectedQuantity by mutableIntStateOf(1)
        private set
    var selectedPayType by mutableStateOf("alipay")
        private set
    var agreementChecked by mutableStateOf(false)
        private set
    val user: UserProfile
        get() = AiToyPrefs.user
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var pendingOrderNo by mutableStateOf("")
        private set

    private var monthlyProducts by mutableStateOf<List<Product>>(emptyList())
    private var addonProducts by mutableStateOf<List<Product>>(emptyList())
    var selectedProduct by mutableStateOf<Product?>(null)
        private set
    var quantityCap by mutableIntStateOf(1)
        private set

    fun syncAccountData(
        monthlyProducts: List<Product>,
        addonProducts: List<Product>,
    ) {
        this.monthlyProducts = monthlyProducts
        this.addonProducts = addonProducts
        syncSelectedProducts()
        syncSelectionState()
    }

    fun selectMode(mode: PurchaseMode) {
        purchaseMode = mode
        syncSelectionState()
    }

    fun selectMonthly(productId: String) {
        purchaseMode = PurchaseMode.Monthly
        selectedMonthlyId = productId
        syncSelectionState()
    }

    fun selectAddon(productId: String) {
        purchaseMode = PurchaseMode.Addon
        selectedAddonId = productId
        syncSelectionState()
    }

    fun setMonths(months: Int) {
        selectedMonths = months.coerceIn(1, 12)
    }

    fun setQuantity(quantity: Int) {
        selectedQuantity = quantity.coerceIn(1, quantityCap.coerceAtLeast(1))
    }

    fun setPayType(payType: String) {
        selectedPayType = payType
    }

    fun updateAgreementChecked(checked: Boolean) {
        agreementChecked = checked
    }

    fun openMembershipAgreement() {
        val prefix = AiToyPrefs.apiPrefix.trim().trim('/')
        BridgePlatform.openUrl(OkHttpUtils.externalUrl("$prefix/pay/membership-agreement"))
    }

    fun startPurchase(product: Product) {
        loading = true
        viewModelScope.launch {
            runCatching {
                apiRequest {
                    AiToyServices.payService.createOrder(
                        productId = product.id,
                        payType = selectedPayType,
                        months = selectedMonths.coerceIn(1, 12),
                        quantity = selectedQuantity.coerceIn(1, quantityCap.coerceAtLeast(1)),
                    )
                }
            }.onSuccess { payload ->
                pendingOrderNo = payload.orderNo
                message = "订单已创建，完成支付后回到 App 刷新。"
                if (payload.payUrl.isNotBlank()) {
                    BridgePlatform.openUrl(payload.payUrl)
                }
            }.onFailure {
                message = "暂时无法发起支付，请稍后再试。"
            }
            loading = false
        }
    }

    fun refreshPendingOrder() {
        val orderNo = pendingOrderNo
        if (orderNo.isBlank()) return
        loading = true
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.payService.queryOrder(orderNo) }
            }.onSuccess { payload ->
                payload.user?.let {
                    saveUser(it)
                }
                syncSelectionState()
                message = if (payload.status == "paid") "会员已生效。" else "订单还未完成。"
            }.onFailure {
                message = "订单刷新失败，请稍后再试。"
            }
            loading = false
        }
    }

    private fun syncSelectedProducts() {
        if (selectedMonthlyId.isBlank() || monthlyProducts.none { it.id == selectedMonthlyId }) {
            selectedMonthlyId = monthlyProducts.firstOrNull { it.highlight }?.id
                ?: monthlyProducts.firstOrNull { it.purchasable }?.id
                ?: monthlyProducts.firstOrNull()?.id.orEmpty()
        }
        if (selectedAddonId.isBlank() || addonProducts.none { it.id == selectedAddonId }) {
            selectedAddonId = addonProducts.firstOrNull { it.highlight }?.id
                ?: addonProducts.firstOrNull()?.id.orEmpty()
        }
    }

    fun syncQuantity() {
        selectedQuantity = selectedQuantity.coerceIn(1, quantityCap.coerceAtLeast(1))
    }

    private fun syncSelectionState() {
        selectedProduct = when (purchaseMode) {
            PurchaseMode.Monthly -> monthlyProducts.firstOrNull { it.id == selectedMonthlyId }
            PurchaseMode.Addon -> addonProducts.firstOrNull { it.id == selectedAddonId }
        } ?: monthlyProducts.firstOrNull() ?: addonProducts.firstOrNull()
        quantityCap = selectedProduct?.let { quantityCap(it, user.entitlement) } ?: 1
        syncQuantity()
    }
}

internal class AccountRedeemViewModel : ViewModel() {
    var codeDraft by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set

    fun setInitialCode(code: String) {
        if (codeDraft.isBlank()) updateCode(code)
    }

    fun updateCode(value: String) {
        codeDraft = value.uppercase().filter { it.isLetterOrDigit() }.take(32)
    }

    fun redeem() {
        val code = codeDraft.trim()
        if (code.isBlank()) {
            message = "请输入兑换码。"
            return
        }
        loading = true
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.payService.redeemCode(code) }
            }.onSuccess { payload ->
                payload.user?.let(::saveUser)
                message = payload.message.ifBlank { "兑换成功，额度已到账。" }
                codeDraft = ""
            }.onFailure {
                message = it.message ?: "兑换失败，请检查兑换码。"
            }
            loading = false
        }
    }
}

private suspend fun bootstrapAccountFromToken(): UserProfile? {
    val token = AiToyPrefs.userToken.trim()
    if (token.isBlank()) return null
    val payload = apiRequest {
        AiToyServices.authService.bootstrap(
            userToken = token,
            deviceId = AiToyPrefs.deviceId,
        )
    }
    AiToyPrefs.authToken = payload.token
    saveUser(payload.user)
    return payload.user
}

private fun saveUser(user: UserProfile) {
    AiToyPrefs.user = user
}
