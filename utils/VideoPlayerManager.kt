package com.fitnesslemon.app.utils

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.DefaultRenderersFactory

object VideoPlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var isInitialized = false
    private var onBufferingListener: ((Boolean) -> Unit)? = null
    private var onReadyListener: (() -> Unit)? = null

    fun initializePlayer(context: Context, playerView: StyledPlayerView, url: String) {
        try {
            // Создаем новый плеер
            val trackSelector = DefaultTrackSelector(context)
            val renderersFactory = DefaultRenderersFactory(context)

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .build()

            // Настраиваем плеер
            playerView.player = exoPlayer
            playerView.setShowBuffering(StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            playerView.useController = true
            playerView.hideController()
            playerView.controllerAutoShow = false

            // Загружаем медиа
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF

            // Обработка событий
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            onBufferingListener?.invoke(true)
                        }
                        Player.STATE_READY -> {
                            onBufferingListener?.invoke(false)
                            onReadyListener?.invoke()
                        }
                        Player.STATE_ENDED -> {
                            // Перезапускаем видео при окончании
                            exoPlayer?.seekTo(0)
                            exoPlayer?.play()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    onBufferingListener?.invoke(false)
                }
            })

            isInitialized = true

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPlayerListener(
        onBuffering: (Boolean) -> Unit,
        onReady: () -> Unit
    ) {
        onBufferingListener = onBuffering
        onReadyListener = onReady
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun release() {
        try {
            exoPlayer?.release()
            exoPlayer = null
            isInitialized = false
            onBufferingListener = null
            onReadyListener = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }
}