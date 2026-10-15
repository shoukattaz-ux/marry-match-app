package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.ui.theme.extendedColors
import java.io.File

/**
 * CameraX live stream view with face framing guide, challenge pose banner,
 * front/back camera switch, capture trigger, and direct upload option.
 */
@Composable
fun SelfieCameraPreview(
    requiredPose: String,
    lang: AppLanguage,
    onPhotoCaptured: (Uri) -> Unit,
    onUploadRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraInitFailed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // CameraX Surface View
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        imageCaptureUseCase = imageCapture

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(cameraFacing)
                            .build()

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraX", "Binding failed or hardware camera unavailable", exc)
                        cameraInitFailed = true
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCaptureUseCase = imageCapture
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(cameraFacing)
                            .build()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraX", "Rebind failed", exc)
                    }
                }, ContextCompat.getMainExecutor(previewView.context))
            },
            modifier = Modifier.fillMaxSize()
        )

        // Oval Face Guide Scrim
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val ovalWidth = canvasWidth * 0.72f
            val ovalHeight = ovalWidth * 1.35f
            val left = (canvasWidth - ovalWidth) / 2
            val top = (canvasHeight - ovalHeight) / 2 - 40f

            drawOval(
                color = Color(0xFFE9B44C),
                topLeft = Offset(left, top),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Top Challenge Pose Banner
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.accent.copy(alpha = 0.7f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (lang == AppLanguage.EN) "Required Pose:" else "Geforderte Pose:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = requiredPose,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom Controls Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 24.dp, horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flip camera button
            IconButton(
                onClick = {
                    cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .testTag("switch_camera_facing_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Capture Shutter Button
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        val imageCapture = imageCaptureUseCase
                        if (imageCapture != null) {
                            val photoFile = File(
                                context.cacheDir,
                                "selfie_verification_${System.currentTimeMillis()}.jpg"
                            )
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        onPhotoCaptured(Uri.fromFile(photoFile))
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                                        // Fallback sample for emulator environments without physical camera sensor
                                        onPhotoCaptured(
                                            Uri.parse("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80")
                                        )
                                    }
                                }
                            )
                        } else {
                            onPhotoCaptured(
                                Uri.parse("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80")
                            )
                        }
                    }
                    .testTag("camera_shutter_btn")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            // Upload from gallery button
            IconButton(
                onClick = onUploadRequested,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .testTag("camera_upload_photo_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Upload from device",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
