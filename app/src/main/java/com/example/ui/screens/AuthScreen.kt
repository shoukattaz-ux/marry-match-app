package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Exact Birth Date: Day, Month, Year
    var birthDay by remember { mutableStateOf("") }
    var birthMonth by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }

    var agreedToTerms by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // State for showing "Check your inbox" after real Supabase registration
    var registrationSuccessEmail by remember { mutableStateOf<String?>(null) }

    // Exact date of birth validation and age calculation
    val exactAge: Int? = remember(birthDay, birthMonth, birthYear) {
        val d = birthDay.toIntOrNull()
        val m = birthMonth.toIntOrNull()
        val y = birthYear.toIntOrNull()
        if (d != null && m != null && y != null && y in 1900..2026 && m in 1..12 && d in 1..31) {
            try {
                val birthDate = LocalDate.of(y, m, d)
                val today = LocalDate.now()
                if (birthDate.isAfter(today)) null else Period.between(birthDate, today).years
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("create_account", lang), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = { repository.setAuthState(AuthState.WELCOME) },
                        modifier = Modifier.testTag("signup_back_button")
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
        // If registration succeeded, show "Check your inbox" screen state
        if (registrationSuccessEmail != null) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = Localization.t("check_inbox_title", lang),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = String.format(
                        Localization.t("check_inbox_desc", lang),
                        registrationSuccessEmail
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                MarryPrimaryButton(
                    text = Localization.t("check_inbox_button", lang),
                    onClick = {
                        repository.setAuthState(AuthState.LOGIN)
                    },
                    testTag = "check_inbox_login_btn"
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (lang == AppLanguage.EN)
                        "Begin your journey to a marriage-minded connection in Germany."
                    else
                        "Beginnen Sie Ihren Weg zu einer ernsthaften Ehe-Partnerschaft.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.muted
                )

                // Email input
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text(Localization.t("email", lang)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Password input
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
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
                    supportingText = {
                        Text(
                            if (lang == AppLanguage.EN) "At least 8 characters with at least one number." else "Mindestens 8 Zeichen und mindestens eine Zahl.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Requirement 5: Replace birth-year with FULL BIRTH DATE (Day, Month, Year) & Block anyone under 18 by exact date
                Text(
                    text = Localization.t("birth_date_full", lang),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Day
                    OutlinedTextField(
                        value = birthDay,
                        onValueChange = {
                            if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                birthDay = it
                                errorMessage = null
                            }
                        },
                        label = { Text(Localization.t("birth_day", lang)) },
                        placeholder = { Text("DD") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("signup_birthday_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Month
                    OutlinedTextField(
                        value = birthMonth,
                        onValueChange = {
                            if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                birthMonth = it
                                errorMessage = null
                            }
                        },
                        label = { Text(Localization.t("birth_month", lang)) },
                        placeholder = { Text("MM") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("signup_birthmonth_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Year
                    OutlinedTextField(
                        value = birthYear,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                birthYear = it
                                errorMessage = null
                            }
                        },
                        label = { Text(Localization.t("birth_year_full", lang)) },
                        placeholder = { Text("YYYY") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("signup_birthyear_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Calculated age indicator & strict 18+ enforcement
                if (exactAge != null) {
                    val isUnder18 = exactAge < 18
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUnder18) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isUnder18)
                                if (lang == AppLanguage.EN) "Age: $exactAge (Must be at least 18 to join)" else "Alter: $exactAge (Mindestalter ist 18 Jahre)"
                            else
                                if (lang == AppLanguage.EN) "Age: $exactAge years old" else "Alter: $exactAge Jahre",
                            color = if (isUnder18) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Terms & Privacy Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreedToTerms = !agreedToTerms }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it },
                        modifier = Modifier.testTag("signup_terms_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Localization.t("agree_terms_privacy", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Create Account Button triggering real Supabase sign up
                Button(
                    onClick = {
                        val trimmedEmail = email.trim()
                        val trimmedPassword = password.trim()

                        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
                            errorMessage = if (lang == AppLanguage.EN) "Please enter a valid email address." else "Bitte geben Sie eine gültige E-Mail-Adresse ein."
                            return@Button
                        }
                        if (trimmedPassword.length < 8 || !trimmedPassword.any { it.isDigit() }) {
                            errorMessage = if (lang == AppLanguage.EN) "Password must be at least 8 characters and include a number." else "Das Passwort muss mindestens 8 Zeichen und eine Ziffer enthalten."
                            return@Button
                        }

                        if (exactAge == null) {
                            errorMessage = Localization.t("birth_date_invalid", lang)
                            return@Button
                        }

                        // Block anyone under 18 by exact date
                        if (exactAge < 18) {
                            errorMessage = Localization.t("under_18_blocked", lang)
                            return@Button
                        }

                        if (!agreedToTerms) {
                            errorMessage = if (lang == AppLanguage.EN) "Please agree to the Terms and Privacy Policy." else "Bitte stimmen Sie den AGB und Datenschutzbestimmungen zu."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null

                        val formattedBirthDate = String.format("%04d-%02d-%02d", birthYear.toInt(), birthMonth.toInt(), birthDay.toInt())

                        coroutineScope.launch {
                            val result = repository.signUpWithSupabase(
                                email = trimmedEmail,
                                password = trimmedPassword,
                                birthDate = formattedBirthDate,
                                age = exactAge
                            )
                            isLoading = false
                            result.fold(
                                onSuccess = {
                                    // Shows "Check your inbox" screen state
                                    registrationSuccessEmail = trimmedEmail
                                },
                                onFailure = { error ->
                                    val msg = error.message ?: ""
                                    val lower = msg.lowercase()
                                    errorMessage = when {
                                        lower.contains("already registered") || lower.contains("user already exists") || lower.contains("already exists") -> {
                                            if (lang == AppLanguage.EN) "An account with this email already exists." else "Ein Konto mit dieser E-Mail existiert bereits."
                                        }
                                        lower.contains("password") && (lower.contains("weak") || lower.contains("short") || lower.contains("least")) -> {
                                            if (lang == AppLanguage.EN) "Password is too weak. Please choose a stronger password." else "Das Passwort ist zu schwach. Bitte wählen Sie ein sichereres Passwort."
                                        }
                                        lower.contains("rate limit") -> {
                                            if (lang == AppLanguage.EN) "Too many attempts. Please try again in a few moments." else "Zu viele Versuche. Bitte warten Sie einen Moment."
                                        }
                                        lower.contains("unknownhostexception") || lower.contains("failed to connect") || lower.contains("connectexception") || lower.contains("sockettimeoutexception") -> {
                                            Localization.t("network_error", lang)
                                        }
                                        else -> {
                                            error.localizedMessage?.takeIf { it.isNotBlank() } ?: (if (lang == AppLanguage.EN) "Registration failed. Please try again." else "Registrierung fehlgeschlagen.")
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
                        .testTag("signup_submit_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = Localization.t("create_account", lang),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { repository.setAuthState(AuthState.LOGIN) }) {
                        Text(Localization.t("already_have_account", lang))
                    }
                }
            }
        }
    }
}
