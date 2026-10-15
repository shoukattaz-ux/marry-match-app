package com.example.ui.components

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
import com.example.ui.theme.extendedColors
import java.io.File

/**
 * Live camera for the selfie check: face guide, the pose to copy, camera switch and shutter.
 * Only a photo taken here can be submitted; there is no gallery option and no stand-in image.
 * When the camera fails, [onCaptureFailed] is called and nothing is submitted.
 */
@Composable
fun SelfieCameraPreview(
    poseLabel: String,
    poseText: String,
    onPhotoCaptured: (Uri) -> Unit,
    onCaptureFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    var cameraFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }

    // Bind once per lens; switching the lens binds again.
    DisposableEffect(cameraFacing, lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val selector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
            } catch (e: Exception) {
                Log.e("CameraX", "Camera could not be started", e)
                imageCapture = null
                onCaptureFailed()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            imageCapture = null
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Oval face guide
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ovalWidth = size.width * 0.72f
            val ovalHeight = ovalWidth * 1.35f
            drawOval(
                color = Color(0xFFE9B44C),
                topLeft = Offset((size.width - ovalWidth) / 2, (size.height - ovalHeight) / 2 - 40f),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // The pose the server picked for this check
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
                    text = poseLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = poseText,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 24.dp, horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
                Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(enabled = imageCapture != null && !capturing) {
                        val capture = imageCapture ?: return@clickable
                        capturing = true
                        val file = File(context.cacheDir, "selfie_check_${System.currentTimeMillis()}.jpg")
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    capturing = false
                                    onPhotoCaptured(Uri.fromFile(file))
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                                    capturing = false
                                    file.delete()
                                    onCaptureFailed()
                                }
                            }
                        )
                    }
                    .testTag("camera_shutter_btn")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (capturing) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    } else {
                        Box(modifier = Modifier.size(62.dp).background(Color.White, CircleShape))
                    }
                }
            }

            // Keeps the shutter centred.
            Spacer(modifier = Modifier.size(52.dp))
        }
    }
}
