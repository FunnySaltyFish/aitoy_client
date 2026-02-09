package com.funny.submaker.feature.auth.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    errorTrigger: Int,
    modifier: Modifier = Modifier,
    length: Int = 6,
    onErrorFeedback: (() -> Unit)? = null,
) {
    val shake = remember { Animatable(0f) }
    val tokens = rememberAuthUiTokens()

    LaunchedEffect(errorTrigger) {
        if (errorTrigger <= 0) return@LaunchedEffect
        onErrorFeedback?.invoke()
        launch {
            shake.animateTo(10f, tween(45))
            shake.animateTo(-8f, tween(45))
            shake.animateTo(6f, tween(40))
            shake.animateTo(-4f, tween(40))
            shake.animateTo(0f, tween(40))
        }
    }

    BasicTextField(
        value = TextFieldValue(value),
        onValueChange = {
            val next = it.text.filter(Char::isDigit).take(length)
            if (next != value) onValueChange(next)
        },
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.value.roundToInt(), 0) },
        textStyle = TextStyle(fontSize = 0.sp),
        cursorBrush = SolidColor(tokens.otpActiveBorder),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
        ),
        visualTransformation = VisualTransformation.None,
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(length) { index ->
                    val ch = value.getOrNull(index)?.toString().orEmpty()
                    val isFocusSlot = value.length.coerceAtMost(length - 1) == index
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(56.dp)
                            .background(
                                color = if (ch.isNotEmpty()) tokens.otpFilledBackground else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    ch.isNotEmpty() -> tokens.otpActiveBorder
                                    isFocusSlot -> tokens.otpActiveBorder
                                    else -> tokens.otpInactiveBorder
                                },
                                shape = RoundedCornerShape(12.dp),
                            )
                            .border(
                                width = if (isFocusSlot) 1.5.dp else 0.dp,
                                color = if (isFocusSlot) tokens.otpActiveBorder.copy(alpha = 0.38f) else tokens.otpInactiveBorder,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ch,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            color = if (ch.isEmpty()) {
                                tokens.subtitleColor
                            } else {
                                tokens.titleColor
                            },
                        )
                    }
                }
            }
        },
    )
}
