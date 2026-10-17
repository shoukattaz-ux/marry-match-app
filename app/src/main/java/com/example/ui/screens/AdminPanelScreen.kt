package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.AdminReport
import com.example.data.AdminStats
import com.example.data.AdminUser
import com.example.data.AuditEntry
import com.example.data.MarryMatchRepository
import com.example.data.SelfieReviewItem
import com.example.data.SupabaseVerificationService
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.AdminUiState
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.SelfieQueueUiState
import com.example.ui.viewmodel.SelfieQueueViewModel

private enum class AdminTab(val labelKey: String) {
    OVERVIEW("admin_tab_overview"), SELFIES("admin_tab_selfies"), REPORTS("admin_tab_reports"),
    USERS("admin_tab_users"), LOG("admin_tab_log")
}

/** Short date and time from an ISO timestamp, e.g. "2026-10-15 09:00". */
private fun shortTime(iso: String): String = iso.take(16).replace('T', ' ')

/**
 * Moderator panel (blueprint Phase 9). Opened from the profile only for accounts with
 * user_status.is_admin; the server checks that flag again on every call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    val lang by repository.appLanguage.collectAsState()
    val queueVm: SelfieQueueViewModel = viewModel(key = "selfie_queue") { SelfieQueueViewModel(repository.verification) }
    val queueState by queueVm.state.collectAsState()
    val adminVm: AdminViewModel = viewModel(key = "admin_panel") { AdminViewModel(repository.admin) }
    val state by adminVm.state.collectAsState()

    // Load each tab when it is opened.
    LaunchedEffect(tab) {
        when (tab) {
            AdminTab.OVERVIEW -> adminVm.loadStats()
            AdminTab.SELFIES -> queueVm.reload()
            AdminTab.REPORTS -> adminVm.loadReports()
            AdminTab.USERS -> if (state.users.isEmpty()) adminVm.searchUsers()
            AdminTab.LOG -> adminVm.loadAudit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("admin_panel", lang), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Localization.t("back", lang))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AdminTab.entries.forEach { t ->
                    val count = when (t) {
                        AdminTab.SELFIES -> state.stats?.pendingSelfies ?: queueState.items.size
                        AdminTab.REPORTS -> state.stats?.openReports ?: 0
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; adminVm.clearMessages() },
                        icon = {
                            BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                                Icon(
                                    when (t) {
                                        AdminTab.OVERVIEW -> Icons.Default.Dashboard
                                        AdminTab.SELFIES -> Icons.Default.Face
                                        AdminTab.REPORTS -> Icons.Default.Flag
                                        AdminTab.USERS -> Icons.Default.People
                                        AdminTab.LOG -> Icons.Default.History
                                    },
                                    contentDescription = null
                                )
                            }
                        },
                        label = { Text(Localization.t(t.labelKey, lang), maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (tab != AdminTab.SELFIES) {
                state.errorKey?.let { MessageBar(Localization.t(it, lang), error = true, onClose = adminVm::clearMessages) }
                state.doneKey?.let { MessageBar(Localization.t(it, lang), error = false, onClose = adminVm::clearMessages) }
            }
            when (tab) {
                AdminTab.OVERVIEW -> AdminOverview(
                    stats = state.stats,
                    loading = state.loading,
                    lang = lang,
                    onReload = adminVm::loadStats,
                    locationCheckWorking = state.workingOn == "location_check",
                    onSetLocationCheck = adminVm::setLocationCheck
                )
                AdminTab.SELFIES -> SelfieQueueView(
                    state = queueState,
                    lang = lang,
                    onReload = queueVm::reload,
                    onOpen = queueVm::open,
                    onClose = queueVm::close,
                    onDecide = queueVm::decide
                )
                AdminTab.REPORTS -> AdminReports(state, lang, adminVm)
                AdminTab.USERS -> AdminUsers(state, lang, adminVm)
                AdminTab.LOG -> AdminLog(state.audit, state.loading, lang, onReload = adminVm::loadAudit)
            }
        }
    }
}

@Composable
private fun MessageBar(text: String, error: Boolean, onClose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClose)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun AdminOverview(
    stats: AdminStats?,
    loading: Boolean,
    lang: AppLanguage,
    onReload: () -> Unit,
    locationCheckWorking: Boolean,
    onSetLocationCheck: (Boolean) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(Localization.t("admin_overview_title", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onReload, enabled = !loading) { Text(Localization.t("admin_reload", lang)) }
            }
        }
        if (stats == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { if (loading) CircularProgressIndicator() } }
        } else {
            item {
                LocationCheckCard(
                    enabled = stats.locationCheck,
                    working = locationCheckWorking,
                    lang = lang,
                    onChange = onSetLocationCheck
                )
            }
            val rows = listOf(
                listOf("admin_stat_users" to stats.usersTotal, "admin_stat_new_today" to stats.newToday),
                listOf("admin_stat_active" to stats.active7d, "admin_stat_profiles" to stats.profilesDone),
                listOf("admin_stat_verified" to stats.verified, "admin_stat_banned" to stats.banned),
                listOf("admin_stat_selfies" to stats.pendingSelfies, "admin_stat_reports" to stats.openReports),
                listOf("admin_stat_needs_review" to stats.needsReview, "admin_stat_met_unsafe" to stats.metUnsafe30d),
                listOf("admin_stat_met_couples" to stats.metCouples)
            )
            val alerts = setOf("admin_stat_selfies", "admin_stat_reports", "admin_stat_needs_review", "admin_stat_met_unsafe")
            items(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (key, value) ->
                        AdminStatCard(Localization.t(key, lang), value.toString(), Modifier.weight(1f), isAlert = key in alerts && value > 0)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Localization.t("admin_rules_title", lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(Localization.t("admin_rules_body", lang), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Switch for the Germany location check at sign-up. On by default. Switching it off (for testing
 * or a launch in another country) asks for confirmation; while it is off a warning stays visible.
 */
@Composable
private fun LocationCheckCard(enabled: Boolean, working: Boolean, lang: AppLanguage, onChange: (Boolean) -> Unit) {
    var confirmOff by remember { mutableStateOf(false) }
    val t: (String) -> String = { Localization.t(it, lang) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth().testTag("location_check_card")
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("admin_location_title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    t(if (enabled) "admin_location_on_desc" else "admin_location_off_desc"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on -> if (on) onChange(true) else confirmOff = true },
                enabled = !working,
                modifier = Modifier.testTag("location_check_switch")
            )
        }
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text(t("admin_location_confirm_title")) },
            text = { Text(t("admin_location_confirm_body")) },
            confirmButton = {
                TextButton(onClick = { confirmOff = false; onChange(false) }, modifier = Modifier.testTag("location_check_confirm_off")) {
                    Text(t("admin_location_confirm_off"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(t("cancel")) } }
        )
    }
}

@Composable
fun AdminStatCard(title: String, value: String, modifier: Modifier = Modifier, isAlert: Boolean = false) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
        }
    }
}

@Composable
fun SelfieQueueView(
    state: SelfieQueueUiState,
    lang: AppLanguage,
    onReload: () -> Unit,
    onOpen: (SelfieReviewItem) -> Unit,
    onClose: () -> Unit,
    onDecide: (Boolean, String?) -> Unit
) {
    val open = state.open
    if (open != null) {
        SelfieReviewDetail(item = open, selfieUrl = state.openSelfieUrl, state = state, lang = lang, onClose = onClose, onDecide = onDecide)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Localization.t("admin_selfie_queue", lang) + " (${state.items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onReload, enabled = !state.loading) { Text(Localization.t("admin_reload", lang)) }
        }
        state.errorKey?.let { Text(Localization.t(it, lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Localization.t("admin_queue_empty", lang), color = MaterialTheme.extendedColors.muted)
            }
            // The list shows no selfies; one is only loaded when the moderator opens the request.
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.requestId }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${item.displayName}, ${item.age}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                listOf(item.city, item.submittedAt.take(16).replace('T', ' ')).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.muted
                            )
                            Text(poseText(item.pose, lang), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelfieReviewDetail(
    item: SelfieReviewItem,
    selfieUrl: String?,
    state: SelfieQueueUiState,
    lang: AppLanguage,
    onClose: () -> Unit,
    onDecide: (Boolean, String?) -> Unit
) {
    var rejecting by remember(item.requestId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Localization.t("close", lang)) }
            Text("${item.displayName}, ${item.age}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(
                Localization.t("admin_required_pose", lang) + " " + poseText(item.pose, lang),
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(Localization.t("admin_live_selfie", lang), style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (selfieUrl == null) {
                CircularProgressIndicator()
            } else {
                AsyncImage(model = selfieUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Text(Localization.t("admin_profile_photos", lang), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(item.profilePhotoUrls) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp, 160.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        }
        state.errorKey?.let { Text(Localization.t(it, lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.deciding) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (rejecting) {
            Text(Localization.t("admin_reject_reason", lang), style = MaterialTheme.typography.labelLarge)
            SupabaseVerificationService.REJECT_REASONS.forEach { reason ->
                OutlinedButton(
                    onClick = { onDecide(false, reason) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(Localization.t("selfie_reason_$reason", lang)) }
            }
            TextButton(onClick = { rejecting = false }) { Text(Localization.t("cancel", lang)) }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDecide(true, null) },
                    enabled = selfieUrl != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.verified),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.t("admin_approve", lang))
                }
                OutlinedButton(
                    onClick = { rejecting = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.t("admin_reject", lang))
                }
            }
        }
    }
}


@Composable
private fun AdminReports(state: AdminUiState, lang: AppLanguage, vm: AdminViewModel) {
    // Ban asks for confirmation first: it locks the account and closes all its reports.
    var confirmBan by remember { mutableStateOf<AdminReport?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("open", "actioned", "dismissed").forEach { s ->
                FilterChip(
                    selected = state.reportStatus == s,
                    onClick = { vm.loadReports(s) },
                    label = { Text(Localization.t("admin_reports_$s", lang)) }
                )
            }
        }
        when {
            state.loading && state.reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Localization.t("admin_reports_empty", lang), color = MaterialTheme.extendedColors.muted)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.reports, key = { it.reportId }) { rep ->
                    ReportCard(rep, lang, working = state.workingOn == rep.reportId, actionsEnabled = state.workingOn == null,
                        onDismiss = { vm.dismissReport(rep) }, onWarn = { vm.warnReported(rep) }, onBan = { confirmBan = rep })
                }
            }
        }
    }
    confirmBan?.let { rep ->
        ConfirmBanDialog(name = rep.reportedName, lang = lang, onDismiss = { confirmBan = null }) { note ->
            confirmBan = null
            vm.banReported(rep, note)
        }
    }
}

@Composable
private fun ReportCard(
    rep: AdminReport,
    lang: AppLanguage,
    working: Boolean,
    actionsEnabled: Boolean,
    onDismiss: () -> Unit,
    onWarn: () -> Unit,
    onBan: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(rep.reportedName.ifBlank { "?" }, rep.reportedAge.takeIf { it > 0 }?.toString(), rep.reportedCity.ifBlank { null })
                        .filterNotNull().joinToString(", "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        Localization.t("reason_${rep.reason}", lang),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (rep.reportedBanned) {
                Text(Localization.t("admin_user_banned", lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            if (rep.reportedOpenReports > 1) {
                Text(String.format(Localization.t("admin_open_reports_count", lang), rep.reportedOpenReports), style = MaterialTheme.typography.labelMedium)
            }
            rep.messageBody?.let {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text("“$it”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(8.dp))
                }
            }
            if (!rep.details.isNullOrBlank()) Text(rep.details, style = MaterialTheme.typography.bodyMedium)
            Text(
                String.format(Localization.t("admin_reported_by", lang), rep.reporterName.ifBlank { "?" }, shortTime(rep.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.muted
            )
            if (rep.status == "open") {
                if (working) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, enabled = actionsEnabled) { Text(Localization.t("admin_dismiss", lang)) }
                        FilledTonalButton(onClick = onWarn, enabled = actionsEnabled) { Text(Localization.t("admin_warn", lang)) }
                        if (!rep.reportedBanned) {
                            Button(
                                onClick = onBan,
                                enabled = actionsEnabled,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text(Localization.t("admin_ban", lang)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmBanDialog(name: String, lang: AppLanguage, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(String.format(Localization.t("admin_ban_title", lang), name.ifBlank { "?" })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Localization.t("admin_ban_body", lang), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(Localization.t("admin_note", lang)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(Localization.t("admin_ban", lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Localization.t("cancel", lang)) } }
    )
}

@Composable
private fun AdminUsers(state: AdminUiState, lang: AppLanguage, vm: AdminViewModel) {
    var confirmBan by remember { mutableStateOf<AdminUser?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.userQuery,
            onValueChange = vm::setUserQuery,
            label = { Text(Localization.t("admin_search_hint", lang)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = vm::searchUsers) { Icon(Icons.Default.Search, contentDescription = Localization.t("admin_search", lang)) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchUsers() }),
            modifier = Modifier.fillMaxWidth()
        )
        when {
            state.loading && state.users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Localization.t("admin_users_empty", lang), color = MaterialTheme.extendedColors.muted)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.users, key = { it.userId }) { user ->
                    UserCard(user, lang, working = state.workingOn == user.userId, actionsEnabled = state.workingOn == null,
                        onBan = { confirmBan = user }, onUnban = { vm.setBanned(user, false, null) })
                }
            }
        }
    }
    confirmBan?.let { user ->
        ConfirmBanDialog(name = user.displayName, lang = lang, onDismiss = { confirmBan = null }) { note ->
            confirmBan = null
            vm.setBanned(user, true, note)
        }
    }
}

@Composable
private fun UserCard(user: AdminUser, lang: AppLanguage, working: Boolean, actionsEnabled: Boolean, onBan: () -> Unit, onUnban: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                listOf(user.displayName.ifBlank { Localization.t("admin_no_profile", lang) }, user.age.takeIf { it > 0 }?.toString(), user.city.ifBlank { null })
                    .filterNotNull().joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            Text(
                String.format(Localization.t("admin_user_line", lang), shortTime(user.createdAt), user.respectScore, user.openReports),
                style = MaterialTheme.typography.bodySmall
            )
            val badges = buildList {
                if (user.isAdmin) add("admin_badge_admin")
                if (user.selfieVerified) add("admin_badge_verified")
                if (user.needsReview) add("admin_badge_review")
                if (user.isBanned) add("admin_user_banned")
            }
            if (badges.isNotEmpty()) {
                Text(badges.joinToString(" • ") { Localization.t(it, lang) }, style = MaterialTheme.typography.labelMedium,
                    color = if (user.isBanned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            if (!user.isAdmin) {
                if (working) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (user.isBanned) {
                    OutlinedButton(onClick = onUnban, enabled = actionsEnabled) { Text(Localization.t("admin_unban", lang)) }
                } else {
                    Button(
                        onClick = onBan,
                        enabled = actionsEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(Localization.t("admin_ban", lang)) }
                }
            }
        }
    }
}

@Composable
private fun AdminLog(entries: List<AuditEntry>, loading: Boolean, lang: AppLanguage, onReload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Localization.t("admin_log_title", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onReload, enabled = !loading) { Text(Localization.t("admin_reload", lang)) }
        }
        when {
            loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Localization.t("admin_log_empty", lang), color = MaterialTheme.extendedColors.muted)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            Localization.t("audit_${e.action}", lang) +
                                (e.targetName.ifBlank { e.targetUser?.take(8) ?: "" }.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            listOfNotNull(shortTime(e.createdAt), e.actorName.ifBlank { null }, e.note?.let { Localization.t("selfie_reason_$it", lang).takeIf { t -> t != "selfie_reason_$it" } ?: it })
                                .joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
