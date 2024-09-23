package com.rafdev.mediaplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.rafdev.mediaplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Arrays

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
    private var mProgress: ProgressBar? = null
    private var playBackward: ImageView? = null
    private var playForward: ImageView? = null
    private var mBackgroundPlayer: ImageView? = null
    private var mProgressBarMain: ProgressBar? = null
    private var mSetting: ImageView? = null
    private var adView: AdManagerAdView? = null


    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val backgroundScope = CoroutineScope(Dispatchers.IO)
        backgroundScope.launch {
            MobileAds.initialize(this@MainActivity) {
                Log.d("AdManagerAdView", "MobileAds.initialize $it")
            }
        }
        playerView = binding.playerView
        initUI()
        setPlayerAndPause()
        mProgress = playerView?.findViewById(R.id.progress_player)

        setupTestDevice()
        adView = AdManagerAdView(this).apply {
            adUnitId = "/21775744923/example/adaptive-banner"
            setAdSize(AdSize.BANNER)
        }
        binding.adView.removeAllViews()
        binding.adView.addView(adView)
        val adRequest = AdManagerAdRequest.Builder().build()
        adView!!.loadAd(adRequest)

        adView!!.adListener = object : AdListener() {
            override fun onAdClicked() {
                // Code to be executed when the user clicks on an ad.
            }

            override fun onAdClosed() {
                // Code to be executed when the user is about to return
                // to the app after tapping on an ad.
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("adManagerAdView", "adError: ${adError.message}")
                val deviceId = MobileAds.getRequestConfiguration().testDeviceIds.firstOrNull()
                Log.d("AdManager", "ID de dispositivo de prueba: $deviceId")
            }

            override fun onAdImpression() {
                // Code to be executed when an impression is recorded
                // for an ad.
            }

            override fun onAdLoaded() {
                    // Obtén el ID del dispositivo
                    val deviceId = MobileAds.getRequestConfiguration().testDeviceIds.firstOrNull()
                    Log.d("AdManager", "ID de dispositivo de prueba: $deviceId")

                Log.d("adManagerAdView", "El anuncio se ha cargado correctamente.")
            }

            override fun onAdOpened() {
                // Code to be executed when an ad opens an overlay that
                // covers the screen.
            }
        }
    }

    private fun setupTestDevice() {

        val testDeviceIds = Arrays.asList("65C03ADB725622F697AF793700E8B55D")
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)
    }
    private fun initUI() {

        val trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        player?.addListener(playerEventListener())
        playerView?.player = player
        backgroundPlayer()

        val mediaItem = MediaItem.fromUri(VIDEO_URI)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        setupTimeBar()
        startUpdatingTimeBar()
    }


    private fun showQualitySelectionDialog() {
        val trackSelector = player?.trackSelector as DefaultTrackSelector
        val availableTracks = trackSelector.currentMappedTrackInfo

        val qualities = mutableListOf<Pair<Int, String?>>() // Par de altura y calidad
        availableTracks?.let { trackInfo ->
            for (rendererIndex in 0 until trackInfo.rendererCount) {
                val trackGroups = trackInfo.getTrackGroups(rendererIndex)
                for (groupIndex in 0 until trackGroups.length) {
                    val trackGroup = trackGroups.get(groupIndex)
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getFormat(trackIndex)
                        val height = format.height
                        if (height > 0) { // Solo agregar si hay altura válida
                            qualities.add(height to "${height}p")
                        } else if (format.label != null && format.label != "Unknown Quality") {
                            qualities.add(0 to format.label) // Añadir si tiene etiqueta válida
                        }
                    }
                }
            }
        }

        // Ordenar por altura
        val sortedQualities = qualities
            .filter { it.first > 0 } // Filtra solo calidades válidas
            .sortedBy { it.first } // Ordenar por altura
            .map { it.second } // Mapear solo a los nombres

        AlertDialog.Builder(this)
            .setTitle("Select Quality")
            .setItems(sortedQualities.toTypedArray()) { dialog, which ->
                val selectedQuality = sortedQualities[which]
                changeVideoQuality(selectedQuality)
            }
            .show()
        Log.d("PlayerListener", "Qualities: $sortedQualities")
    }

    private fun changeVideoQuality(selectedQuality: String?) {
        val trackSelector = player?.trackSelector as DefaultTrackSelector
        val availableTracks = trackSelector.currentMappedTrackInfo

        availableTracks?.let { trackInfo ->
            for (rendererIndex in 0 until trackInfo.rendererCount) {
                val trackGroups = trackInfo.getTrackGroups(rendererIndex)
                for (groupIndex in 0 until trackGroups.length) {
                    val trackGroup = trackGroups.get(groupIndex)
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getFormat(trackIndex)
                        // Verificar si la calidad seleccionada coincide
                        if (selectedQuality == "${format.height}p") {
                            val parameters = trackSelector.parameters.buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(trackGroup, trackIndex)
                                )
                                .build()

                            trackSelector.setParameters(parameters)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun backgroundPlayer() {
        mBackgroundPlayer = binding.backgroundPlayer
        mProgressBarMain = binding.progressBarMain
        mBackgroundPlayer?.visibility = View.VISIBLE
        mProgressBarMain?.visibility = View.VISIBLE
        mBackgroundPlayer?.setImageResource(R.drawable.background_player)
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
        mSetting = playerView?.findViewById(R.id.exo_settings_player)

        mSetting?.setOnClickListener {
            showQualitySelectionDialog()
        }

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

    private fun playerEventListener(): Player.Listener {
        return object : Player.Listener {

            override fun onRenderedFirstFrame() {
                super.onRenderedFirstFrame()
                mBackgroundPlayer?.visibility = View.GONE
                mProgressBarMain?.visibility = View.GONE
            }

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
        adView?.destroy()
        Log.e("PlayerListener", "onDestroy")
        player?.release()
        stopUpdatingTimeBar()
    }

}