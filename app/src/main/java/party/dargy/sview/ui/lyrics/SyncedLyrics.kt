package party.dargy.sview.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import party.dargy.sview.ui.theme.LyricsFontFamily

data class LyricLine(val timestampMs: Int, val text: String)

private data class RenderLine(val line: LyricLine, val showNote: Boolean, val selectable: Boolean)

fun parseLrc(raw: String): List<LyricLine> {
    return raw.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trimEnd()
            if (trimmed.isEmpty()) return@mapNotNull null
            val closingBracket = trimmed.indexOf(']')
            if (!trimmed.startsWith('[') || closingBracket <= 1) return@mapNotNull null
            val tsPart = trimmed.substring(1, closingBracket)
            val textPart = trimmed.substring(closingBracket + 1).trimStart()
            val tsMs = parseTimestampMs(tsPart) ?: return@mapNotNull null
            LyricLine(timestampMs = tsMs, text = textPart)
        }
        .sortedBy { it.timestampMs }
        .toList()
}

private fun parseTimestampMs(raw: String): Int? {
    val parts = raw.split(":", limit = 2)
    if (parts.size != 2) return null
    val minutes = parts[0].toIntOrNull() ?: return null
    val secondsParts = parts[1].split('.')
    val seconds = secondsParts.getOrNull(0)?.toIntOrNull() ?: return null
    val hundredths = secondsParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toIntOrNull() ?: 0
    return minutes * 60_000 + seconds * 1_000 + hundredths * 10
}

@Composable
fun SyncedLyrics(
    lyrics: List<LyricLine>,
    positionMs: Long,
    durationMs: Long,
    offsetMs: Long = 0L,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onBackground
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val effectivePosition = (positionMs + offsetMs).coerceAtMost(safeDuration)
    val renderLines = remember(lyrics) {
        lyrics.mapIndexed { index, line ->
            val isBlank = line.text.isBlank()
            val nextTs = lyrics.getOrNull(index + 1)?.timestampMs
            val gap = nextTs?.minus(line.timestampMs)
            val atEnds = index == 0 || index == lyrics.lastIndex
            val showNote = isBlank && !atEnds && gap != null && gap > 10_000
            val selectable = !isBlank || showNote
            RenderLine(line = line, showNote = showNote, selectable = selectable)
        }
    }

    val listState = rememberLazyListState()
    val lastTimedIndex = renderLines.indexOfLast { effectivePosition >= it.line.timestampMs }
    val activeIndex = if (lastTimedIndex >= 0 && renderLines[lastTimedIndex].selectable) lastTimedIndex else -1
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 12.dp)
    ) {
        items(renderLines.size) { index ->
            val renderLine = renderLines[index]
            val isActive = index == activeIndex
            val targetAlpha = when {
                isActive -> 1f
                renderLine.selectable -> 0.55f
                else -> 0.4f
            }
            val animatedAlpha = remember { Animatable(targetAlpha) }
            LaunchedEffect(targetAlpha) {
                animatedAlpha.animateTo(targetAlpha, animationSpec = tween(durationMillis = 200))
            }

            val displayText = when {
                renderLine.line.text.isNotBlank() -> renderLine.line.text
                renderLine.showNote -> "\u266A"
                else -> ""
            }

            Surface(
                tonalElevation = if (isActive) 8.dp else 0.dp,
                color = Color.Transparent
            ) {
                Text(
                    text = displayText,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    fontSize = 22.sp,
                    fontFamily = LyricsFontFamily,
                    fontWeight = if (isActive && renderLine.selectable) FontWeight.Bold else FontWeight.SemiBold,
                    fontStyle = if (renderLine.line.text.isBlank()) FontStyle.Italic else FontStyle.Normal,
                    color = contentColor.copy(alpha = animatedAlpha.value)
                )
            }
        }
    }
}
