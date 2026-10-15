package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.SelfieCameraPreview
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.delay

enum class SelfieCaptureState {
    PERMISSION_AND_POSE,
    CAMERA_STREAM,
    REVIEW_CAPTURED_PHOTO,
    VERIFYING,
    VERIFIED_SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfieVerificationScreen(
    repository: MarryMatchRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val context = LocalContext.current

    val randomPoses = remember {
        listOf(
            "Touch your nose with one finger",
            "Show a peace sign next to your cheek",
            "Thumbs up and tilt your head to the left"
        )
    }
    val requiredPose = remember { randomPoses.random() }

    var captureState by remember { mutableStateOf(SelfieCaptureState.PERMISSION_AND_POSE) }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Android Photo Picker launcher (zero-permission media picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            capturedPhotoUri = uri
            captureState = SelfieCaptureState.REVIEW_CAPTURED_PHOTO
        }
    }

    // Camera runtime permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            captureState = SelfieCaptureState.CAMERA_STREAM
        } else {
            errorMessage = if (lang == AppLanguage.EN)
                "Camera permission is required to take a live selfie. You can also upload a photo below."
            else
                "Kamerazugriff erforderlich für Live-Selfie. Alternativ können Sie ein Foto hochladen."
        }
    }

    fun startCamera() {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            captureState = SelfieCaptureState.CAMERA_STREAM
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.verified
                        )
                        Text(
                            text = Localization.t("selfie_verification", lang),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_selfie_screen_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (captureState == SelfieCaptureState.CAMERA_STREAM) {
                        Color.Black.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    titleContentColor = if (captureState == SelfieCaptureState.CAMERA_STREAM) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    navigationIconContentColor = if (captureState == SelfieCaptureState.CAMERA_STREAM) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    if (captureState == SelfieCaptureState.CAMERA_STREAM) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                )
        ) {
            when (captureState) {
                SelfieCaptureState.PERMISSION_AND_POSE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Badge Icon
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.extendedColors.verified.copy(alpha = 0.15f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.extendedColors.verified,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Get Verified on MarryMatch"
                            else
                                "Werden Sie verifiziert auf MarryMatch",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Our community prioritizes authentic, marriage-minded individuals. Fulfill the verified requirement with a quick selfie holding the required pose."
                            else
                                "Unsere Gemeinschaft setzt auf echte, heiratsorientierte Mitglieder. Erfüllen Sie die Verifizierungsanforderung mit einem schnellen Selfie.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted,
                            textAlign = TextAlign.Center
                        )

                        // Required Challenge Pose Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = Localization.t("selfie_pose_instruction", lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "\"$requiredPose\"",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // GDPR Biometric Privacy Guarantee Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (lang == AppLanguage.EN)
                                            "GDPR Biometric Privacy Guarantee"
                                        else
                                            "DSGVO-Datenschutzgarantie",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = Localization.t("selfie_consent", lang),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Primary Action: Open CameraX
                        MarryPrimaryButton(
                            text = if (lang == AppLanguage.EN) "Open Camera & Take Pose" else "Kamera öffnen & Pose aufnehmen",
                            onClick = { startCamera() },
                            testTag = "open_camerax_btn"
                        )

                        // Upload Selfie via Photo Picker
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("upload_verification_selfie_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (lang == AppLanguage.EN) "Upload Verification Selfie" else "Verifizierungs-Selfie hochladen",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Fallback snapshot trigger for headless/emulator environments
                        TextButton(
                            onClick = {
                                capturedPhotoUri = Uri.parse(
                                    "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80"
                                )
                                captureState = SelfieCaptureState.REVIEW_CAPTURED_PHOTO
                            },
                            modifier = Modifier.testTag("simulate_selfie_btn")
                        ) {
                            Text(
                                text = if (lang == AppLanguage.EN) "Simulate Pose Selfie (Test Mode)" else "Test-Selfie simulieren",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.muted
                            )
                        }
                    }
                }

                SelfieCaptureState.CAMERA_STREAM -> {
                    SelfieCameraPreview(
                        requiredPose = requiredPose,
                        lang = lang,
                        onPhotoCaptured = { uri ->
                            capturedPhotoUri = uri
                            captureState = SelfieCaptureState.REVIEW_CAPTURED_PHOTO
                        },
                        onUploadRequested = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                SelfieCaptureState.REVIEW_CAPTURED_PHOTO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Review Verification Selfie"
                            else
                                "Verifizierungs-Selfie überprüfen",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (lang == AppLanguage.EN)
                                    "Required Pose: \"$requiredPose\""
                                else
                                    "Geforderte Pose: \"$requiredPose\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        // Image Preview Frame
                        Box(
                            modifier = Modifier
                                .size(260.dp, 330.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    3.dp,
                                    MaterialTheme.extendedColors.verified,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            AsyncImage(
                                model = capturedPhotoUri,
                                contentDescription = "Captured selfie",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Ensure your face and pose are clearly visible. Your biometric selfie is strictly used for verification and will not be displayed publicly."
                            else
                                "Stellen Sie sicher, dass Gesicht und Pose gut erkennbar sind. Ihr Selfie wird ausschließlich zur Prüfung verwendet und nicht öffentlich angezeigt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    capturedPhotoUri = null
                                    captureState = SelfieCaptureState.PERMISSION_AND_POSE
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("retake_selfie_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (lang == AppLanguage.EN) "Retake" else "Wiederholen")
                            }

                            Button(
                                onClick = {
                                    captureState = SelfieCaptureState.VERIFYING
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.extendedColors.verified
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("confirm_submit_selfie_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    Localization.t("submit_verification", lang),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                SelfieCaptureState.VERIFYING -> {
                    // Biometric verification processing simulation
                    var progressStep by remember { mutableIntStateOf(0) }

                    LaunchedEffect(Unit) {
                        delay(600)
                        progressStep = 1
                        delay(600)
                        progressStep = 2
                        delay(600)
                        // Fulfill verification in repository
                        repository.completeSelfieVerification(
                            pose = requiredPose,
                            photoUri = capturedPhotoUri?.toString()
                        )
                        captureState = SelfieCaptureState.VERIFIED_SUCCESS
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.extendedColors.verified,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Verifying Biometric Selfie..."
                            else
                                "Biometrisches Selfie wird geprüft...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VerificationCheckStep(
                                    label = if (lang == AppLanguage.EN) "Face geometry and lighting check" else "Gesichtsgeometrie und Beleuchtungsprüfung",
                                    isDone = progressStep >= 1
                                )
                                VerificationCheckStep(
                                    label = if (lang == AppLanguage.EN) "Pose challenge: \"$requiredPose\"" else "Posen-Abgleich: \"$requiredPose\"",
                                    isDone = progressStep >= 2
                                )
                                VerificationCheckStep(
                                    label = if (lang == AppLanguage.EN) "Liveness & anti-spoofing confirmation" else "Lebendigkeitsprüfung & Manipulationsschutz",
                                    isDone = progressStep >= 2
                                )
                            }
                        }
                    }
                }

                SelfieCaptureState.VERIFIED_SUCCESS -> {
                    // Success View: Fulfills the app's 'verified' requirement!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.extendedColors.verified.copy(alpha = 0.15f),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.extendedColors.verified,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (lang == AppLanguage.EN)
                                "You are Verified!"
                            else
                                "Sie sind verifiziert!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Your selfie verified requirement has been fulfilled! The official Blue Tick is now active on your MarryMatch profile."
                            else
                                "Ihre Verifizierungsanforderung wurde erfolgreich erfüllt! Das offizielle blaue Häkchen ist nun aktiv.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = if (lang == AppLanguage.EN) "Unlocked Benefits:" else "Freigeschaltete Vorteile:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (lang == AppLanguage.EN)
                                        "• Blue Tick badge on Discover, Likes, and Profile\n• 3x boosted visibility in German daily curation\n• Eligible to match with members using 'Verified Only'"
                                    else
                                        "• Blaues Häkchen bei Entdecken, Likes und Profil\n• 3x höhere Sichtbarkeit bei täglichen Vorschlägen\n• Freischaltung für 'Nur Verifizierte'-Filter",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        MarryPrimaryButton(
                            text = if (lang == AppLanguage.EN) "Return to Profile" else "Zurück zum Profil",
                            onClick = onClose,
                            testTag = "verified_done_btn"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationCheckStep(
    label: String,
    isDone: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.verified,
                modifier = Modifier.size(18.dp)
            )
        } else {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isDone) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.muted
        )
    }
}
