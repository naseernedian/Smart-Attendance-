package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.biometrics.LiveFaceAnalysisState
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    onGrantedContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        onGrantedContent()
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera permission needed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Camera permission is required for live facial capture and QR scanning.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("grant_camera_permission_button")
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun LiveFaceCameraPanel(
    title: String,
    captureButtonText: String,
    defaultUseFrontCamera: Boolean,
    allowCameraSwitch: Boolean,
    isBusy: Boolean,
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier,
    onFaceCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var lensFacing by remember(defaultUseFrontCamera) {
        mutableIntStateOf(
            if (defaultUseFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }
    var analysisState by remember {
        mutableStateOf(
            LiveFaceAnalysisState(
                faceCount = 0,
                isCenteredAndClear = false,
                statusMessage = "Initializing live camera..."
            )
        )
    }
    var captureError by remember { mutableStateOf<String?>(null) }
    var pictureCaptured by remember { mutableStateOf(false) }
    var capturedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCaptureInFlight by remember { mutableStateOf(false) }
    var stableClearFrames by remember { mutableIntStateOf(0) }

    val currentOnFaceCaptured by rememberUpdatedState(onFaceCaptured)
    val currentIsBusy by rememberUpdatedState(isBusy)

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val liveDetector = remember {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.18f)
            .build()
        FaceDetection.getClient(opts)
    }

    fun triggerCaptureNow() {
        if (isCaptureInFlight || currentIsBusy) return
        captureError = null
        isCaptureInFlight = true
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val upright = imageProxyToUprightBitmap(
                            image,
                            mirrorHorizontally = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                        )
                        capturedPreviewBitmap = upright
                        pictureCaptured = true
                        isCaptureInFlight = false
                        analysisState = LiveFaceAnalysisState(
                            faceCount = 1,
                            isCenteredAndClear = true,
                            statusMessage = "Picture Captured"
                        )
                        currentOnFaceCaptured(upright)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCaptureInFlight = false
                    captureError = "Capture failed: ${exception.message}"
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            liveDetector.close()
        }
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            pictureCaptured = false
            capturedPreviewBitmap = null
            stableClearFrames = 0
            captureError = null
            isCaptureInFlight = false
            analysisState = LiveFaceAnalysisState(
                faceCount = 0,
                isCenteredAndClear = false,
                statusMessage = "Align next student face inside the frame"
            )
        }
    }

    LaunchedEffect(lensFacing) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            var lastProcessedMs = 0L
            analyzer.setAnalyzer(cameraExecutor) { proxy ->
                val now = System.currentTimeMillis()
                if (pictureCaptured || isCaptureInFlight || currentIsBusy || (now - lastProcessedMs < 240L)) {
                    proxy.close()
                    return@setAnalyzer
                }
                lastProcessedMs = now
                val mediaImage = proxy.image
                if (mediaImage == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val rot = proxy.imageInfo.rotationDegrees
                val input = InputImage.fromMediaImage(mediaImage, rot)
                val imgW = if (rot == 90 || rot == 270) proxy.height else proxy.width
                val imgH = if (rot == 90 || rot == 270) proxy.width else proxy.height

                liveDetector.process(input)
                    .addOnSuccessListener { faces ->
                        val state = evaluateQuickLiveFaces(faces, imgW, imgH)
                        mainExecutor.execute {
                            if (!pictureCaptured) {
                                analysisState = state
                                if (state.isCenteredAndClear && !isCaptureInFlight && !currentIsBusy) {
                                    stableClearFrames += 1
                                    if (stableClearFrames >= 2) {
                                        triggerCaptureNow()
                                    }
                                } else {
                                    stableClearFrames = 0
                                }
                            }
                        }
                    }
                    .addOnCompleteListener {
                        proxy.close()
                    }
            }

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val bindResult = runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                    analyzer
                )
            }
            if (bindResult.isFailure) {
                val fallbackFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder().requireLensFacing(fallbackFacing).build(),
                        preview,
                        imageCapture,
                        analyzer
                    )
                    lensFacing = fallbackFacing
                }.onFailure { err ->
                    captureError = "Camera unavailable: ${err.message}"
                }
            }
        }, mainExecutor)
    }

    CameraPermissionGate(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (allowCameraSwitch) {
                        OutlinedButton(
                            onClick = {
                                pictureCaptured = false
                                capturedPreviewBitmap = null
                                stableClearFrames = 0
                                captureError = null
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("switch_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch front or back camera",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    "Back Camera"
                                } else {
                                    "Front Camera"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    val frozenBmp = capturedPreviewBitmap
                    if (pictureCaptured && frozenBmp != null) {
                        Image(
                            bitmap = frozenBmp.asImageBitmap(),
                            contentDescription = "Picture Captured",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Face alignment guide frame
                    val frameColor = if (pictureCaptured || analysisState.isCenteredAndClear) VerifiedGreen else Color(0xFFFFC107)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rectW = size.width * 0.56f
                        val rectH = size.height * 0.74f
                        val left = (size.width - rectW) / 2f
                        val top = (size.height - rectH) / 2f
                        drawRoundRect(
                            color = frameColor,
                            topLeft = Offset(left, top),
                            size = Size(rectW, rectH),
                            cornerRadius = CornerRadius(24f, 24f),
                            style = Stroke(width = 4f)
                        )
                    }

                    if (pictureCaptured) {
                        Surface(
                            color = VerifiedGreenBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VerifiedGreen),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .testTag("picture_captured_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = VerifiedGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Picture Captured",
                                    color = VerifiedGreen,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = analysisState.statusMessage,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (captureError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = captureError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (pictureCaptured) {
                    OutlinedButton(
                        onClick = {
                            pictureCaptured = false
                            capturedPreviewBitmap = null
                            stableClearFrames = 0
                            captureError = null
                            analysisState = LiveFaceAnalysisState(
                                faceCount = 0,
                                isCenteredAndClear = false,
                                statusMessage = "Align face inside the frame"
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("retake_live_face_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isBusy) "Processing Picture..." else "Picture Captured • Retake Picture")
                    }
                } else {
                    Button(
                        onClick = { triggerCaptureNow() },
                        enabled = !isBusy && !isCaptureInFlight,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (analysisState.isCenteredAndClear) VerifiedGreen else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("capture_live_face_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isBusy || isCaptureInFlight) "Capturing Picture..." else captureButtonText)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun LiveQrScannerDialog(
    onDismiss: () -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    var handled by remember { mutableStateOf(false) }

    val scanner = remember {
        val opts = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(opts)
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Scan Student QR Code",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_qr_scanner")) {
                        Icon(Icons.Default.Close, contentDescription = "Close scanner")
                    }
                }

                Spacer(Modifier.height(10.dp))

                CameraPermissionGate {
                    LaunchedEffect(Unit) {
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                val mediaImage = proxy.image
                                if (mediaImage == null || handled) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                                scanner.process(input)
                                    .addOnSuccessListener { codes ->
                                        val value = codes.firstOrNull()?.rawValue
                                        if (!value.isNullOrBlank() && !handled) {
                                            handled = true
                                            onQrDetected(value)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        proxy.close()
                                    }
                            }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxSize = size.minDimension * 0.62f
                            val left = (size.width - boxSize) / 2f
                            val top = (size.height - boxSize) / 2f
                            drawRoundRect(
                                color = Color(0xFF4ADE80),
                                topLeft = Offset(left, top),
                                size = Size(boxSize, boxSize),
                                cornerRadius = CornerRadius(16f, 16f),
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun DocumentCameraCaptureDialog(
    title: String,
    onDismiss: () -> Unit,
    onDocumentCaptured: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                CameraPermissionGate {
                    LaunchedEffect(Unit) {
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        val frozenDoc = capturedBitmap
                        if (frozenDoc != null) {
                            Image(
                                bitmap = frozenDoc.asImageBitmap(),
                                contentDescription = "Picture Captured",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = VerifiedGreenBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, VerifiedGreen),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = VerifiedGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Picture Captured",
                                        color = VerifiedGreen,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    if (capturedBytes != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    capturedBitmap = null
                                    capturedBytes = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Retake")
                            }
                            Button(
                                onClick = {
                                    capturedBytes?.let(onDocumentCaptured)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Use Picture")
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    imageCapture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                try {
                                                    val bmp = imageProxyToUprightBitmap(image, mirrorHorizontally = false)
                                                    val scaled = scaleMaxDimension(bmp, 1280)
                                                    val out = ByteArrayOutputStream()
                                                    scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
                                                    capturedBitmap = scaled
                                                    capturedBytes = out.toByteArray()
                                                } finally {
                                                    image.close()
                                                }
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Capture")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun evaluateQuickLiveFaces(
    faces: List<com.google.mlkit.vision.face.Face>,
    imgW: Int,
    imgH: Int
): LiveFaceAnalysisState {
    if (faces.isEmpty()) {
        return LiveFaceAnalysisState(0, false, "Align face inside the frame")
    }
    if (faces.size > 1) {
        return LiveFaceAnalysisState(faces.size, false, "Multiple faces detected — one person only")
    }
    val face = faces.first()
    val widthRatio = face.boundingBox.width().toFloat() / maxOf(1, imgW)
    if (widthRatio < 0.16f) {
        return LiveFaceAnalysisState(1, false, "Move closer to camera")
    }
    if (kotlin.math.abs(face.headEulerAngleY) > 18f || kotlin.math.abs(face.headEulerAngleZ) > 15f) {
        return LiveFaceAnalysisState(1, false, "Look straight ahead")
    }
    val leftEye = face.leftEyeOpenProbability
    val rightEye = face.rightEyeOpenProbability
    if ((leftEye != null && leftEye < 0.35f) || (rightEye != null && rightEye < 0.35f)) {
        return LiveFaceAnalysisState(1, false, "Keep eyes open")
    }
    return LiveFaceAnalysisState(1, true, "Live face aligned — tap Capture")
}

private fun imageProxyToUprightBitmap(image: ImageProxy, mirrorHorizontally: Boolean): Bitmap {
    val raw = image.toBitmap()
    val matrix = Matrix().apply {
        postRotate(image.imageInfo.rotationDegrees.toFloat())
        if (mirrorHorizontally) {
            postScale(-1f, 1f)
        }
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

private fun scaleMaxDimension(bmp: Bitmap, maxDim: Int): Bitmap {
    val largest = maxOf(bmp.width, bmp.height)
    if (largest <= maxDim) return bmp
    val ratio = maxDim.toFloat() / largest
    return Bitmap.createScaledBitmap(
        bmp,
        (bmp.width * ratio).toInt().coerceAtLeast(1),
        (bmp.height * ratio).toInt().coerceAtLeast(1),
        true
    )
}
