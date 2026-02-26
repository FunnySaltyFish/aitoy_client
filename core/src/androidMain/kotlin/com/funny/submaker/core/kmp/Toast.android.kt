package com.funny.submaker.core.kmp

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.funny.submaker.core.utils.nowMs
import com.funny.submaker.core.utils.runOnUI
import kotlin.math.roundToInt

internal actual fun showPlatformToast(
    context: KMPContext,
    msg: String,
    type: ToastType,
) {
    AndroidToastPresenter.show(context, msg, type)
}

private object AndroidToastPresenter {
    private var lastMessage: String = ""
    private var lastShowAtMs: Long = 0L
    private var currentToast: Toast? = null

    fun show(
        context: KMPContext,
        message: String,
        type: ToastType,
    ) {
        runOnUI {
            showInternal(context, message, type)
        }
    }

    @SuppressLint("ShowToast")
    private fun showInternal(
        context: KMPContext,
        message: String,
        type: ToastType,
    ) {
        val now = nowMs()
        // 避免短时间重复提示刷屏
        if (message == lastMessage && now - lastShowAtMs < 900L) return
        lastMessage = message
        lastShowAtMs = now

        currentToast?.cancel()

        val toast =
            Toast(context).apply {
                duration = if (type == ToastType.Error) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                view = buildToastView(context, message, type)
                setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, context.dp(56))
            }
        currentToast = toast
        toast.show()
    }
}

@Suppress("DEPRECATION")
private fun buildToastView(
    context: KMPContext,
    message: String,
    type: ToastType,
): View {
    val palette = type.palette()
    val container =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dpF(14)
                    setColor(palette.containerColor)
                    setStroke(context.dp(1), palette.borderColor)
                }
            elevation = context.dpF(6)
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        }

    val indicator =
        View(context).apply {
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dpF(3)
                    setColor(palette.accentColor)
                }
            layoutParams =
                LinearLayout.LayoutParams(
                    context.dp(4),
                    context.dp(28),
                ).apply {
                    marginEnd = context.dp(10)
                }
        }

    val textView =
        TextView(context).apply {
            text = message
            setTextColor(palette.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.NORMAL)
            maxLines = 3
            includeFontPadding = false
            setLineSpacing(0f, 1.08f)
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
        }

    container.addView(indicator)
    container.addView(textView)
    return container
}

private data class AndroidToastPalette(
    val containerColor: Int,
    val borderColor: Int,
    val textColor: Int,
    val accentColor: Int,
)

@SuppressLint("UseKtx")
private fun ToastType.palette(): AndroidToastPalette =
    when (this) {
        ToastType.Default ->
            AndroidToastPalette(
                containerColor = Color.parseColor("#E61A2430"),
                borderColor = Color.parseColor("#334A5C70"),
                textColor = Color.parseColor("#EAF1FA"),
                accentColor = Color.parseColor("#7DD3FC"),
            )

        ToastType.Success ->
            AndroidToastPalette(
                containerColor = Color.parseColor("#E6152E2A"),
                borderColor = Color.parseColor("#3354C88E"),
                textColor = Color.parseColor("#E7FFF2"),
                accentColor = Color.parseColor("#54C88E"),
            )

        ToastType.Warning ->
            AndroidToastPalette(
                containerColor = Color.parseColor("#E63A2A14"),
                borderColor = Color.parseColor("#33F2C35D"),
                textColor = Color.parseColor("#FFF0CC"),
                accentColor = Color.parseColor("#F2C35D"),
            )

        ToastType.Error ->
            AndroidToastPalette(
                containerColor = Color.parseColor("#E6341A1D"),
                borderColor = Color.parseColor("#33FF7A7A"),
                textColor = Color.parseColor("#FFE9E9"),
                accentColor = Color.parseColor("#FF7A7A"),
            )

        ToastType.Info ->
            AndroidToastPalette(
                containerColor = Color.parseColor("#E6142940"),
                borderColor = Color.parseColor("#335A9DFF"),
                textColor = Color.parseColor("#E7F1FF"),
                accentColor = Color.parseColor("#5A9DFF"),
            )
    }

private fun KMPContext.dp(value: Int): Int = dpF(value).roundToInt()

private fun KMPContext.dpF(value: Int): Float = value * resources.displayMetrics.density
