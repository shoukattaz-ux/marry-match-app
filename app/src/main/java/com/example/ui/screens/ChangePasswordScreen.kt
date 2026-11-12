package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.unit.dp
import com.example.core.Localization
import com.example.core.PasswordRules
import com.example.data.MarryMatchRepository
import kotlinx.coroutines.launch

/**
 * Settings → Password: current password, new password twice, Save. "Forgot password?" sends the
 * reset e-mail to the account's address instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(repository: MarryMatchRepository, onBack: () -> Unit) {
    val lang by repository.appLanguage.collectAsState()
    val user by repository.currentUser.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    val problem = PasswordRules.checkChange(current, new, confirm)

    fun save() {
        if (problem != null) { errorKey = problem; return }
        saving = true
        errorKey = null
        scope.launch {
            val result = repository.changePassword(current, new)
            saving = false
            result.onSuccess {
                done = true
                current = ""; new = ""; confirm = ""
            }.onFailure { e ->
                errorKey = when ((e as? MarryMatchRepository.PasswordChangeException)?.code) {
                    "wrong_current" -> "pw_err_wrong_current"
                    "same_password" -> "pw_err_same"
                    "weak_password" -> "pw_err_weak"
                    else -> "pw_err_failed"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("pw_title"), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) } },
                actions = {
                    TextButton(onClick = ::save, enabled = !saving && !done && current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty(), modifier = Modifier.testTag("pw_save")) {
                        Text(t("pw_save"), fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).testTag("change_password_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (done) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().testTag("pw_done")) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(t("pw_done_title"), fontWeight = FontWeight.Bold)
                            Text(t("pw_done_body"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(t("pw_back_to_settings")) }
                return@Column
            }

            PasswordField(t("pw_current"), t("pw_current_hint"), current, { current = it; errorKey = null }, "pw_current")
            TextButton(
                onClick = {
                    scope.launch {
                        val sent = repository.resetPasswordForEmail(user.email).isSuccess
                        snackbar.showSnackbar(if (sent) String.format(t("pw_reset_sent"), user.email) else t("pw_err_failed"))
                    }
                },
                enabled = user.email.isNotBlank(),
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("pw_forgot")
            ) { Text(t("pw_forgot")) }

            PasswordField(t("pw_new"), t("pw_new_hint"), new, { new = it; errorKey = null }, "pw_new")
            // The rules, ticked off while typing.
            Rule(new.trim().length >= PasswordRules.MIN_LENGTH, t("pw_rule_length"))
            Rule(new.any { it.isDigit() }, t("pw_rule_digit"))
            PasswordField(t("pw_confirm"), t("pw_confirm_hint"), confirm, { confirm = it; errorKey = null }, "pw_confirm")
            if (confirm.isNotEmpty() && new.trim() != confirm.trim()) {
                Text(t("pw_err_mismatch"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            errorKey?.let { Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("pw_error")) }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = ::save,
                enabled = !saving && current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(t("pw_save_button"), fontWeight = FontWeight.Bold) }
            Text(t("pw_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordField(label: String, hint: String, value: String, onChange: (String) -> Unit, tag: String) {
    var visible by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 72) onChange(it) },
        placeholder = { Text(hint) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun Rule(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
