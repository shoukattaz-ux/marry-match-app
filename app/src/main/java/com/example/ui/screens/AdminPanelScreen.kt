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
import com.example.model.VerificationRequest
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Selfie Queue, 2: Reports, 3: Settings
    val selfieQueue by repository.verificationQueue.collectAsState()
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
                0 -> AdminDashboardView(selfieCount = selfieQueue.size, reportCount = reports.size, activeUsers = profiles.size + 1)
                1 -> SelfieQueueView(queue = selfieQueue, onReview = { id, approve, reason -> repository.reviewSelfie(id, approve, reason) })
                2 -> ReportsListView(reports = reports)
                3 -> AdminSwitchesView()
            }
        }
    }
}

@Composable
fun AdminDashboardView(selfieCount: Int, reportCount: Int, activeUsers: Int) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

@Composable
fun SelfieQueueView(
    queue: List<VerificationRequest>,
    onReview: (String, Boolean, String?) -> Unit
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending selfie verifications in queue! All caught up.", color = MaterialTheme.extendedColors.muted)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Pending Selfie Submissions (${queue.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(queue) { req ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${req.userName}, ${req.userAge}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${req.userCity} • Submitted ${req.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text("Required Pose: ${req.pose}", modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Side by Side comparison (Profile Photo vs Live Pose Selfie)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Profile Photo", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))) {
                                    AsyncImage(model = req.profilePhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Live Pose Check", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))) {
                                    AsyncImage(model = req.selfiePhotoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onReview(req.id, true, null) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.verified),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve")
                            }

                            OutlinedButton(
                                onClick = { onReview(req.id, false, "Pose does not match") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject")
                            }
                        }
                    }
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
