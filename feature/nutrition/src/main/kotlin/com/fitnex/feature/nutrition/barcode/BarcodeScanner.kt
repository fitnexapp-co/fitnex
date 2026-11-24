package com.fitnex.feature.nutrition.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitnex.core.data.nutrition.BarcodeFoodLookup
import com.fitnex.core.data.nutrition.BarcodeResult
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Food
import com.fitnex.feature.nutrition.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val code: String) : ScanState
    data class Missing(val code: String) : ScanState
    data object Offline : ScanState
}

/**
 * Full-screen barcode scanner: point at the code, the product is looked up in Open Food Facts and
 * handed back as a [Food] (per-100 g label values) to pick the portion. Frames stay on the phone.
 */
@Composable
internal fun BarcodeScannerDialog(lookup: BarcodeFoodLookup, onFound: (Food) -> Unit, onCreateCustom: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    val scope = rememberCoroutineScope()

    fun onCode(code: String) {
        if (state != ScanState.Scanning) return
        state = ScanState.Looking(code)
        scope.launch {
            state = when (val r = lookup.lookup(code)) {
                is BarcodeResult.Found -> { onFound(r.food); return@launch }
                BarcodeResult.NotFound -> ScanState.Missing(code)
                BarcodeResult.NoConnection -> ScanState.Offline
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("barcode_scanner")) {
            if (granted && state == ScanState.Scanning) CameraScanner(::onCode)
            // Aiming frame.
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.78f).height(170.dp)
                    .border(3.dp, FitnexTheme.colors.accent, RoundedCornerShape(18.dp)),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.statusBarsPadding().padding(Spacing.md).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
            ) { Icon(Icons.Rounded.Close, stringResource(R.string.barcode_close), tint = Color.White) }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).navigationBarsPadding().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val s = state
                when {
                    !granted -> {
                        Text(stringResource(R.string.barcode_permission), color = Color.White, textAlign = TextAlign.Center)
                        FitnexPrimaryButton(stringResource(R.string.barcode_allow), { ask.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
                    }
                    s is ScanState.Looking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CircularProgressIndicator(color = FitnexTheme.colors.accent, modifier = Modifier.height(20.dp))
                        Text(stringResource(R.string.barcode_looking, s.code), color = Color.White)
                    }
                    s is ScanState.Missing -> {
                        Text(stringResource(R.string.barcode_missing, s.code), color = Color.White, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FitnexSecondaryButton(stringResource(R.string.barcode_again), { state = ScanState.Scanning }, Modifier.weight(1f))
                            FitnexPrimaryButton(stringResource(R.string.barcode_custom), onCreateCustom, Modifier.weight(1f))
                        }
                    }
                    s is ScanState.Offline -> {
                        Text(stringResource(R.string.barcode_offline), color = Color.White, textAlign = TextAlign.Center)
                        FitnexSecondaryButton(stringResource(R.string.barcode_again), { state = ScanState.Scanning }, Modifier.fillMaxWidth())
                    }
                    else -> Text(stringResource(R.string.barcode_hint), color = Color.White, textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.barcode_source), style = FitnexTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
        }
    }
}

// ImageProxy.image is CameraX's documented way to hand frames to ML Kit; it needs this opt-in.
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraScanner(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build(),
        )
        val future = ProcessCameraProvider.getInstance(context)
        val main = ContextCompat.getMainExecutor(context)
        future.addListener({
            val provider = future.get()
            val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image ?: run { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener(main) { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching { provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis) }
        }, main)
        onDispose {
            runCatching { future.get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView({ preview }, Modifier.fillMaxSize())
}
