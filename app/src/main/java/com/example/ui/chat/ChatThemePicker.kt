package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.ui.theme.extendedColors

/**
 * "Chat theme" sheet: every theme as a small chat preview. Preview shows the chosen theme on
 * this chat only for me; Apply sets it for both people.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThemePicker(
    current: String,
    otherName: String,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onPreview: (String) -> Unit,
    onApply: (String) -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var selected by remember(current) { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(t("chat_theme_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                String.format(t("chat_theme_shared"), otherName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 520.dp)
            ) {
                items(ChatThemes.ALL, key = { it.key }) { theme ->
                    ThemeTile(theme, label = t("chat_theme_${theme.key}"), selected = selected == theme.key) { selected = theme.key }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onPreview(selected) },
                    enabled = selected != current,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp).testTag("chat_theme_preview_btn")
                ) { Text(t("chat_theme_preview"), fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onApply(selected) },
                    enabled = selected != current,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp).testTag("chat_theme_apply_btn")
                ) { Text(t("chat_theme_apply"), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** A tall tile: the theme's background (still) with two sample bubbles, and its name below. */
@Composable
private fun ThemeTile(theme: ChatTheme, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("chat_theme_${theme.key}")) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onClick)
        ) {
            ChatThemeBackground(theme, animate = false)
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                SampleBubble(widthFraction = 0.7f, mine = false, theme = theme)
                SampleBubble(widthFraction = 0.55f, mine = true, theme = theme)
                SampleBubble(widthFraction = 0.6f, mine = false, theme = theme)
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SampleBubble(widthFraction: Float, mine: Boolean, theme: ChatTheme) {
    val color = when {
        mine && !theme.isClassic -> theme.myBubble
        mine -> MaterialTheme.colorScheme.primary
        !theme.isClassic -> theme.theirBubble
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(14.dp)
                .background(color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
        )
    }
}
