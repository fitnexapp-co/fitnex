package com.fitnex.feature.workout.media

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.feature.workout.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Exercise photos (free-exercise-db, public domain): `<mediaUri>/0.jpg` = start position,
 * `<mediaUri>/1.jpg` = end position. Decoded downsampled from assets and cached in memory.
 */
private object MediaCache {
    // ~16 MB of decoded thumbnails/frames at most.
    val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Already decoded: shown on the very first frame (no placeholder flash when scrolling back). */
    fun cached(path: String, maxPx: Int): ImageBitmap? = cache.get("$path@$maxPx")

    fun load(context: Context, path: String, maxPx: Int): ImageBitmap? {
        val key = "$path@$maxPx"
        cache.get(key)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx) sample *= 2
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?.asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }
}

/** A mediaUri ending in "#ref" points to the photos of a very similar variant (shown with a label). */
private const val REFERENCE_SUFFIX = "#ref"
fun isReferenceMedia(mediaUri: String?): Boolean = mediaUri?.endsWith(REFERENCE_SUFFIX) == true
private fun mediaFolder(mediaUri: String): String = mediaUri.removeSuffix(REFERENCE_SUFFIX)

@Composable
private fun rememberAssetImage(path: String?, maxPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState(path?.let { MediaCache.cached(it, maxPx) }, path, maxPx) {
        if (value == null) value = path?.let { withContext(Dispatchers.IO) { MediaCache.load(context, it, maxPx) } }
    }
    return image
}

/** Small square photo for lists; a quiet icon when the exercise has no media (custom exercises). */
@Composable
fun ExerciseThumbnail(
    mediaUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    /** Shown when there is no photo: an icon that says what kind of exercise it is. */
    placeholder: androidx.compose.ui.graphics.vector.ImageVector = FitnexIcons.Workout,
) {
    val colors = FitnexTheme.colors
    val image = rememberAssetImage(mediaUri?.let { "${mediaFolder(it)}/1.jpg" }, maxPx = 200)
    // Already in memory (scrolling back, reopening a list): no fade, it is simply there.
    val instant = remember(mediaUri) { image != null } || FitnexTheme.appearance.reduceMotion
    Box(
        modifier.size(size).clip(RoundedCornerShape(Radii.sm)).background(colors.accentMuted),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // A photo still loading shows the plain tile (no icon that then jumps to a photo) and fades in.
            image != null -> {
                val shown = remember { androidx.compose.animation.core.Animatable(if (instant) 1f else 0f) }
                LaunchedEffect(image) { shown.animateTo(1f, tween(150)) }
                Image(
                    image, contentDescription = null, contentScale = ContentScale.Crop,
                    // Alpha read while drawing: the fade redraws without recomposing.
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value },
                )
            }
            mediaUri == null -> Icon(placeholder, contentDescription = null, tint = colors.accent, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/**
 * How the exercise looks: start and end photos alternating like a short animation. With
 * "Reducir animaciones" both positions are shown side by side instead.
 */
@Composable
fun ExerciseMotion(mediaUri: String, contentDescription: String, modifier: Modifier = Modifier, aspect: Float = 1.5f) {
    val colors = FitnexTheme.colors
    val folder = mediaFolder(mediaUri)
    val start = rememberAssetImage("$folder/0.jpg", maxPx = 900)
    val end = rememberAssetImage("$folder/1.jpg", maxPx = 900)
    val shape = RoundedCornerShape(Radii.lg)
    if (isReferenceMedia(mediaUri)) {
        // Honest label: the photo shows a close variant, not this exact exercise.
        Column(modifier.fillMaxWidth()) {
            MotionFrames(start, end, shape, contentDescription, Modifier, aspect)
            Text(
                stringResource(R.string.media_reference),
                style = FitnexTheme.typography.labelSmall, color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs).testTag("exercise_media_reference"),
            )
        }
        return
    }
    MotionFrames(start, end, shape, contentDescription, modifier, aspect)
}

@Composable
private fun MotionFrames(start: ImageBitmap?, end: ImageBitmap?, shape: RoundedCornerShape, contentDescription: String, modifier: Modifier, aspect: Float) {
    val colors = FitnexTheme.colors
    if (FitnexTheme.appearance.reduceMotion) {
        Row(modifier.fillMaxWidth().testTag("exercise_media"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(start to R.string.media_start, end to R.string.media_end).forEach { (img, label) ->
                Column(Modifier.weight(1f)) {
                    Frame(img, shape, Modifier.aspectRatio(aspect))
                    Text(stringResource(label), style = FitnexTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
        return
    }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_300); frame = 1 - frame }
    }
    Box(modifier.fillMaxWidth().aspectRatio(aspect).testTag("exercise_media")) {
        Crossfade(targetState = frame, animationSpec = tween(450), label = "exerciseMotion") { f ->
            Frame(if (f == 0) start else end, shape, Modifier.fillMaxSize(), contentDescription)
        }
        Text(
            stringResource(if (frame == 0) R.string.media_start else R.string.media_end),
            style = FitnexTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.sm)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(Radii.pill))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun Frame(image: ImageBitmap?, shape: RoundedCornerShape, modifier: Modifier, description: String? = null) {
    Box(modifier.clip(shape).background(FitnexTheme.colors.surfaceRaised)) {
        if (image != null) Image(image, description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
