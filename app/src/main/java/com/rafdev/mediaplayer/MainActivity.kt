package com.rafdev.mediaplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.rafdev.mediaplayer.databinding.ActivityMainBinding

@UnstableApi
class MainActivity : AppCompatActivity() {

    companion object {
        private const val VIDEO_URI =
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    }

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var defaultTimeBar: DefaultTimeBar? = null
    private var mPlayPausePlayer: ImageView? = null
    private var mProgress:ProgressBar? = null
    private var playBackward: ImageView? = null
    private var playForward: ImageView? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()
        setPlayerAndPause()
        mProgress = playerView?.findViewById(R.id.progress_player)

    }

    private fun initUI() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(PlayerEventListener())
        playerView = binding.playerView

        playerView?.player = player

        val mediaItem = MediaItem.fromUri(VIDEO_URI)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
        setupTimeBar()

        startUpdatingTimeBar()
    }

    private fun startUpdatingTimeBar() {
        handler.post(object : Runnable {
            override fun run() {
                updateTimeBar()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun setupTimeBar() {
        defaultTimeBar = playerView?.findViewById(R.id.default_time_bar)
        defaultTimeBar?.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(p0: TimeBar, p1: Long) {
            }

            override fun onScrubMove(p0: TimeBar, p1: Long) {
            }

            override fun onScrubStop(p0: TimeBar, p1: Long, p2: Boolean) {
                player?.seekTo(p1)
                if (p2) {
                    player?.play()
                }
            }
        })
    }


    private fun updateTimeBar() {
        val currentPosition = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L
        val bufferedPosition = player?.bufferedPosition ?: 0L

        defaultTimeBar?.setPosition(currentPosition)
        defaultTimeBar?.setBufferedPosition(bufferedPosition)
        defaultTimeBar?.setDuration(duration)

    }

    private fun setPlayerAndPause() {
        mPlayPausePlayer = playerView?.findViewById(R.id.play_pause_player)
        playBackward = playerView?.findViewById(R.id.play_backward)
        playForward = playerView?.findViewById(R.id.play_forward)

        mPlayPausePlayer?.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                player?.play()
            }
        }

        playBackward?.setOnClickListener {
            Log.d("PlayerListener", "playBackward")
            player?.seekTo((player?.currentPosition ?: 0L) - 15000)
        }

        playForward?.setOnClickListener {
            Log.d("PlayerListener", "forward")
            player?.seekTo((player?.currentPosition ?: 0L) + 15000)
        }
    }

    private fun PlayerEventListener(): Player.Listener {
        return object : Player.Listener {


            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        mProgress?.visibility = View.VISIBLE
                        mPlayPausePlayer?.visibility = View.INVISIBLE
                        Log.d("PlayerListener", "Buffering...")
                    }

                    Player.STATE_READY -> {
                        mProgress?.visibility = View.GONE
                        mPlayPausePlayer?.visibility = View.VISIBLE
                        Log.d("PlayerListener", "Ready to play")
                    }

                    Player.STATE_ENDED -> {
                        Log.d("PlayerListener", "Playback ended")
                        stopUpdatingTimeBar()
                    }

                    Player.STATE_IDLE -> {
                        Log.d("PlayerListener", "Idle")
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    mPlayPausePlayer?.setImageResource(R.drawable.ic_pause)
                    Log.d("PlayerListener", "Playing")
                } else {
                    Log.d("PlayerListener", "Paused")
                    mPlayPausePlayer?.setImageResource(R.drawable.ic_play)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerListener", "Playback error: ${error.message}")
            }
        }
    }

    private fun stopUpdatingTimeBar() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        Log.e("PlayerListener", "onResume")
        if (player == null) {
            Log.e("PlayerListener", "onResume play")
            initUI()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.e("PlayerListener", "onPause")
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        Log.e("PlayerListener", "onStop")
        stopUpdatingTimeBar()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("PlayerListener", "onDestroy")
        player?.release()
        stopUpdatingTimeBar()
    }

}