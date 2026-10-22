package com.heru.prototype.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

class ClipPlayer(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    var onInterrupted: () -> Unit = {}
    var onError: (String) -> Unit = {}

    fun play(asset: String, onComplete: () -> Unit) {
        stop()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    stop()
                    onInterrupted()
                }
            }, handler).build()
        focus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stop()
            onError("다른 소리가 재생 중이에요. 잠시 후 다시 시도해 주세요.")
            return
        }
        try {
            val clip = MediaPlayer()
            player = clip
            clip.setAudioAttributes(attributes)
            context.assets.openFd(asset).use { fd ->
                clip.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            clip.setOnPreparedListener {
                if (player === it) {
                    it.start()
                }
            }
            clip.setOnCompletionListener {
                if (player === it) { stop(); onComplete() }
            }
            clip.setOnErrorListener { _, _, _ ->
                stop(); onError("음성을 재생하지 못했어요. 종료 후 다시 시도해 주세요."); true
            }
            clip.prepareAsync()
        } catch (_: Exception) {
            stop(); onError("음성 파일을 열지 못했어요. 앱을 다시 실행해 주세요.")
        }
    }
    fun stop() {
        player?.apply {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            release()
        }
        player = null
        focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
