import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

private val defaultBgColors = listOf( Color(0xFF0E1217), Color(0xFF16202A), Color(0xFF243445), Color(0xFF3A536C), Color(0xFF6582A4), )

@Composable
fun AnimatedBlobBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    blobAlpha: Float = 0.55f,
    softness: Float = 1.15f, // >1 = softer edge (bigger fade)
) {
    val c = if (colors.isNotEmpty()) colors else defaultBgColors

    val time = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            // Keep drifting forward without a reset to avoid a visible loop seam
            time.animateTo(
                targetValue = time.value + 1f,
                animationSpec = tween(durationMillis = 30_000, easing = LinearEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val base = Brush.linearGradient(
                    colors = listOf(c[0], c.getOrElse(1) { c[0] }, c.getOrElse(2) { c[0] }),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )

                fun center(phase01: Float, ax: Float, ay: Float, ox: Float, oy: Float): Offset {
                    val a = (phase01 * (Math.PI * 2)).toFloat()
                    return Offset(
                        x = size.width * (ox + ax * (0.5f + 0.5f * cos(a))),
                        y = size.height * (oy + ay * (0.5f + 0.5f * sin(a)))
                    )
                }

                val r1 = size.minDimension * 0.55f
                val r2 = size.minDimension * 0.45f
                val r3 = size.minDimension * 0.50f

                val col1 = c[0].copy(alpha = blobAlpha)
                val col2 = c.getOrElse(1) { c[0] }.copy(alpha = blobAlpha)
                val col3 = c.getOrElse(2) { c[0] }.copy(alpha = blobAlpha)

                fun blobBrush(color: Color, center: Offset, radius: Float) =
                    Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = center,
                        radius = radius * softness
                    )

                onDrawBehind {
                    drawRect(base)

                    // Different speeds derived from one clock
                    val t = time.value
                    val o1 = center(t * 1.00f, ax = 0.65f, ay = 0.55f, ox = 0.05f, oy = 0.05f)
                    val o2 = center(t * 0.78f, ax = 0.60f, ay = 0.60f, ox = 0.15f, oy = 0.10f)
                    val o3 = center(t * 0.62f, ax = 0.70f, ay = 0.50f, ox = 0.00f, oy = 0.20f)

                    // Soft-looking blobs (GPU) — no real blur needed
                    drawCircle(brush = blobBrush(col1, o1, r1), radius = r1 * softness, center = o1)
                    drawCircle(brush = blobBrush(col2, o2, r2), radius = r2 * softness, center = o2)
                    drawCircle(brush = blobBrush(col3, o3, r3), radius = r3 * softness, center = o3)
                }
            }
    )
}
