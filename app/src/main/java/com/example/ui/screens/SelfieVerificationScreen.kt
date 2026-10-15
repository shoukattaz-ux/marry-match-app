package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.PhotoProcessor
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.SelfieCameraPreview
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.SelfieStep
import com.example.ui.viewmodel.SelfieUiState
import com.example.ui.viewmodel.SelfieViewModel
import kotlinx.coroutines.launch
import java.io.File

/** Text for a pose key chosen by the server. */
fun poseText(pose: String?, lang: AppLanguage): String =
    if (pose == null) "" else Localization.t("pose_$pose", lang)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfieVerificationScreen(
    repository: MarryMatchRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SelfieViewModel = viewModel(key = "selfie_check") { SelfieViewModel(repository.verification) }
    val state by vm.state.collectAsState()
    var cameraAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var preparing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAllowed = granted
        if (!granted) vm.onCameraFailed()
    }

    // Keep the profile badge and status card up to date after leaving this screen.
    fun close() {
        repository.refreshSelfieStatus()
        onClose()
    }

    val onCamera = state.step == SelfieStep.CAMERA
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.extendedColors.verified)
                        Text(Localization.t("selfie_verification", lang), style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { close() }, modifier = Modifier.testTag("close_selfie_screen_btn")) {
                        Icon(Icons.Default.Close, contentDescription = Localization.t("close", lang))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (onCamera) Color.Black.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface,
                    titleContentColor = if (onCamera) Color.White else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (onCamera) Color.White else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (onCamera) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            when (state.step) {
                SelfieStep.LOADING, SelfieStep.UPLOADING -> CenteredProgress(
                    if (state.step == SelfieStep.UPLOADING) Localization.t("selfie_uploading", lang) else null
                )

                SelfieStep.INTRO -> IntroStep(
                    state = state,
                    lang = lang,
                    onConsent = vm::setConsent,
                    onStart = {
                        if (!cameraAllowed) permissionLauncher.launch(Manifest.permission.CAMERA)
                        vm.start()
                    }
                )

                SelfieStep.CAMERA -> {
                    if (cameraAllowed) {
                        SelfieCameraPreview(
                            poseLabel = Localization.t("selfie_pose_instruction", lang),
                            poseText = poseText(state.pose, lang),
                            onPhotoCaptured = { uri ->
                                preparing = true
                                scope.launch {
                                    try {
                                        vm.onPhotoTaken(PhotoProcessor.prepare(context, uri))
                                    } catch (e: Throwable) {
                                        vm.onCameraFailed()
                                    } finally {
                                        // The raw capture is not kept on the phone.
                                        uri.path?.let { File(it).delete() }
                                        preparing = false
                                    }
                                }
                            },
                            onCaptureFailed = vm::onCameraFailed
                        )
                    } else {
                        CameraPermissionNeeded(lang) { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    }
                    if (preparing) CenteredProgress(null)
                    state.errorKey?.let { key ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .clickable { vm.clearError() }
                        ) {
                            Text(
                                text = Localization.t(key, lang),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                SelfieStep.REVIEW -> ReviewStep(state, lang, onRetake = vm::retake, onSubmit = vm::submit)

                SelfieStep.PENDING -> ResultStep(
                    icon = { Icon(Icons.Default.HourglassTop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) },
                    title = Localization.t("selfie_pending_title", lang),
                    body = Localization.t("selfie_pending_body", lang),
                    button = Localization.t("selfie_back_to_profile", lang),
                    onDone = { close() }
                )

                SelfieStep.VERIFIED -> ResultStep(
                    icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.extendedColors.verified, modifier = Modifier.size(56.dp)) },
                    title = Localization.t("selfie_verified_title", lang),
                    body = Localization.t("selfie_verified_body", lang),
                    button = Localization.t("selfie_back_to_profile", lang),
                    onDone = { close() }
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress(text: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.extendedColors.verified)
        if (text != null) {
            Spacer(Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun IntroStep(
    state: SelfieUiState,
    lang: AppLanguage,
    onConsent: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.extendedColors.verified.copy(alpha = 0.15f), modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Verified, null, tint = MaterialTheme.extendedColors.verified, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            Localization.t("selfie_intro_title", lang),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            Localization.t("selfie_intro_body", lang),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.muted,
            textAlign = TextAlign.Center
        )

        state.lastRejectReason?.let { reason ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = Localization.t("selfie_last_rejected", lang) + " " + Localization.t("selfie_reason_$reason", lang),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // What happens with the selfie (GDPR Art. 9: explicit consent for biometric data)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Text(Localization.t("selfie_privacy_title", lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    Localization.t("selfie_consent", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onConsent(!state.consentChecked) }
                .testTag("selfie_consent_checkbox")
        ) {
            Checkbox(checked = state.consentChecked, onCheckedChange = onConsent)
            Text(Localization.t("selfie_consent_checkbox", lang), style = MaterialTheme.typography.bodySmall)
        }

        state.errorKey?.let {
            Text(
                Localization.t(it, lang),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        if (state.busy) {
            CircularProgressIndicator()
        } else {
            MarryPrimaryButton(
                text = Localization.t("selfie_open_camera", lang),
                onClick = onStart,
                enabled = state.consentChecked,
                testTag = "open_camerax_btn"
            )
        }
        Text(
            Localization.t("selfie_attempts_hint", lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CameraPermissionNeeded(lang: AppLanguage, onAsk: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            Localization.t("selfie_camera_permission", lang),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAsk) { Text(Localization.t("selfie_allow_camera", lang)) }
    }
}

@Composable
private fun ReviewStep(state: SelfieUiState, lang: AppLanguage, onRetake: () -> Unit, onSubmit: () -> Unit) {
    val image = remember(state.photo) {
        state.photo?.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(Localization.t("selfie_review_title", lang), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = Localization.t("selfie_pose_instruction", lang) + "\n" + poseText(state.pose, lang),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(10.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(260.dp, 330.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(3.dp, MaterialTheme.extendedColors.verified, RoundedCornerShape(16.dp))
        ) {
            if (image != null) {
                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            Localization.t("selfie_review_hint", lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.muted,
            textAlign = TextAlign.Center
        )
        state.errorKey?.let {
            Text(Localization.t(it, lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, enabled = !state.busy, modifier = Modifier.weight(1f).height(48.dp).testTag("retake_selfie_btn")) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Localization.t("selfie_retake", lang))
            }
            Button(
                onClick = onSubmit,
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.verified),
                modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_submit_selfie_btn")
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Localization.t("submit_verification", lang), color = Color.White)
            }
        }
    }
}

@Composable
private fun ResultStep(icon: @Composable () -> Unit, title: String, body: String, button: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        MarryPrimaryButton(text = button, onClick = onDone, testTag = "selfie_done_btn")
    }
}
