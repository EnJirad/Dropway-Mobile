package com.pocketdev.dropway.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pocketdev.dropway.ui.theme.*
import com.pocketdev.dropway.util.ImageEnhancer
import com.pocketdev.dropway.util.LabelDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CameraViewfinder provides:
 * 1. Live camera preview via Camera2 & TextureView
 * 2. Background 250ms frame analysis (looksLikeLabel, looksTooClose, frameDifference)
 * 3. Auto-hold (900ms) and auto-still (500ms) detection for automatic label capture
 * 4. Manual shutter trigger & Flashlight (Torch) toggle
 */
@Composable
fun CameraViewfinder(
    scanning: Boolean,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onCapture: (Bitmap) -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            onStartScanning()
        }
    }

    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Auto-detection states: "idle", "searching", "holding", "capturing", "stepback"
    var autoStatus by remember { mutableStateOf("idle") }

    // Camera lifecycle observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                closeCamera(captureSession, cameraDevice)
                captureSession = null
                cameraDevice = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            closeCamera(captureSession, cameraDevice)
        }
    }

    // Background thread for camera
    val cameraThread = remember {
        HandlerThread("Camera2Background").apply { start() }
    }
    val cameraHandler = remember { Handler(cameraThread.looper) }

    DisposableEffect(Unit) {
        onDispose {
            cameraThread.quitSafely()
        }
    }

    fun toggleTorchMode() {
        val session = captureSession ?: return
        val builder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        val tv = textureViewRef ?: return
        val surface = Surface(tv.surfaceTexture)
        builder.addTarget(surface)

        torchOn = !torchOn
        val flashMode = if (torchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        builder.set(CaptureRequest.FLASH_MODE, flashMode)
        try {
            session.setRepeatingRequest(builder.build(), null, cameraHandler)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun startCamera(textureView: TextureView) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                cameraError = "ไม่พบกล้องบนอุปกรณ์นี้"
                return
            }

            val chars = cameraManager.getCameraCharacteristics(cameraId)
            torchAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaceTexture = textureView.surfaceTexture
                    if (surfaceTexture != null) {
                        surfaceTexture.setDefaultBufferSize(1280, 720)
                        val surface = Surface(surfaceTexture)
                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        builder.addTarget(surface)
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    captureSession = session
                                    try {
                                        session.setRepeatingRequest(builder.build(), null, cameraHandler)
                                        cameraError = null
                                    } catch (e: Exception) {
                                        cameraError = "ตั้งค่ากล้องไม่สำเร็จ: ${e.message}"
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    cameraError = "เปิดการเชื่อมต่อกล้องไม่สำเร็จ"
                                }
                            },
                            cameraHandler
                        )
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    cameraError = "กล้องเกิดข้อผิดพลาด (code $error)"
                }
            }, cameraHandler)
        } catch (e: Exception) {
            cameraError = "เปิดกล้องไม่สำเร็จ: ${e.message}"
        }
    }

    // Real-time Frame Analysis Coroutine Loop (every 250ms)
    LaunchedEffect(scanning, isProcessing) {
        if (!scanning || isProcessing) {
            autoStatus = "idle"
            return@LaunchedEffect
        }

        var prevGray: IntArray? = null
        var holdStart = 0L
        var stillSince = 0L

        while (isActive && scanning && !isProcessing) {
            delay(250) // AUTO_TICK_MS

            val tv = textureViewRef ?: continue
            if (!tv.isAvailable) continue

            // Sample 320x320 thumbnail for ultra fast CPU processing
            val thumb = try {
                tv.getBitmap(320, 320)
            } catch (_: Exception) {
                null
            } ?: continue

            val analysis = withContext(Dispatchers.Default) {
                LabelDetector.analyzeBitmap(thumb)
            }

            if (analysis == null) continue

            val diff = if (prevGray != null) LabelDetector.frameDifference(prevGray!!, analysis.gray) else 999f
            val moving = diff > 9f
            val isLabel = LabelDetector.looksLikeLabel(analysis)
            val tooClose = !isLabel && LabelDetector.looksTooClose(analysis)
            prevGray = analysis.gray

            if (!isLabel) {
                holdStart = 0L
                stillSince = 0L
                autoStatus = if (tooClose) "stepback" else "searching"
                continue
            }

            val now = System.currentTimeMillis()
            if (holdStart == 0L) holdStart = now
            if (moving) {
                stillSince = 0L
            } else if (stillSince == 0L) {
                stillSince = now
            }

            val stillFor = if (stillSince > 0) now - stillSince else 0L
            val heldFor = now - holdStart

            // Still for 500ms and held for 900ms -> Trigger Auto-Capture!
            if (stillFor >= 500L && heldFor >= 900L) {
                autoStatus = "capturing"
                val fullFrame = try {
                    tv.bitmap
                } catch (_: Exception) {
                    null
                }
                if (fullFrame != null) {
                    val sharpened = withContext(Dispatchers.Default) {
                        ImageEnhancer.sharpenBitmap(fullFrame)
                    }
                    onCapture(sharpened)
                }
                break
            } else {
                autoStatus = if (stillFor >= 500L) "holding" else "searching"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
    ) {
        if (hasCameraPermission && scanning) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        textureViewRef = this
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                startCamera(this@apply)
                            }
                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                closeCamera(captureSession, cameraDevice)
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder Target Frame Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = size.width * 0.72f
                val boxHeight = size.height * 0.72f
                val left = (size.width - boxWidth) / 2f
                val top = (size.height - boxHeight) / 2f

                drawRoundRect(
                    color = when (autoStatus) {
                        "holding", "capturing" -> Color(0xFF0D9488) // Teal
                        "stepback" -> Color(0xFFF59E0B) // Amber
                        else -> Color.White.copy(alpha = 0.65f)
                    },
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 4f)
                )
            }

            // Flashlight Toggle (Top-Right)
            if (torchAvailable) {
                IconButton(
                    onClick = { toggleTorchMode() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(42.dp)
                        .background(
                            if (torchOn) Color(0xFFFBBF24) else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "แฟลช",
                        tint = if (torchOn) Color(0xFF78350F) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Manual Shutter Button (Bottom-Center)
            if (!isProcessing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 54.dp)
                        .size(68.dp)
                        .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            val frame = textureViewRef?.bitmap
                            if (frame != null) {
                                autoStatus = "capturing"
                                onCapture(ImageEnhancer.sharpenBitmap(frame))
                            }
                        }
                )
            }
        } else {
            // Idle Viewfinder
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (!hasCameraPermission) "ต้องการสิทธิ์การใช้งานกล้อง" else "เปิดกล้องสแกนเพื่อเริ่มจับภาพใบปะหน้า",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        } else {
                            onStartScanning()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (!hasCameraPermission) "อนุญาตการใช้กล้อง" else "เปิดกล้องสแกน")
                }
            }
        }

        // Camera Error Banner
        cameraError?.let { err ->
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = Color(0xFF92400E),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Real-Time Status Pill (Bottom)
        if (scanning) {
            Surface(
                color = when (autoStatus) {
                    "holding", "capturing" -> Color(0xFFCCFBF1) // Teal container
                    "stepback" -> Color(0xFFFEF3C7) // Amber container
                    else -> Color.Black.copy(alpha = 0.65f)
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when (autoStatus) {
                                    "holding", "capturing" -> TealPrimary
                                    "stepback" -> Color(0xFFD97706)
                                    else -> Color(0xFF2DD4BF)
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (autoStatus) {
                            "holding" -> "ถือไว้อีกนิด ระบบกำลังจะถ่ายให้เอง…"
                            "capturing" -> "กำลังอ่านฉลาก…"
                            "stepback" -> "จ่อใกล้เกินไป — ถอยกล้องออกให้เห็นขอบใบปะหน้า"
                            else -> "จ่อใบปะหน้าให้อยู่กลางกรอบ ระบบจะถ่ายและอ่านให้เอง"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (autoStatus) {
                            "holding", "capturing" -> TealDark
                            "stepback" -> Color(0xFF92400E)
                            else -> Color.White
                        }
                    )
                }
            }
        }
    }
}

private fun closeCamera(session: CameraCaptureSession?, device: CameraDevice?) {
    try {
        session?.stopRepeating()
        session?.close()
    } catch (_: Exception) {}
    try {
        device?.close()
    } catch (_: Exception) {}
}
