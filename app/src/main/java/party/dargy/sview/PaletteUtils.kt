package party.dargy.sview

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

val DefaultBackgroundColors = listOf(
    Color(0xFF0E1217),
    Color(0xFF16202A),
    Color(0xFF243445),
    Color(0xFF3A536C),
    Color(0xFF6582A4)
)

val DefaultForeground = Color(0xFFF7F7F7)

fun extractPaletteColors(bitmap: Bitmap): List<Color> {
    val palette = Palette.from(bitmap).clearFilters().generate()
    val swatches = listOfNotNull(
        palette.dominantSwatch,
        palette.vibrantSwatch,
        palette.darkVibrantSwatch,
        palette.lightVibrantSwatch,
        palette.mutedSwatch,
        palette.darkMutedSwatch,
        palette.lightMutedSwatch
    ).distinctBy { it.rgb }
        .sortedByDescending { it.population }

    val picked = swatches.take(5).map { Color(it.rgb) }
    return when {
        picked.size >= 5 -> picked
        picked.isNotEmpty() -> picked + DefaultBackgroundColors.take(5 - picked.size)
        else -> DefaultBackgroundColors
    }
}

fun pickForeground(bgColors: List<Color>): Color {
    val colors = bgColors.takeIf { it.isNotEmpty() } ?: DefaultBackgroundColors
    val avgLum = colors.map { it.luminanceEstimate() }.average().toFloat()
    return if (avgLum < 0.6f) Color(0xFFF7F7F7) else Color(0xFF0D0D0D)
}

fun Color.luminanceEstimate(): Float {
    val r = red
    val g = green
    val b = blue
    return (0.299f * r + 0.587f * g + 0.114f * b)
}
