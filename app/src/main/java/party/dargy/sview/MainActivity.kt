package party.dargy.sview

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import party.dargy.sview.ui.lyrics.LyricLine
import party.dargy.sview.ui.lyrics.LyricsScreen
import party.dargy.sview.ui.theme.SviewTheme

class MainActivity : ComponentActivity() {
    private val CLIENT_ID = "d78a9e1cabc148c38ce2176c2914bc7b"
    private val REDIRECT_URI = "https://party.dargy.sview/callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var isConnecting = false
    private var connectAttempts = 0
    private var connectError: String? by mutableStateOf(null)
    private var trackInfo by mutableStateOf(TrackInfo(title = "Title", artist = "Artist", cover = null))
    private var backgroundColors by mutableStateOf(DefaultBackgroundColors)
    private var foregroundColor by mutableStateOf(DefaultForeground)
    private var lyrics: List<LyricLine>? by mutableStateOf(null)
    private var isLyricsLoading by mutableStateOf(false)
    private var lyricsError: String? by mutableStateOf(null)
    private var lastLyricsSignature: String? = null
    private var lyricsFetchSignature: String? = null
    private var playbackPositionMs by mutableStateOf(0L)
    private var isPlaying by mutableStateOf(false)
    private var trackDurationMs by mutableStateOf(0L)
    private var positionAnchorMs: Long = 0L
    private var positionAnchorTime: Long = 0L
    private var positionTicker: Job? = null
    private var lyricsFetchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent {
            SviewTheme {
                val track = trackInfo
                LyricsScreen(
                    title = track.title,
                    artist = track.artist,
                    cover = track.cover,
                    lyrics = lyrics,
                    isLoading = isLyricsLoading,
                    errorMessage = lyricsError,
                    positionMs = playbackPositionMs,
                    durationMs = trackDurationMs,
                    connectionError = connectError,
                    isPlaying = isPlaying,
                    backgroundColors = backgroundColors,
                    foregroundColor = foregroundColor
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectToSpotify()
    }

    private fun connectToSpotify(delayMs: Long = 0) {
        if (isConnecting) return
        isConnecting = true
        connectAttempts += 1
        lifecycleScope.launch(Dispatchers.Main) {
            if (delayMs > 0) delay(delayMs)

            val connectionParams = ConnectionParams.Builder(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
                .showAuthView(true)
                .build()

            SpotifyAppRemote.connect(this@MainActivity, connectionParams,
                object : Connector.ConnectionListener {
                    override fun onConnected(remote: SpotifyAppRemote) {
                        isConnecting = false
                        connectError = null
                        connectAttempts = 0
                        spotifyAppRemote = remote
                        with(this@MainActivity) {
                            Toast.makeText(this, "Connected to Spotify app", Toast.LENGTH_SHORT).show()
                        }
                        connected()
                    }

                    override fun onFailure(throwable: Throwable) {
                        isConnecting = false
                        if (connectAttempts >= 3) {
                            connectError = "Spotify connection failed after retries"
                        }
                        if (throwable is SpotifyConnectionTerminatedException) {
                            Log.w("MainActivity", "Spotify connection terminated, retrying...", throwable)
                            if (connectAttempts < 3) connectToSpotify(delayMs = 1000)
                            return
                        }
                        Toast.makeText(
                            this@MainActivity,
                            "Error connecting to Spotify",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("MainActivity", "Error connecting to Spotify", throwable)
                    }
                })
        }
    }

    private fun connected() {
        spotifyAppRemote?.let {
            it.playerApi.playerState.setResultCallback { state ->
                Log.i("InitState", state.toString());
                handlePlayerState(state)
            }

            it.playerApi.subscribeToPlayerState().setEventCallback { playerState ->
                handlePlayerState(playerState)
            }
        }
    }

    private fun handlePlayerState(state: com.spotify.protocol.types.PlayerState) {
        Log.i("StateChange", state.toString());
        val track = state.track ?: return
        positionAnchorMs = state.playbackPosition
        positionAnchorTime = SystemClock.elapsedRealtime()
        isPlaying = !state.isPaused
        trackDurationMs = state.track.duration
        playbackPositionMs = positionAnchorMs
        startPositionTicker()

        fetchCoverAndUpdate(track.name, track.artist?.name, track.imageUri?.raw)
        fetchLyricsAsync(
            title = track.name,
            artist = track.artist?.name,
            album = track.album?.name,
            durationMs = track.duration
        )
    }

    private fun fetchCoverAndUpdate(title: String?, artist: String?, imageUriRaw: String?) {
        val remote = spotifyAppRemote ?: return
        if (imageUriRaw == null) {
            updateTrackInfo(title, artist, null)
            return
        }
        remote.imagesApi.getImage(com.spotify.protocol.types.ImageUri(imageUriRaw))
            .setResultCallback { bitmap ->
                lifecycleScope.launch(Dispatchers.Default) {
                    val colors = extractPaletteColors(bitmap)
                    val textColor = pickForeground(colors)
                    withContext(Dispatchers.Main) {
                        backgroundColors = colors
                        foregroundColor = textColor
                        updateTrackInfo(title, artist, bitmap)
                    }
                }
            }
            .setErrorCallback {
                backgroundColors = DefaultBackgroundColors
                foregroundColor = DefaultForeground
                updateTrackInfo(title, artist, null)
            }
    }

    private fun updateTrackInfo(title: String?, artist: String?, cover: Bitmap?) {
        runOnUiThread {
            trackInfo = TrackInfo(
                title = title.orEmpty(),
                artist = artist.orEmpty(),
                cover = cover
            )
        }
    }

    private fun startPositionTicker() {
        positionTicker?.cancel()
        positionTicker = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - positionAnchorTime
                val raw = if (isPlaying) positionAnchorMs + elapsed else positionAnchorMs
                playbackPositionMs = raw.coerceAtMost(trackDurationMs)
                delay(120)
            }
        }
    }

    private fun fetchLyricsAsync(title: String?, artist: String?, album: String?, durationMs: Long) {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return
        val safeArtist = artist.orEmpty()
        val safeAlbum = album.orEmpty()
        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
        val signature = listOf(safeTitle, safeArtist, safeAlbum, durationSec.toString()).joinToString("|")
        if (signature == lastLyricsSignature && lyrics != null) return
        if (lyricsFetchJob?.isActive == true && signature == lyricsFetchSignature) return

        if (signature != lyricsFetchSignature) {
            lyricsFetchJob?.cancel()
        }

        isLyricsLoading = true
        lyricsError = null
        lyricsFetchSignature = signature

        lyricsFetchJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                fetchLyricsFromLrclib(
                    trackName = safeTitle,
                    artistName = safeArtist,
                    albumName = safeAlbum,
                    durationSeconds = durationSec
                )
            }

            withContext(Dispatchers.Main) {
                isLyricsLoading = false
                result.onSuccess { fetched ->
                    lyrics = fetched.takeIf { it.isNotEmpty() }
                    lyricsError = null
                    lastLyricsSignature = signature
                }.onFailure { err ->
                    lyrics = null
                    lyricsError = err.message
                    lastLyricsSignature = null
                }
                lyricsFetchSignature = null
                lyricsFetchJob = null
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        isConnecting = false
        connectAttempts = 0
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        positionTicker?.cancel()
    }
}
