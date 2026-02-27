package com.funny.submaker.feature.asr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowRightAlt
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.asr.components.AsrFieldLabel
import com.funny.submaker.feature.asr.components.AsrWizardBottomActions

private enum class LanguageMode(val label: String) {
    Mono("单语"),
    Bilingual("双语"),
}

private data class LanguageOption(
    val name: String,
    val hint: String,
)

private data class RecentLanguagePair(
    val shortFrom: String,
    val shortTo: String,
    val label: String,
    val dotColor: Color,
)

@Composable
fun AsrLanguageStep(
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    var mode by rememberSaveable { mutableStateOf(LanguageMode.Bilingual) }
    val source = remember { LanguageOption("英语 (English)", "自动检测中...") }
    val target = remember { LanguageOption("简体中文 (Chinese)", "默认") }
    val recentPairs = remember {
        listOf(
            RecentLanguagePair("En", "Zh", "英语 -> 中文", Color(0xFF4AD8A6)),
            RecentLanguagePair("Ja", "Zh", "日语 -> 中文", Color(0xFF60A5FA)),
            RecentLanguagePair("Ko", "Zh", "韩语 -> 中文", Color(0xFFB58BFF)),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "语言配置",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.titleText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "设置字幕翻译的源语言和目标语言。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedText,
                )
            }

            Surface(
                color = tokens.panelContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "工作模式",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.bodyText
                        )
                        ModeSegment(
                            mode = mode,
                            onModeChange = { mode = it },
                        )
                    }
                    Text(
                        text = "生成包含源语言和目标语言的双语字幕。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.mutedText,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsrFieldLabel(text = "源语言 (SOURCE)")
                LanguageSelectorCard(
                    title = source.name,
                    hint = source.hint,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = tokens.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(AsrStepShape)
                            .background(tokens.chipContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = null,
                            tint = tokens.mutedText,
                        )
                    }
                }

                AsrFieldLabel(text = "目标语言 (TARGET)")
                LanguageSelectorCard(
                    title = target.name,
                    hint = target.hint,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = tokens.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f).height(1.dp).background(tokens.topBarBorder))
                Text(
                    "最近使用",
                    color = tokens.mutedText,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.weight(1f).height(1.dp).background(tokens.topBarBorder))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RecentPairCard(
                    pair = recentPairs[0],
                    modifier = Modifier.weight(1f),
                )
                RecentPairCard(
                    pair = recentPairs[1],
                    modifier = Modifier.weight(1f),
                )
            }
            RecentPairCard(pair = recentPairs[2], modifier = Modifier.fillMaxWidth(0.48f))
        }

        AsrWizardBottomActions(
            primaryLabel = "下一步",
            primaryIcon = Icons.AutoMirrored.Rounded.ArrowForward,
            onPrimaryClick = onNext,
            secondaryLabel = "上一步",
            secondaryIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            onSecondaryClick = onBack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ModeSegment(
    mode: LanguageMode,
    onModeChange: (LanguageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tokens.chipContainer)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LanguageMode.entries.forEach { item ->
            val selected = mode == item
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) tokens.modeSelectedContainer else Color.Transparent)
                    .clickable { onModeChange(item) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) tokens.modeSelectedText else tokens.mutedText,
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectorCard(
    title: String,
    hint: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.inputCardContainer)
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(AsrStepShape)
                .background(tokens.inputLeadingContainer),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.bodyText,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = null,
            tint = tokens.mutedText,
        )
    }
}

@Composable
private fun RecentPairCard(
    pair: RecentLanguagePair,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.inputCardContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(AsrStepShape)
                    .background(pair.dotColor),
            )
            Text(
                pair.shortFrom,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowRightAlt,
                contentDescription = null,
                tint = tokens.mutedText,
                modifier = Modifier.size(14.dp),
            )
            Text(
                pair.shortTo,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText
            )
        }
        Text(
            text = pair.label,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.bodyText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}
