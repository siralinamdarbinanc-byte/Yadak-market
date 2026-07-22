package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.ProductEntity
import com.example.ui.theme.GeoPrimary
import com.example.ui.viewmodel.ProductViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerScreen(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit,
    targetProduct: ProductEntity? = null,
    viewModel: ProductViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scannedValue by remember { mutableStateOf<String?>(null) }
    var foundProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(scannedValue) {
        scannedValue?.let { barcode ->
            if (!isProcessing) {
                isProcessing = true
                if (targetProduct != null) {
                    // حالت ثبت بارکد برای کالا
                    viewModel.updateBarcode(targetProduct.id, barcode)
                    onBarcodeDetected(barcode)
                } else {
                    // حالت جستجو با بارکد
                    val product = viewModel.getProductByBarcode(barcode)
                    if (product != null) {
                        foundProduct = product
                    } else {
                        onBarcodeDetected(barcode)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val scanner = BarcodeScanning.getClient()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && scannedValue == null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let { value ->
                                            scannedValue = value
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("دسترسی به دوربین لازم است", color = Color.White, fontSize = 16.sp)
            }
        }

        // دکمه بستن
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White)
        }

        // راهنما
        if (targetProduct != null) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text("ثبت بارکد برای: ${targetProduct.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
            Text("بارکد را جلوی دوربین بگیرید", color = Color.White, fontSize = 14.sp)
        }

        // نمایش کالای پیدا شده
        foundProduct?.let { product ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE1E1B30))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("کالا پیدا شد!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GeoPrimary)
                        Text(product.name, fontSize = 14.sp, color = Color.White)
                        Text(product.brand, fontSize = 13.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                        Text("${product.price} ریال", fontSize = 14.sp, color = GeoPrimary, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onBarcodeDetected(product.barcode ?: "") }) {
                                Text("مشاهده")
                            }
                            OutlinedButton(onClick = { foundProduct = null; scannedValue = null; isProcessing = false }) {
                                Text("اسکن مجدد", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
