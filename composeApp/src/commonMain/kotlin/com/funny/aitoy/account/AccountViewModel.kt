package com.funny.aitoy.account

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.funny.aitoy.BridgeViewModel
import com.funny.aitoy.core.model.UserProfile
import com.funny.aitoy.network.api.service.Product

internal val LocalAccountActions = compositionLocalOf<AccountActions> {
    error("LocalAccountActions 未提供")
}

internal interface AccountActions {
    val user: UserProfile
    val loading: Boolean
    val message: String
    val products: List<Product>
    val pendingOrderNo: String

    fun showMessage(message: String)
    fun refreshAccount()
    fun saveProfile(displayName: String)
    fun uploadAvatar(fileName: String, bytes: ByteArray)
    fun openMembershipAgreement()
    fun startMembershipPurchase(productId: String, payType: String, months: Int, quantity: Int)
    fun refreshPendingOrder()
    fun redeemCode(code: String, onSuccess: () -> Unit)
}

internal class BridgeAccountActions(
    private val bridgeVm: BridgeViewModel,
) : AccountActions {
    override val user: UserProfile
        get() = bridgeVm.accountUser
    override val loading: Boolean
        get() = bridgeVm.accountLoading
    override val message: String
        get() = bridgeVm.accountMessage
    override val products: List<Product>
        get() = bridgeVm.memberProducts
    override val pendingOrderNo: String
        get() = bridgeVm.pendingOrderNo

    override fun showMessage(message: String) = bridgeVm.showAccountMessage(message)
    override fun refreshAccount() = bridgeVm.refreshAccount()
    override fun saveProfile(displayName: String) = bridgeVm.saveProfile(displayName)
    override fun uploadAvatar(fileName: String, bytes: ByteArray) = bridgeVm.uploadAvatar(fileName, bytes)
    override fun openMembershipAgreement() = bridgeVm.openMembershipAgreement()
    override fun startMembershipPurchase(productId: String, payType: String, months: Int, quantity: Int) {
        bridgeVm.startMembershipPurchase(
            productId = productId,
            payType = payType,
            months = months,
            quantity = quantity,
        )
    }
    override fun refreshPendingOrder() = bridgeVm.refreshPendingOrder()
    override fun redeemCode(code: String, onSuccess: () -> Unit) = bridgeVm.redeemCode(code, onSuccess)
}

internal class AccountHomeViewModel(
    internal val actions: AccountActions,
) : ViewModel() {
    val user: UserProfile
        get() = actions.user
    val loading: Boolean
        get() = actions.loading
    val message: String
        get() = actions.message
    val products: List<Product>
        get() = actions.products.ifEmpty { defaultProducts() }

    fun showMessage(message: String) = actions.showMessage(message)
    fun refreshAccount() = actions.refreshAccount()
    fun saveProfile(displayName: String) = actions.saveProfile(displayName)
    fun uploadAvatar(fileName: String, bytes: ByteArray) = actions.uploadAvatar(fileName, bytes)
}

internal class AccountUsageViewModel(
    private val actions: AccountActions,
) : ViewModel() {
    val user: UserProfile
        get() = actions.user
}

internal class AccountBillingViewModel(
    private val actions: AccountActions,
) : ViewModel() {
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
        get() = actions.user
    val loading: Boolean
        get() = actions.loading
    val pendingOrderNo: String
        get() = actions.pendingOrderNo

    fun syncProducts(monthlyProducts: List<Product>, addonProducts: List<Product>) {
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

    fun selectedProduct(monthlyProducts: List<Product>, addonProducts: List<Product>): Product? =
        when (purchaseMode) {
            PurchaseMode.Monthly -> monthlyProducts.firstOrNull { it.id == selectedMonthlyId }
            PurchaseMode.Addon -> addonProducts.firstOrNull { it.id == selectedAddonId }
        } ?: monthlyProducts.firstOrNull() ?: addonProducts.firstOrNull()

    fun syncQuantity(quantityCap: Int) {
        selectedQuantity = selectedQuantity.coerceIn(1, quantityCap.coerceAtLeast(1))
    }

    fun selectMode(mode: PurchaseMode) {
        purchaseMode = mode
    }

    fun selectMonthly(productId: String) {
        purchaseMode = PurchaseMode.Monthly
        selectedMonthlyId = productId
    }

    fun selectAddon(productId: String) {
        purchaseMode = PurchaseMode.Addon
        selectedAddonId = productId
    }

    fun setMonths(months: Int) {
        selectedMonths = months.coerceIn(1, 12)
    }

    fun setQuantity(quantity: Int, cap: Int) {
        selectedQuantity = quantity.coerceIn(1, cap.coerceAtLeast(1))
    }

    fun setPayType(payType: String) {
        selectedPayType = payType
    }

    fun updateAgreementChecked(checked: Boolean) {
        agreementChecked = checked
    }

    fun openMembershipAgreement() = actions.openMembershipAgreement()

    fun startPurchase(product: Product, quantityCap: Int) {
        actions.startMembershipPurchase(
            productId = product.id,
            payType = selectedPayType,
            months = selectedMonths,
            quantity = selectedQuantity.coerceIn(1, quantityCap.coerceAtLeast(1)),
        )
    }

    fun refreshPendingOrder() = actions.refreshPendingOrder()
}

internal class AccountRedeemViewModel(
    private val actions: AccountActions,
) : ViewModel() {
    var codeDraft by mutableStateOf("")
        private set

    val loading: Boolean
        get() = actions.loading
    val message: String
        get() = actions.message

    fun setInitialCode(code: String) {
        if (codeDraft.isBlank()) updateCode(code)
    }

    fun updateCode(value: String) {
        codeDraft = value.uppercase().filter { it.isLetterOrDigit() }.take(32)
    }

    fun redeem() {
        actions.redeemCode(codeDraft) {
            codeDraft = ""
        }
    }
}
