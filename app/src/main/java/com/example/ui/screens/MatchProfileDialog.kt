package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.model.UserProfile

/**
 * The full profile of a match, opened by tapping their photo or name in a chat or in the Chats
 * list (migration 048). The same view as Discover, without Like / Pass (you are matched); Report
 * in the menu. Loaded fresh each time, so new photos and answers show at once. Shown over the
 * chat, so closing it returns to the chat as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchProfileDialog(
    repository: MarryMatchRepository,
    person: UserProfile,
    onDismiss: () -> Unit,
    onReport: () -> Unit
) {
    val lang by repository.appLanguage.collectAsState()
    val me by repository.currentUser.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    var profile by remember(person.id) { mutableStateOf<UserProfile?>(null) }
    var failed by remember(person.id) { mutableStateOf(false) }
    var attempt by remember(person.id) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(person.id, attempt) {
        failed = false
        profile = try {
            repository.discovery?.matchProfile(person.id)
        } catch (e: Exception) {
            null
        }
        failed = profile == null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().testTag("match_profile"), color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(
                    title = { Text(person.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("match_profile_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back"))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(t("report")) },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; onReport() },
                                    modifier = Modifier.testTag("match_profile_report")
                                )
                            }
                        }
                    }
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val shown = profile
                    when {
                        shown != null -> ProfileHingeCard(
                            profile = shown,
                            lang = lang,
                            enabled = false,
                            myInterests = me.interests,
                            preview = true,  // no Like / Pass: you are matched
                            onPass = {},
                            onLike = {},
                            onLikeTarget = {},
                            onReport = onReport
                        )
                        failed -> Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(t("match_profile_failed"), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { attempt++ }) { Text(t("retry")) }
                        }
                        else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
