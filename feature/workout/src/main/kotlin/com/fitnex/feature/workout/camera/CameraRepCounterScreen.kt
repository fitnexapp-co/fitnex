package com.fitnex.feature.workout.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.feature.workout.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors

/** Latest detected skeleton in upright image coordinates, for the overlay. */
private data class Skeleton(val points: Map<Joint, Landmark>, val imageWidth: Int, val imageHeight: Int)

private val LANDMARK_IDS = mapOf(
    Joint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER, Joint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    Joint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW, Joint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
    Joint.LEFT_WRIST to PoseLandmark.LEFT_WRIST, Joint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
    Joint.LEFT_HIP to PoseLandmark.LEFT_HIP, Joint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
    Joint.LEFT_KNEE to PoseLandmark.LEFT_KNEE, Joint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
    Joint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE, Joint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
)

private val BONES = listOf(
    Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER, Joint.LEFT_HIP to Joint.RIGHT_HIP,
    Joint.LEFT_SHOULDER to Joint.LEFT_ELBOW, Joint.LEFT_ELBOW to Joint.LEFT_WRIST,
    Joint.RIGHT_SHOULDER to Joint.RIGHT_ELBOW, Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST,
    Joint.LEFT_SHOULDER to Joint.LEFT_HIP, Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP,
    Joint.LEFT_HIP to Joint.LEFT_KNEE, Joint.LEFT_KNEE to Joint.LEFT_ANKLE,
    Joint.RIGHT_HIP to Joint.RIGHT_KNEE, Joint.RIGHT_KNEE to Joint.RIGHT_ANKLE,
)

/**
 * Full-screen counter. Frames go camera → ML Kit (on device) → angle → [RepCounter]; nothing is
 * recorded, stored or uploaded. "Usar N repeticiones" hands the count back to the live workout,
 * where it stays editable before the set is logged.
 */
@Composable
internal fun CameraRepCounterDialog(exerciseId: String, exerciseName: String, onUse: (Int) -> Unit, onDismiss: () -> Unit) {
    val exercise = remember(exerciseId) { CameraExercises.forExercise(exerciseId) } ?: return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("camera_counter")) {
            if (granted) {
                CounterContent(exercise, exerciseName, onUse, onDismiss)
            } else {
                PermissionContent(denied, onRetry = { request.launch(Manifest.permission.CAMERA) }, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun PermissionContent(denied: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.camera_permission_title), style = FitnexTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Text(
            stringResource(if (denied) R.string.camera_permission_denied else R.string.camera_permission_body),
            style = FitnexTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.lg),
        )
        FitnexPrimaryButton(stringResource(R.string.camera_permission_allow), onRetry, Modifier.fillMaxWidth())
        FitnexSecondaryButton(stringResource(R.string.camera_close), onDismiss, Modifier.fillMaxWidth().padding(top = Spacing.sm))
    }
}

// ImageProxy.image is CameraX's documented way to hand frames to ML Kit; it needs this opt-in.
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CounterContent(exercise: CameraExercise, exerciseName: String, onUse: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = FitnexTheme.colors
    var front by remember { mutableStateOf(true) }
    val counter = remember(exercise) { RepCounter(exercise) }
    var rep by remember { mutableStateOf(RepState()) }
    var skeleton by remember { mutableStateOf<Skeleton?>(null) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(front) {
        val executor = Executors.newSingleThreadExecutor()
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val main = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                val rotation = proxy.imageInfo.rotationDegrees
                val upright = rotation % 180 != 0
                val w = if (upright) proxy.height else proxy.width
                val h = if (upright) proxy.width else proxy.height
                detector.process(InputImage.fromMediaImage(media, rotation))
                    .addOnSuccessListener(main) { pose ->
                        val points = LANDMARK_IDS.mapNotNull { (joint, id) ->
                            pose.getPoseLandmark(id)?.let { joint to Landmark(it.position.x, it.position.y, it.inFrameLikelihood) }
                        }.toMap()
                        skeleton = Skeleton(points, w, h)
                        rep = counter.onFrame(exercise.measure(points), SystemClock.elapsedRealtime())
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }
        }, main)
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            detector.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        SkeletonOverlay(skeleton, mirrored = front, color = colors.accent, Modifier.fillMaxSize())

        // Top: exercise, close, switch camera.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))) {
                Icon(Icons.Rounded.Close, stringResource(R.string.camera_close), tint = Color.White)
            }
            Text(exerciseName, style = FitnexTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { front = !front }, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.camera_switch), tint = Color.White)
            }
        }

        // Bottom: count, guidance, actions.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .navigationBarsPadding()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                rep.count.toString(),
                style = FitnexTheme.typography.displayLarge.copy(fontSize = FitnexTheme.typography.displayLarge.fontSize * 1.6f),
                color = Color.White,
                modifier = Modifier.testTag("camera_count").semantics { liveRegion = LiveRegionMode.Polite },
            )
            val hint = when {
                !rep.tracking -> stringResource(R.string.camera_hint_frame)
                rep.phase == RepPhase.GET_READY -> stringResource(R.string.camera_hint_ready)
                else -> stringResource(R.string.camera_hint_go)
            }
            Text(hint, style = FitnexTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            LinearProgressIndicator(
                progress = { if (rep.tracking) rep.progress else 0f },
                color = colors.accent, trackColor = Color.White.copy(alpha = 0.2f), strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md).height(6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                FitnexSecondaryButton(stringResource(R.string.camera_reset), { counter.reset(); rep = counter.state }, Modifier.weight(1f))
                FitnexPrimaryButton(
                    pluralStringResource(R.plurals.camera_use, rep.count, rep.count), { onUse(rep.count) },
                    Modifier.weight(1.6f).testTag("camera_use"), enabled = rep.count > 0,
                )
            }
            Text(
                stringResource(R.string.camera_privacy),
                style = FitnexTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/** Bones over the preview, mapped like PreviewView's FILL_CENTER (and mirrored for the selfie camera). */
@Composable
private fun SkeletonOverlay(skeleton: Skeleton?, mirrored: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = skeleton ?: return@Canvas
        if (s.imageWidth == 0 || s.imageHeight == 0) return@Canvas
        val scale = maxOf(size.width / s.imageWidth, size.height / s.imageHeight)
        val dx = (size.width - s.imageWidth * scale) / 2f
        val dy = (size.height - s.imageHeight * scale) / 2f
        fun map(p: Landmark): Offset {
            val x = p.x * scale + dx
            return Offset(if (mirrored) size.width - x else x, p.y * scale + dy)
        }
        val visible = s.points.filterValues { it.likelihood >= 0.5f }
        BONES.forEach { (a, b) ->
            val pa = visible[a] ?: return@forEach
            val pb = visible[b] ?: return@forEach
            drawLine(color, map(pa), map(pb), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        visible.values.forEach { drawCircle(Color.White, 5.dp.toPx(), map(it)) }
    }
}
