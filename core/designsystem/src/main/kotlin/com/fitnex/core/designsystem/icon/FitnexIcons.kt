package com.fitnex.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimal line icons (1.8 px stroke on a 24 grid) drawn for FITNEX.
 * They are tinted by the Icon composable, so the stroke colour here is irrelevant.
 */
object FitnexIcons {
    val Progress: ImageVector by lazy {
        lineIcon("Progress") {
            moveTo(3f, 20f); lineTo(21f, 20f)
            moveTo(4f, 16f); lineTo(9f, 10.5f); lineTo(13f, 13.5f); lineTo(20f, 5f)
            moveTo(15.5f, 5f); lineTo(20f, 5f); lineTo(20f, 9.5f)
        }
    }

    val Nutrition: ImageVector by lazy {
        lineIcon("Nutrition") {
            // Leaf-topped apple silhouette.
            moveTo(12f, 7.5f)
            curveTo(9.5f, 5.8f, 4.5f, 6.2f, 4.5f, 11.5f)
            curveTo(4.5f, 16.5f, 8f, 21f, 10.5f, 21f)
            curveTo(11.3f, 21f, 11.6f, 20.5f, 12f, 20.5f)
            curveTo(12.4f, 20.5f, 12.7f, 21f, 13.5f, 21f)
            curveTo(16f, 21f, 19.5f, 16.5f, 19.5f, 11.5f)
            curveTo(19.5f, 6.2f, 14.5f, 5.8f, 12f, 7.5f)
            close()
            moveTo(12f, 7.5f); curveTo(12f, 5f, 13f, 3.5f, 15.5f, 3f)
        }
    }

    val Workout: ImageVector by lazy {
        lineIcon("Workout") {
            // Dumbbell.
            moveTo(8f, 12f); lineTo(16f, 12f)
            moveTo(6f, 7.5f); lineTo(6f, 16.5f)
            moveTo(8.5f, 6f); lineTo(8.5f, 18f)
            moveTo(18f, 7.5f); lineTo(18f, 16.5f)
            moveTo(15.5f, 6f); lineTo(15.5f, 18f)
            moveTo(3f, 10f); lineTo(3f, 14f)
            moveTo(21f, 10f); lineTo(21f, 14f)
        }
    }

    val Sparkle: ImageVector by lazy {
        lineIcon("Sparkle") {
            moveTo(12f, 3f)
            curveTo(12.6f, 8.2f, 13.8f, 10.4f, 20f, 12f)
            curveTo(13.8f, 13.6f, 12.6f, 15.8f, 12f, 21f)
            curveTo(11.4f, 15.8f, 10.2f, 13.6f, 4f, 12f)
            curveTo(10.2f, 10.4f, 11.4f, 8.2f, 12f, 3f)
            close()
            moveTo(19f, 3.5f); lineTo(19f, 6.5f)
            moveTo(17.5f, 5f); lineTo(20.5f, 5f)
        }
    }

    val Calendar: ImageVector by lazy {
        lineIcon("Calendar") {
            moveTo(5f, 5.5f); lineTo(19f, 5.5f); lineTo(19f, 20f); lineTo(5f, 20f); close()
            moveTo(5f, 10f); lineTo(19f, 10f)
            moveTo(9f, 3.5f); lineTo(9f, 7.5f)
            moveTo(15f, 3.5f); lineTo(15f, 7.5f)
        }
    }

    val Camera: ImageVector by lazy {
        lineIcon("Camera") {
            moveTo(3.5f, 8f); lineTo(7.5f, 8f); lineTo(9f, 5.5f); lineTo(15f, 5.5f); lineTo(16.5f, 8f); lineTo(20.5f, 8f)
            lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            moveTo(12f, 10f)
            curveTo(14f, 10f, 15.5f, 11.5f, 15.5f, 13.5f)
            curveTo(15.5f, 15.5f, 14f, 17f, 12f, 17f)
            curveTo(10f, 17f, 8.5f, 15.5f, 8.5f, 13.5f)
            curveTo(8.5f, 11.5f, 10f, 10f, 12f, 10f)
            close()
        }
    }

    /** A picture (frame, sun and mountains): "choose from the gallery". */
    val Image: ImageVector by lazy {
        lineIcon("Image") {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            moveTo(4.5f, 17f); lineTo(9.5f, 11.5f); lineTo(13f, 15f); lineTo(15.5f, 12.5f); lineTo(19.5f, 16.5f)
            moveTo(15.5f, 7.8f)
            curveTo(16.5f, 7.8f, 17.2f, 8.5f, 17.2f, 9.5f)
            curveTo(17.2f, 10.5f, 16.5f, 11.2f, 15.5f, 11.2f)
            curveTo(14.5f, 11.2f, 13.8f, 10.5f, 13.8f, 9.5f)
            curveTo(13.8f, 8.5f, 14.5f, 7.8f, 15.5f, 7.8f)
            close()
        }
    }

    val Barcode: ImageVector by lazy {
        lineIcon("Barcode") {
            moveTo(3.5f, 8f); lineTo(3.5f, 4.5f); lineTo(7f, 4.5f)
            moveTo(17f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 8f)
            moveTo(20.5f, 16f); lineTo(20.5f, 19.5f); lineTo(17f, 19.5f)
            moveTo(7f, 19.5f); lineTo(3.5f, 19.5f); lineTo(3.5f, 16f)
            moveTo(7.5f, 8.5f); lineTo(7.5f, 15.5f)
            moveTo(10.5f, 8.5f); lineTo(10.5f, 15.5f)
            moveTo(13f, 8.5f); lineTo(13f, 15.5f)
            moveTo(16.5f, 8.5f); lineTo(16.5f, 15.5f)
        }
    }

    val Drop: ImageVector by lazy {
        lineIcon("Drop") {
            moveTo(12f, 3.5f)
            curveTo(9f, 8f, 6f, 11f, 6f, 14.5f)
            curveTo(6f, 18f, 8.7f, 20.5f, 12f, 20.5f)
            curveTo(15.3f, 20.5f, 18f, 18f, 18f, 14.5f)
            curveTo(18f, 11f, 15f, 8f, 12f, 3.5f)
            close()
        }
    }

    val Chart: ImageVector by lazy {
        lineIcon("Chart") {
            moveTo(5f, 20f); lineTo(5f, 13f)
            moveTo(10f, 20f); lineTo(10f, 8f)
            moveTo(15f, 20f); lineTo(15f, 11f)
            moveTo(20f, 20f); lineTo(20f, 4.5f)
        }
    }

    val People: ImageVector by lazy {
        lineIcon("People") {
            circle(9f, 8.5f, 3.2f)
            moveTo(3f, 19.5f); curveTo(3.5f, 15.8f, 6f, 14f, 9f, 14f); curveTo(12f, 14f, 14.5f, 15.8f, 15f, 19.5f)
            moveTo(15.5f, 5.6f); curveTo(17.4f, 5.6f, 18.7f, 7f, 18.7f, 8.6f); curveTo(18.7f, 10.2f, 17.4f, 11.6f, 15.5f, 11.6f)
            moveTo(17f, 14.3f); curveTo(19.3f, 14.8f, 20.6f, 16.6f, 21f, 19.5f)
        }
    }

    val Brush: ImageVector by lazy {
        lineIcon("Brush") {
            moveTo(20f, 4f); lineTo(11f, 13f)
            moveTo(9.5f, 13.5f); curveTo(7f, 13.5f, 5.5f, 15f, 5.5f, 17f); curveTo(5.5f, 18.5f, 4.5f, 19.5f, 3.5f, 20f)
            curveTo(7.5f, 20.5f, 11.5f, 19f, 11f, 15f); close()
        }
    }

    val Timer: ImageVector by lazy {
        lineIcon("Timer") {
            circle(12f, 13.5f, 7f)
            moveTo(12f, 13.5f); lineTo(12f, 9.5f)
            moveTo(10f, 3f); lineTo(14f, 3f)
            moveTo(18f, 6.5f); lineTo(19.5f, 5f)
        }
    }

    val Cloud: ImageVector by lazy {
        lineIcon("Cloud") {
            moveTo(7f, 18.5f)
            curveTo(4.5f, 18.5f, 3f, 16.8f, 3f, 14.8f); curveTo(3f, 12.8f, 4.6f, 11.3f, 6.5f, 11.2f)
            curveTo(7f, 8f, 9.4f, 6f, 12.3f, 6f); curveTo(15.3f, 6f, 17.6f, 8.2f, 17.9f, 11f)
            curveTo(19.8f, 11.2f, 21f, 12.8f, 21f, 14.7f); curveTo(21f, 16.8f, 19.5f, 18.5f, 17.2f, 18.5f); close()
        }
    }

    val Heart: ImageVector by lazy {
        lineIcon("Heart") {
            moveTo(12f, 20f)
            curveTo(6f, 16f, 3f, 12.8f, 3f, 9.2f); curveTo(3f, 6.5f, 5f, 4.5f, 7.5f, 4.5f); curveTo(9.4f, 4.5f, 11f, 5.6f, 12f, 7.2f)
            curveTo(13f, 5.6f, 14.6f, 4.5f, 16.5f, 4.5f); curveTo(19f, 4.5f, 21f, 6.5f, 21f, 9.2f); curveTo(21f, 12.8f, 18f, 16f, 12f, 20f); close()
        }
    }

    val Shield: ImageVector by lazy {
        lineIcon("Shield") {
            moveTo(12f, 3f); lineTo(19.5f, 6f); curveTo(19.5f, 13f, 16.5f, 18f, 12f, 21f); curveTo(7.5f, 18f, 4.5f, 13f, 4.5f, 6f); close()
            moveTo(9f, 12f); lineTo(11.2f, 14.2f); lineTo(15.2f, 10f)
        }
    }

    val Body: ImageVector by lazy {
        lineIcon("Body") {
            circle(12f, 4.8f, 2f)
            moveTo(5f, 9f); lineTo(19f, 9f)
            moveTo(12f, 9f); lineTo(12f, 14f)
            moveTo(9f, 21f); lineTo(10.5f, 14f); lineTo(13.5f, 14f); lineTo(15f, 21f)
        }
    }

    val Gear: ImageVector by lazy {
        lineIcon("Gear") {
            circle(12f, 12f, 3f)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                moveTo(12f + c * 5.6f, 12f + s * 5.6f); lineTo(12f + c * 8.4f, 12f + s * 8.4f)
            }
            circle(12f, 12f, 5.6f)
        }
    }

    val Motion: ImageVector by lazy {
        lineIcon("Motion") {
            circle(12f, 12f, 8f)
            circle(12f, 12f, 3.5f)
            moveTo(5f, 19f); lineTo(19f, 5f)
        }
    }

    val Theme: ImageVector by lazy {
        lineIcon("Theme") {
            circle(12f, 12f, 8f)
            moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(12f, 8f); lineTo(18.9f, 8f)
            moveTo(12f, 12f); lineTo(20f, 12f)
            moveTo(12f, 16f); lineTo(18.9f, 16f)
        }
    }

    val Swap: ImageVector by lazy {
        lineIcon("Swap") {
            moveTo(4f, 8f); lineTo(18f, 8f); moveTo(15f, 5f); lineTo(18f, 8f); lineTo(15f, 11f)
            moveTo(20f, 16f); lineTo(6f, 16f); moveTo(9f, 13f); lineTo(6f, 16f); lineTo(9f, 19f)
        }
    }

    val Target: ImageVector by lazy {
        lineIcon("Target") {
            circle(12f, 12f, 8.5f)
            circle(12f, 12f, 4.5f)
            circle(12f, 12f, 0.9f)
        }
    }

    val Layers: ImageVector by lazy {
        lineIcon("Layers") {
            moveTo(12f, 3.5f); lineTo(21f, 8f); lineTo(12f, 12.5f); lineTo(3f, 8f); close()
            moveTo(3f, 12f); lineTo(12f, 16.5f); lineTo(21f, 12f)
            moveTo(3f, 16f); lineTo(12f, 20.5f); lineTo(21f, 16f)
        }
    }

    val Repeat: ImageVector by lazy {
        lineIcon("Repeat") {
            moveTo(4f, 11f); curveTo(4f, 7.5f, 6.5f, 5.5f, 10f, 5.5f); lineTo(19f, 5.5f)
            moveTo(16f, 2.5f); lineTo(19f, 5.5f); lineTo(16f, 8.5f)
            moveTo(20f, 13f); curveTo(20f, 16.5f, 17.5f, 18.5f, 14f, 18.5f); lineTo(5f, 18.5f)
            moveTo(8f, 15.5f); lineTo(5f, 18.5f); lineTo(8f, 21.5f)
        }
    }

    val Clock: ImageVector by lazy {
        lineIcon("Clock") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
        }
    }

    val Kettlebell: ImageVector by lazy {
        lineIcon("Kettlebell") {
            moveTo(8.5f, 9f); curveTo(7.8f, 4f, 16.2f, 4f, 15.5f, 9f)
            moveTo(6f, 20.5f); lineTo(18f, 20.5f); curveTo(20f, 17f, 19.5f, 10f, 12f, 9f); curveTo(4.5f, 10f, 4f, 17f, 6f, 20.5f); close()
        }
    }

    val Pause: ImageVector by lazy {
        lineIcon("Pause") {
            moveTo(9f, 5.5f); lineTo(9f, 18.5f)
            moveTo(15f, 5.5f); lineTo(15f, 18.5f)
        }
    }

    val Flame: ImageVector by lazy {
        lineIcon("Flame") {
            moveTo(12f, 21f)
            curveTo(8f, 21f, 5.5f, 18.3f, 5.5f, 14.8f)
            curveTo(5.5f, 10.5f, 9.5f, 8.5f, 10f, 3f)
            curveTo(13.5f, 5f, 15f, 8f, 14.5f, 10.5f)
            curveTo(15.5f, 10f, 16.3f, 9f, 16.5f, 8f)
            curveTo(18f, 9.8f, 18.5f, 12.3f, 18.5f, 14.8f)
            curveTo(18.5f, 18.3f, 16f, 21f, 12f, 21f)
            close()
            moveTo(12f, 21f); curveTo(10.3f, 21f, 9.3f, 19.7f, 9.3f, 18.2f); curveTo(9.3f, 16.2f, 11f, 15.5f, 12f, 13.5f); curveTo(13f, 15.5f, 14.7f, 16.2f, 14.7f, 18.2f); curveTo(14.7f, 19.7f, 13.7f, 21f, 12f, 21f)
        }
    }

    val Bulb: ImageVector by lazy {
        lineIcon("Bulb") {
            moveTo(9.5f, 17.5f); lineTo(14.5f, 17.5f)
            moveTo(10f, 20.5f); lineTo(14f, 20.5f)
            moveTo(9.5f, 17.5f); curveTo(9.5f, 15f, 6.5f, 13.5f, 6.5f, 9.5f)
            curveTo(6.5f, 6.4f, 9f, 4f, 12f, 4f); curveTo(15f, 4f, 17.5f, 6.4f, 17.5f, 9.5f)
            curveTo(17.5f, 13.5f, 14.5f, 15f, 14.5f, 17.5f)
            moveTo(12f, 1f); lineTo(12f, 1.5f)
            moveTo(3f, 9.5f); lineTo(3.8f, 9.5f)
            moveTo(20.2f, 9.5f); lineTo(21f, 9.5f)
        }
    }

    val Calculator: ImageVector by lazy {
        lineIcon("Calculator") {
            moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(6f, 21f); close()
            moveTo(8.5f, 6f); lineTo(15.5f, 6f); lineTo(15.5f, 9f); lineTo(8.5f, 9f); close()
            moveTo(9f, 12.5f); lineTo(9.01f, 12.5f)
            moveTo(12f, 12.5f); lineTo(12.01f, 12.5f)
            moveTo(15f, 12.5f); lineTo(15.01f, 12.5f)
            moveTo(9f, 15.5f); lineTo(9.01f, 15.5f)
            moveTo(12f, 15.5f); lineTo(12.01f, 15.5f)
            moveTo(15f, 15.5f); lineTo(15.01f, 15.5f)
            moveTo(9f, 18.2f); lineTo(9.01f, 18.2f)
            moveTo(12f, 18.2f); lineTo(12.01f, 18.2f)
            moveTo(15f, 18.2f); lineTo(15.01f, 18.2f)
        }
    }

    val Cutlery: ImageVector by lazy {
        lineIcon("Cutlery") {
            moveTo(7f, 3f); lineTo(7f, 21f)
            moveTo(4.5f, 3f); lineTo(4.5f, 8f); curveTo(4.5f, 9.4f, 5.6f, 10.5f, 7f, 10.5f); curveTo(8.4f, 10.5f, 9.5f, 9.4f, 9.5f, 8f); lineTo(9.5f, 3f)
            moveTo(17f, 21f); lineTo(17f, 3f); curveTo(15f, 4f, 14f, 7f, 14f, 10.5f); lineTo(14f, 13f); lineTo(17f, 13f)
        }
    }

    val Clipboard: ImageVector by lazy {
        lineIcon("Clipboard") {
            moveTo(9f, 4.5f); lineTo(6f, 4.5f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 4.5f); lineTo(15f, 4.5f)
            moveTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6.5f); lineTo(9f, 6.5f); close()
            moveTo(9f, 13.5f); lineTo(11f, 15.5f); lineTo(15f, 11f)
        }
    }

    val Trophy: ImageVector by lazy {
        lineIcon("Trophy") {
            moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 9f); curveTo(17f, 11.8f, 14.8f, 14f, 12f, 14f); curveTo(9.2f, 14f, 7f, 11.8f, 7f, 9f); close()
            moveTo(7f, 6f); lineTo(4f, 6f); curveTo(4f, 9f, 5.5f, 10.5f, 7.3f, 10.5f)
            moveTo(17f, 6f); lineTo(20f, 6f); curveTo(20f, 9f, 18.5f, 10.5f, 16.7f, 10.5f)
            moveTo(12f, 14f); lineTo(12f, 17.5f)
            moveTo(8.5f, 20.5f); lineTo(15.5f, 20.5f); lineTo(15f, 17.5f); lineTo(9f, 17.5f); close()
        }
    }

    val Arm: ImageVector by lazy {
        lineIcon("Arm") {
            // Flexed biceps.
            moveTo(4f, 20f); curveTo(4f, 13f, 5f, 8f, 8f, 4.5f); lineTo(11f, 4f); lineTo(11.5f, 6.5f); lineTo(9.5f, 7.5f); lineTo(9f, 12f)
            curveTo(11f, 10f, 15f, 9.5f, 17.5f, 11.5f); curveTo(20.5f, 14f, 19.5f, 20f, 14f, 20f); close()
        }
    }

    val Squat: ImageVector by lazy {
        lineIcon("Squat") {
            circle(13f, 4.5f, 1.8f)
            moveTo(12.5f, 8f); lineTo(10.5f, 13f); lineTo(15f, 14.5f); lineTo(14f, 20f)
            moveTo(10.5f, 13f); lineTo(8f, 17f); lineTo(9f, 20f)
            moveTo(12f, 9.5f); lineTo(17f, 9f)
        }
    }

    /** Full circle as two arcs (PathBuilder has no circle primitive). */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "Fitnex.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()
}
