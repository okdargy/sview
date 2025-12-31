package party.dargy.sview

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import party.dargy.sview.ui.lyrics.LyricLine
import party.dargy.sview.ui.lyrics.parseLrc

fun fetchLyricsFromLrclib(
    trackName: String,
    artistName: String,
    albumName: String,
    durationSeconds: Int
): List<LyricLine> {
    val base = "https://lrclib.net/api/get"
    val query = "track_name=${trackName.urlEncode()}&artist_name=${artistName.urlEncode()}&album_name=${albumName.urlEncode()}&duration=$durationSeconds"
    val url = URL("$base?$query")
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "sview-demo/0.1")

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        stream.bufferedReader().use { reader ->
            val body = reader.readText()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("We couldn't find lyrics to this song... (${connection.responseCode})")
            }
            val json = JSONObject(body)
            val synced = json.optString("syncedLyrics", "")
            if (synced.isNotBlank()) return parseLrc(synced)

            val plain = json.optString("plainLyrics", "")
            if (plain.isNotBlank()) {
                return plain.lines().filter { it.isNotBlank() }.mapIndexed { idx, line ->
                    LyricLine(timestampMs = idx * 2000, text = line)
                }
            }
        }
    } finally {
        connection.disconnect()
    }

    return emptyList()
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
