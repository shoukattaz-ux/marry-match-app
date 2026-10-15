package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // No prefilled email or password per user instructions
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("log_in", lang), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = { repository.setAuthState(AuthState.WELCOME) },
                        modifier = Modifier.testTag("login_back_button")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = Localization.t("app_name", lang),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (lang == AppLanguage.EN)
                        "Welcome back to your verified, marriage-focused community in Germany."
                    else
                        "Willkommen zurück in Ihrer verifizierten Gemeinschaft für die Ehe in Deutschland.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.muted
                )
            }

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    authError = null
                },
                label = { Text(Localization.t("email", lang)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_email_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    authError = null
                },
                label = { Text(Localization.t("password", lang)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Forgot password link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.testTag("forgot_password_btn")
                ) {
                    Text(Localization.t("forgot_password", lang), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Error Message Banner (Always displayed on any error, NEVER logs the user in)
            if (authError != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Primary Log In Button with real Supabase authentication
            Button(
                onClick = {
                    val trimmedEmail = email.trim()
                    val trimmedPassword = password.trim()

                    if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
                        authError = if (lang == AppLanguage.EN) "Please enter a valid email." else "Bitte geben Sie eine gültige E-Mail ein."
                        return@Button
                    }
                    if (trimmedPassword.isBlank()) {
                        authError = if (lang == AppLanguage.EN) "Please enter your password." else "Bitte geben Sie Ihr Passwort ein."
                        return@Button
                    }

                    isLoading = true
                    authError = null

                    coroutineScope.launch {
                        val result = repository.signInWithSupabase(trimmedEmail, trimmedPassword)
                        isLoading = false
                        result.fold(
                            onSuccess = {
                                // Successful Supabase authentication transitions state in repository
                            },
                            onFailure = { error ->
                                val msg = error.message ?: ""
                                val lower = msg.lowercase()
                                authError = when {
                                    lower.contains("invalid login") || lower.contains("invalid credentials") || lower.contains("invalid grant") -> {
                                        Localization.t("invalid_credentials", lang)
                                    }
                                    lower.contains("email not confirmed") || lower.contains("unconfirmed") -> {
                                        Localization.t("email_not_confirmed", lang)
                                    }
                                    lower.contains("unknownhostexception") || lower.contains("failed to connect") || lower.contains("connectexception") || lower.contains("sockettimeoutexception") -> {
                                        Localization.t("network_error", lang)
                                    }
                                    else -> {
                                        error.localizedMessage?.takeIf { it.isNotBlank() } ?: Localization.t("invalid_credentials", lang)
                                    }
                                }
                            }
                        )
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_submit_btn")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = Localization.t("log_in", lang),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Need an account button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { repository.setAuthState(AuthState.LOCATION_GATE) },
                    modifier = Modifier.testTag("login_signup_link")
                ) {
                    Text(Localization.t("need_account", lang), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetError by remember { mutableStateOf<String?>(null) }
        var resetSuccessMessage by remember { mutableStateOf(false) }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showForgotPasswordDialog = false
                resetSuccessMessage = false
                resetError = null
            },
            title = { Text(Localization.t("reset_password", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "Enter your registered email address to receive password reset instructions:"
                        else
                            "Geben Sie Ihre registrierte E-Mail-Adresse ein:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = it
                            resetError = null
                        },
                        label = { Text(Localization.t("email", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (resetError != null) {
                        Text(resetError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (resetSuccessMessage) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = Localization.t("reset_link_sent", lang),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = resetEmail.trim()
                        if (trimmed.isBlank() || !trimmed.contains("@")) {
                            resetError = if (lang == AppLanguage.EN) "Please enter a valid email." else "Ungültige E-Mail-Adresse."
                            return@Button
                        }

                        isResetting = true
                        resetError = null
                        coroutineScope.launch {
                            val result = repository.resetPasswordForEmail(trimmed)
                            isResetting = false
                            result.fold(
                                onSuccess = {
                                    resetSuccessMessage = true
                                },
                                onFailure = { e ->
                                    resetError = e.localizedMessage ?: Localization.t("network_error", lang)
                                }
                            )
                        }
                    },
                    enabled = !isResetting
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(Localization.t("reset_password", lang))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgotPasswordDialog = false
                    resetSuccessMessage = false
                    resetError = null
                }) {
                    Text("Close")
                }
            }
        )
    }
}
