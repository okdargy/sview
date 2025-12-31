package party.dargy.sview.ui.lyrics

import AnimatedBlobBackground
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import party.dargy.sview.R

private const val LYRICS_LEAD_MS = 1000L

@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    cover: Bitmap?,
    lyrics: List<LyricLine>?,
    isLoading: Boolean,
    errorMessage: String?,
    positionMs: Long,
    durationMs: Long,
    connectionError: String?,
    isPlaying: Boolean,
    backgroundColors: List<Color>,
    foregroundColor: Color
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBlobBackground(backgroundColors)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                val contentWidthFraction = 0.8f
                val coverShape = RoundedCornerShape(16.dp)
                val coverShadowColor = backgroundColors.firstOrNull() ?: Color.Black
                Box(
                    modifier = Modifier
                        .fillMaxWidth(contentWidthFraction)
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 14.dp,
                            shape = coverShape,
                            ambientColor = coverShadowColor.copy(alpha = 0.45f),
                            spotColor = coverShadowColor.copy(alpha = 0.6f)
                        )
                        .clip(coverShape)
                        .background(Color.LightGray)
                        .align(Alignment.CenterHorizontally)
                        .weight(1f, false),
                ) {
                    cover?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Cover art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (!isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.pause_24px),
                                contentDescription = "Paused",
                                modifier = Modifier.fillMaxWidth(0.32f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth(contentWidthFraction)
                        .align(Alignment.CenterHorizontally)
                ) {
                    val safeDuration = durationMs.coerceAtLeast(1L)
                    val safePosition = positionMs.coerceIn(0L, safeDuration)
                    RoundedProgressBar(
                        progress = safePosition.toFloat() / safeDuration.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp),
                        color = foregroundColor,
                        trackColor = foregroundColor.copy(alpha = 0.24f)
                    )

                    Text(
                        text = title.ifBlank { "Unknown title" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .padding(bottom = 2.dp),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Clip,
                        color = foregroundColor
                    )

                    Text(
                        text = artist.ifBlank { "Unknown artist" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth(),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Clip,
                        color = foregroundColor.copy(alpha = 0.86f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    connectionError != null -> {
                        Text(
                            text = connectionError,
                            modifier = Modifier.padding(16.dp),
                            color = foregroundColor
                        )
                    }
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp),
                            color = foregroundColor,
                            trackColor = foregroundColor.copy(alpha = 0.22f)
                        )
                    }
                    lyrics?.isNotEmpty() == true -> {
                        SyncedLyrics(
                            lyrics = lyrics,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            offsetMs = LYRICS_LEAD_MS,
                            modifier = Modifier.fillMaxSize(),
                            contentColor = foregroundColor
                        )
                    }
                    else -> {
                        Text(
                            text = errorMessage ?: "No lyrics found",
                            modifier = Modifier.padding(16.dp),
                            color = foregroundColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
}
