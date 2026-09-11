package com.stalker.iptvplayer

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

        val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        val title = intent.getStringExtra("TITLE") ?: "Player"

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

        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->

                playerView.player = exoPlayer

                exoPlayer.addListener(
                    object : Player.Listener {

                        override fun onPlaybackStateChanged(
                            playbackState: Int
                        ) {
                            when (playbackState) {

                                Player.STATE_BUFFERING -> {
                                    loadingText.visibility = View.VISIBLE
                                    loadingText.text = "Buffering..."
                                }

                                Player.STATE_READY -> {
                                    loadingText.visibility = View.GONE
                                }

                                Player.STATE_ENDED -> {
                                    loadingText.visibility = View.VISIBLE
                                    loadingText.text = "Playback ended"
                                }
                            }
                        }

                        override fun onPlayerError(
                            error: PlaybackException
                        ) {
                            loadingText.visibility = View.VISIBLE
                            loadingText.text =
                                "Playback error"

                            Toast.makeText(
                                this@PlayerActivity,
                                "Playback error: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )

                val mediaItem = MediaItem.fromUri(
                    Uri.parse(streamUrl)
                )

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    override fun onStop() {
        super.onStop()

        player?.release()
        player = null
        playerView.player = null
    }
}
