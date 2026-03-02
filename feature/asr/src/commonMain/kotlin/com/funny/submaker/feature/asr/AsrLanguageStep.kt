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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.funny.submaker.feature.asr.components.AsrFieldLabel
import com.funny.submaker.feature.asr.components.AsrWizardBottomActions

@Composable
fun AsrLanguageStep(
    sourceLanguage: AsrLanguage,
    targetLanguage: AsrLanguage,
    recentPairs: List<AsrRecentLanguagePair>,
    onSourceChange: (AsrLanguage) -> Unit,
    onTargetChange: (AsrLanguage) -> Unit,
    onSwap: () -> Unit,
    onRecentPairClick: (AsrRecentLanguagePair) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()

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
                    text = "设置识别语种与后续翻译语种。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedText,
                )
            }

            /* API 暂不支持翻译模式切换，先注释 UI，后续配合流式翻译能力开放。
            Surface(
                color = tokens.panelContainer,
                shape = RoundedCornerShape(16.dp),
            ) { ... }
            */

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsrFieldLabel(text = "源语言 (SOURCE)")
                LanguageSelectorCard(
                    selected = sourceLanguage,
                    options = AsrLanguage.entries,
                    hint = "用于 ASR 识别；自动检测会传空 language",
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = tokens.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onSelect = onSourceChange,
                )

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(AsrStepShape)
                            .background(tokens.chipContainer)
                            .clickable(onClick = onSwap),
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
                    selected = targetLanguage,
                    options = AsrLanguage.entries,
                    hint = "翻译目标语种（当前仅保存配置，翻译能力后续接入）",
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = tokens.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onSelect = onTargetChange,
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

            if (recentPairs.isEmpty()) {
                Text(
                    text = "暂无最近语种对",
                    color = tokens.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                recentPairs.forEach { pair ->
                    RecentPairCard(
                        pair = pair,
                        onClick = { onRecentPairClick(pair) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
private fun LanguageSelectorCard(
    selected: AsrLanguage,
    options: List<AsrLanguage>,
    hint: String,
    icon: @Composable () -> Unit,
    onSelect: (AsrLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.inputCardContainer)
                .clickable { expanded = true }
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
                    text = selected.displayName,
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.96f),
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RecentPairCard(
    pair: AsrRecentLanguagePair,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = rememberAsrUiTokens()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.inputCardContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pair.source.shortLabel,
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
                pair.target.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText
            )
        }
        Text(
            text = "${pair.source.label} -> ${pair.target.label}",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.bodyText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}
