package com.stalker.iptvplayer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingText: TextView

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        loadingText = findViewById(R.id.loading_text)

        val streamUrl =
            intent.getStringExtra("STREAM_URL") ?: ""

        val title =
            intent.getStringExtra("TITLE") ?: "Player"

        if (streamUrl.isBlank()) {
            Toast.makeText(
                this,
                "Stream URL is empty.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        title.let {
            setTitle(it)
        }

        initializePlayer(streamUrl)
    }

    private fun initializePlayer(streamUrl: String) {

        loadingText.visibility = View.VISIBLE
        loadingText.text = "Loading stream..."

        val preferences =
            getSharedPreferences(
                "IPTV_Prefs",
                MODE_PRIVATE
            )

        val macAddress =
            preferences
                .getString("MAC_ADDRESS", "")
                ?.trim()
                ?.uppercase()
                .orEmpty()

        val portalUrl =
            preferences
                .getString("PORTAL_URL", "")
                ?.trim()
                .orEmpty()

        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(
                    "Mozilla/5.0 (QtEmbedded; U; Linux; C) " +
                            "AppleWebKit/533.3 (KHTML, like Gecko) " +
                            "MAG250 stbapp ver: 2 rev: 250 Safari/533.3"
                )
                .setDefaultRequestProperties(
                    mapOf(
                        "X-User-Agent" to
                                "Model: MAG250; Link: WiFi",

                        "Accept" to
                                "*/*",

                        "X-Requested-With" to
                                "XMLHttpRequest",

                        "X-MAC" to
                                macAddress,

                        "X-Device-MAC" to
                                macAddress,

                        "Cookie" to
                                "mac=$macAddress; stb_lang=en; timezone=GMT",

                        "Referer" to
                                buildReferer(portalUrl)
                    )
                )

        val mediaItem =
            MediaItem.Builder()
                .setUri(streamUrl)
                .apply {

                    if (
                        streamUrl.contains(
                            ".m3u8",
                            ignoreCase = true
                        )
                    ) {
                        setMimeType(
                            MimeTypes.APPLICATION_M3U8
                        )
                    }

                }
                .build()

        val mediaSource =
            createMediaSource(
                mediaItem,
                streamUrl,
                httpDataSourceFactory
            )

        player =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        httpDataSourceFactory
                    )
                )
                .build()
                .also { exoPlayer ->

                    playerView.player =
                        exoPlayer

                    exoPlayer.addListener(
                        object : Player.Listener {

                            override fun onPlaybackStateChanged(
                                playbackState: Int
                            ) {

                                when (playbackState) {

                                    Player.STATE_BUFFERING -> {
                                        loadingText.visibility =
                                            View.VISIBLE

                                        loadingText.text =
                                            "Buffering..."
                                    }

                                    Player.STATE_READY -> {
                                        loadingText.visibility =
                                            View.GONE
                                    }

                                    Player.STATE_ENDED -> {
                                        loadingText.visibility =
                                            View.VISIBLE

                                        loadingText.text =
                                            "Playback ended"
                                    }
                                }
                            }

                            override fun onPlayerError(
                                error: PlaybackException
                            ) {

                                val details =
                                    buildString {

                                        append(
                                            error.errorCodeName
                                        )

                                        if (
                                            !error.message
                                                .isNullOrBlank()
                                        ) {
                                            append(
                                                "\n${error.message}"
                                            )
                                        }

                                        val cause =
                                            error.cause

                                        if (
                                            cause != null &&
                                            !cause.message
                                                .isNullOrBlank()
                                        ) {
                                            append(
                                                "\n${cause.message}"
                                            )
                                        }
                                    }

                                loadingText.visibility =
                                    View.VISIBLE

                                loadingText.text =
                                    "Playback error\n$details"

                                Toast.makeText(
                                    this@PlayerActivity,
                                    details,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )

                    exoPlayer.setMediaSource(
                        mediaSource
                    )

                    exoPlayer.prepare()

                    exoPlayer.playWhenReady =
                        true
                }
    }

    private fun createMediaSource(
        mediaItem: MediaItem,
        streamUrl: String,
        httpDataSourceFactory:
            DefaultHttpDataSource.Factory
    ): MediaSource {

        val isHls =
            streamUrl.contains(
                ".m3u8",
                ignoreCase = true
            ) ||
                    streamUrl.contains(
                        "m3u8",
                        ignoreCase = true
                    )

        return if (isHls) {

            HlsMediaSource.Factory(
                httpDataSourceFactory
            ).createMediaSource(
                mediaItem
            )

        } else {

            DefaultMediaSourceFactory(
                httpDataSourceFactory
            ).createMediaSource(
                mediaItem
            )
        }
    }

    private fun buildReferer(
        portalUrl: String
    ): String {

        return try {

            val uri =
                java.net.URI(portalUrl)

            val scheme =
                uri.scheme ?: "http"

            val authority =
                uri.rawAuthority ?: return "$scheme://"

            "$scheme://$authority/stalker_portal/c/"

        } catch (_: Exception) {

            portalUrl
                .substringBefore(
                    "/stalker_portal"
                )
                .removeSuffix("/") +
                    "/stalker_portal/c/"
        }
    }

    override fun onStop() {
        super.onStop()

        player?.release()
        player = null

        playerView.player = null
    }
}
