package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.AppConfig
import com.example.data.MarryMatchRepository
import com.example.model.ReportItem
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.SelfieReviewItem
import com.example.data.SupabaseVerificationService
import com.example.ui.viewmodel.SelfieQueueUiState
import com.example.ui.viewmodel.SelfieQueueViewModel
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Selfie Queue, 2: Reports, 3: Settings
    val lang by repository.appLanguage.collectAsState()
    val queueVm: SelfieQueueViewModel = viewModel(key = "selfie_queue") { SelfieQueueViewModel(repository.verification) }
    val queueState by queueVm.state.collectAsState()
    val selfieQueue = queueState.items
    val reports by repository.reports.collectAsState()
    val profiles by repository.dailyProfiles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moderator & Admin Panel", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    icon = {
                        BadgedBox(badge = { if (selfieQueue.isNotEmpty()) Badge { Text(selfieQueue.size.toString()) } }) {
                            Icon(Icons.Default.Face, contentDescription = null)
                        }
                    },
                    label = { Text("Selfies") }
                )
                NavigationBarItem(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    icon = {
                        BadgedBox(badge = { if (reports.isNotEmpty()) Badge { Text(reports.size.toString()) } }) {
                            Icon(Icons.Default.Flag, contentDescription = null)
                        }
                    },
                    label = { Text("Reports") }
                )
                NavigationBarItem(
                    selected = selectedSection == 3,
                    onClick = { selectedSection = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Switches") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (selectedSection) {
                0 -> AdminDashboardView(
                    note = { SampleDataNote(lang) },selfieCount = selfieQueue.size, reportCount = reports.size, activeUsers = profiles.size + 1)
                1 -> SelfieQueueView(
                    state = queueState,
                    lang = lang,
                    onReload = queueVm::reload,
                    onOpen = queueVm::open,
                    onClose = queueVm::close,
                    onDecide = queueVm::decide
                )
                2 -> {
                    SampleDataNote(lang)
                    ReportsListView(reports = reports)
                }
                3 -> {
                    SampleDataNote(lang)
                    AdminSwitchesView()
                }
            }
        }
    }
}

@Composable
fun AdminDashboardView(note: @Composable () -> Unit, selfieCount: Int, reportCount: Int, activeUsers: Int) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { note() }
        item {
            Text("Community Health & Status (Germany)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("New Users Today", "+24", Modifier.weight(1f))
                AdminStatCard("Active Users", activeUsers.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("Pending Selfies", selfieCount.toString(), Modifier.weight(1f), isAlert = selfieCount > 0)
                AdminStatCard("Open Reports", reportCount.toString(), Modifier.weight(1f), isAlert = reportCount > 0)
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Trust Guidelines Rulebook", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("• Rule 1: Underage suspicion, threats, or scams = ban immediately.", style = MaterialTheme.typography.bodySmall)
                    Text("• Rule 2: Fake photos = reject and revoke blue badge.", style = MaterialTheme.typography.bodySmall)
                    Text("• Rule 3: Target SLA = review every report within 24 hours.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
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

/** Tabs that still show sample data until the rest of Phase 9 is built. */
@Composable
private fun SampleDataNote(lang: AppLanguage) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Text(
            Localization.t("admin_sample_note", lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(10.dp)
        )
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
fun ReportsListView(reports: List<ReportItem>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Open User & Message Reports (${reports.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(reports) { rep ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reported: ${rep.reportedUserName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = rep.reason.uppercase(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(rep.details, style = MaterialTheme.typography.bodyMedium)
                    Text("Received ${rep.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { /* Dismiss */ }) { Text("Dismiss", fontSize = 12.sp) }
                        Button(
                            onClick = { /* Ban */ },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Ban Account", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSwitchesView() {
    var phoneSwitch by remember { mutableStateOf<Boolean>(AppConfig.phoneVerificationEnabled) }
    var waitlistEnabled by remember { mutableStateOf<Boolean>(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Feature Toggles & City Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phone Verification (SMS Gateway)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Show or hide the phone check until SMS sender is connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                    }
                    Switch(
                        checked = phoneSwitch,
                        onCheckedChange = {
                            phoneSwitch = it
                            AppConfig.phoneVerificationEnabled = it
                        }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("City Waitlist Rule", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Only open cities once 500+ verified singles sign up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                    }
                    Switch(checked = waitlistEnabled, onCheckedChange = { waitlistEnabled = it })
                }
            }
        }
    }
}
