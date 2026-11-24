package com.fitnex.core.anatomy.map3d

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fitnex.core.anatomy.map2d.BodyView
import com.fitnex.core.anatomy.rememberMusclePalette
import com.fitnex.core.designsystem.theme.FitnexTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Camera that the map's buttons and gestures drive; survives recomposition, reset by "Centrar". */
class Map3DState {
    internal val yaw = Animatable(0f)
    internal var zoom by mutableFloatStateOf(1f)
    internal var pan by mutableStateOf(Offset.Zero)

    internal fun camera() = Camera3D(yaw = yaw.value, zoom = zoom, panX = pan.x, panY = pan.y)

    /** Turns the shortest way to the front (0) or the back (π). */
    /** [animate] false ("Reducir animaciones"): the body is turned at once instead of rotating. */
    internal suspend fun face(view: BodyView, animate: Boolean = true) {
        val target = if (view == BodyView.FRONT) 0.0 else PI
        val turns = round((yaw.value - target) / (2 * PI))
        val to = (target + turns * 2 * PI).toFloat()
        if (animate) yaw.animateTo(to, tween(550)) else yaw.snapTo(to)
    }

    internal fun resetFraming() { zoom = 1f; pan = Offset.Zero }
}

@Composable
fun rememberMap3DState() = remember { Map3DState() }

/**
 * Whether to offer the 3D map. Low-RAM devices get the 2D map only (spec §5: automatic 2D fallback).
 */
@Composable
fun rememberSupports3D(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice != true
    }
}

/**
 * The body is parsed and segmented once per process, off the main thread, and only when a 3D map
 * is first shown. Failure (missing or corrupt asset) is reported so the card can fall back to 2D.
 */
internal object BodyMeshLoader {
    private val lock = Mutex()
    private var cached: BodyMesh? = null

    suspend fun load(context: Context): BodyMesh = lock.withLock {
        cached ?: withContext(Dispatchers.Default) {
            val body = context.assets.open(HumanBody.ASSET).use { HumanBody.parse(it) }
            BodyMesh(body)
        }.also { cached = it }
    }
}

/**
 * Rotatable 3D body. One finger sideways rotates (vertical swipes keep scrolling the page),
 * two fingers zoom and move the camera, a tap selects the muscle under the finger.
 */
@Composable
fun MuscleMap3D(
    state: Map3DState,
    view: BodyView,
    levels: Map<String, Float>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onUnavailable: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unavailable by rememberUpdatedState(onUnavailable)
    val mesh by produceState<BodyMesh?>(null) {
        value = runCatching { BodyMeshLoader.load(context.applicationContext) }.getOrElse { unavailable(); null }
    }
    val loaded = mesh
    if (loaded == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FitnexTheme.colors.accent) }
        return
    }

    // Grey statue, violet where worked (the 2D map keeps its white porcelain figure).
    val palette = com.fitnex.core.anatomy.rememberStatuePalette()
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val reduceMotion = com.fitnex.core.designsystem.theme.FitnexTheme.appearance.reduceMotion
    LaunchedEffect(view) { state.face(view, animate = !reduceMotion) }

    // No endless pulse with "Reducir animaciones": the selected muscle is simply highlighted.
    val pulse: State<Float>? = if (selected != null && !com.fitnex.core.designsystem.theme.FitnexTheme.appearance.reduceMotion) {
        rememberInfiniteTransition(label = "pulse3d").animateFloat(
            0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
        )
    } else null
    val baseColors = remember(loaded, levels, palette) {
        IntArray(loaded.parts.size) { i ->
            val id = loaded.parts[i]
            // On a sculpted body the non-muscle zones read as skin, so a mid grey between the 2D
            // "secondary zone" grey and the idle muscle white keeps it one continuous figure.
            (if (id == null) lerp(palette.body, palette.idle, 0.5f) else palette.fill(levels[id] ?: 0f)).toArgb()
        }
    }
    val colors = remember(baseColors) { baseColors.copyOf() }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val frame = remember { FrameBuffer() }

    Canvas(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var travelled = Offset.Zero
                    var rotating = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            state.zoom = (state.zoom * event.calculateZoom()).coerceIn(Camera3D.MIN_ZOOM, Camera3D.MAX_ZOOM)
                            state.pan += event.calculatePan()
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1) {
                            val delta = event.changes.first { it.pressed }.positionChange()
                            travelled += delta
                            if (!rotating && abs(travelled.x) > slop && abs(travelled.x) > abs(travelled.y)) rotating = true
                            if (rotating) {
                                scope.launch { state.yaw.snapTo(state.yaw.value + delta.x * ROTATE_PER_PX) }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(loaded) {
                detectTapGestures { p ->
                    select(loaded.pick(state.camera(), size.width.toFloat(), size.height.toFloat(), p.x, p.y))
                }
            },
    ) {
        if (selected == null) {
            baseColors.copyInto(colors)
        } else {
            val pulsing = lerp(palette.active, palette.selected, pulse?.value ?: 1f).toArgb()
            loaded.parts.forEachIndexed { i, id -> colors[i] = if (id == selected) pulsing else baseColors[i] }
        }
        // Render at reduced resolution and scale up with filtering: smooth edges, fast frames.
        val bw = (size.width * RESOLUTION).toInt().coerceAtLeast(1)
        val bh = (size.height * RESOLUTION).toInt().coerceAtLeast(1)
        val buf = frame.ensure(bw, bh)
        loaded.rasterize(state.camera(), bw, bh, colors, buf.pixels, RESOLUTION)
        buf.bitmap.setPixels(buf.pixels, 0, bw, 0, 0, bw, bh)
        drawIntoCanvas {
            it.nativeCanvas.drawBitmap(buf.bitmap, null, android.graphics.RectF(0f, 0f, size.width, size.height), paint)
        }
    }
}

/** Reused frame buffer; reallocated only when the canvas size changes. */
private class FrameBuffer {
    var pixels = IntArray(0); private set
    var bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); private set

    fun ensure(w: Int, h: Int): FrameBuffer {
        if (bitmap.width != w || bitmap.height != h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels = IntArray(w * h)
        }
        return this
    }
}

private const val ROTATE_PER_PX = 0.011f
private const val RESOLUTION = 0.6f
