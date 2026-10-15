package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.LocationGateOutcome
import com.example.core.LocationGateService
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.MarrySecondaryButton
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationGateScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val isPostLogin by repository.isPostLoginLocationGate.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionDeniedNotice by remember { mutableStateOf(false) }

    fun runLocationVerification() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionDeniedNotice = true
            return
        }

        isChecking = true
        errorMessage = null
        permissionDeniedNotice = false

        coroutineScope.launch {
            val token = if (isPostLogin) repository.getCurrentAccessToken() else null
            val outcome = LocationGateService.verifyLocationInGermany(context, token)
            isChecking = false

            when (outcome) {
                is LocationGateOutcome.Allowed -> {
                    if (!isPostLogin && outcome.signupPass == null) {
                        // Sign-up is impossible without a pass, so treat this like a server error.
                        errorMessage = Localization.t("location_network_error", lang)
                    } else {
                        repository.onLocationGatePassed(outcome.signupPass)
                    }
                }
                is LocationGateOutcome.NotAllowed -> {
                    repository.setAuthState(AuthState.LOCATION_REJECTED)
                }
                is LocationGateOutcome.PermissionDenied -> {
                    permissionDeniedNotice = true
                }
                is LocationGateOutcome.GpsUnavailable -> {
                    errorMessage = Localization.t("location_gps_error", lang)
                }
                is LocationGateOutcome.NetworkError -> {
                    errorMessage = Localization.t("location_network_error", lang)
                }
                is LocationGateOutcome.MockRejected -> {
                    errorMessage = Localization.t("location_mock_error", lang)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionDeniedNotice = false
            runLocationVerification()
        } else {
            permissionDeniedNotice = true
            isChecking = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("location_gate_title", lang), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isPostLogin) {
                                repository.signOut()
                            } else {
                                repository.setAuthState(AuthState.WELCOME)
                            }
                        },
                        modifier = Modifier.testTag("location_gate_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { repository.toggleLanguage() }) {
                        Text(if (lang == AppLanguage.EN) "DE" else "EN", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Text(
                    text = Localization.t("location_gate_title", lang),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = Localization.t("location_gate_desc", lang),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // GDPR Privacy Notice
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = Localization.t("location_gdpr_notice", lang),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Permission Denied State
                if (permissionDeniedNotice) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = Localization.t("location_permission_required", lang),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Error State (Network, GPS, Server)
                if (errorMessage != null && !permissionDeniedNotice) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Loading Spinner
                if (isChecking) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Localization.t("location_verifying", lang),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (permissionDeniedNotice) {
                    MarryPrimaryButton(
                        text = Localization.t("grant_location_permission", lang),
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        testTag = "location_gate_grant_permission_btn"
                    )
                    // After a permanent denial Android no longer shows the dialog; only Settings can grant it.
                    MarrySecondaryButton(
                        text = Localization.t("open_settings", lang),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        testTag = "location_gate_open_settings_btn"
                    )
                } else if (errorMessage != null) {
                    MarryPrimaryButton(
                        text = Localization.t("try_again", lang),
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                runLocationVerification()
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        testTag = "location_gate_retry_btn"
                    )
                } else {
                    MarryPrimaryButton(
                        text = Localization.t("location_gate_button", lang),
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                runLocationVerification()
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        enabled = !isChecking,
                        testTag = "location_gate_verify_btn"
                    )
                }
            }
        }
    }
}

@Composable
fun LocationRejectedScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val isPostLogin by repository.isPostLoginLocationGate.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Required text: "MarryMatch is not available in your area yet."
        Text(
            text = Localization.t("location_not_available_title", lang),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = Localization.t("location_not_available_desc", lang),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extendedColors.muted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Required button: "with a Try again button"
        MarryPrimaryButton(
            text = Localization.t("try_again", lang),
            onClick = { repository.setAuthState(AuthState.LOCATION_GATE) },
            testTag = "location_rejected_try_again_btn"
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isPostLogin) {
            MarrySecondaryButton(
                text = Localization.t("logout", lang),
                onClick = { repository.signOut() }
            )
        } else {
            MarrySecondaryButton(
                text = Localization.t("log_in", lang),
                onClick = { repository.setAuthState(AuthState.LOGIN) }
            )
        }
    }
}
