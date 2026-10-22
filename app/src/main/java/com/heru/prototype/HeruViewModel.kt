package com.heru.prototype

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heru.prototype.audio.ClipPlayer
import com.heru.prototype.data.LocalStore
import com.heru.prototype.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class HeruViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val player = ClipPlayer(app)
    var level by mutableIntStateOf(store.level()); private set
    var guardian by mutableStateOf(store.guardian()); private set
    var previewing by mutableStateOf(false); private set
    var session by mutableStateOf<CallSession?>(null); private set
    var praiseLevel by mutableIntStateOf(3); private set
    var praisePlayed by mutableStateOf(false); private set
    var countdownSeconds by mutableIntStateOf(3); private set
    var stars by mutableIntStateOf(store.stars()); private set
    var records by mutableStateOf(store.records()); private set
    var history by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    private var countdownJob: Job? = null
    val sessionScript: String get() {
        val current = session ?: return ""
        return when {
            current.phase == Phase.CLOSING -> ScriptCatalog.finish
            current.phase == Phase.PRAISE_SPEAKING || current.phase == Phase.SUCCESS_WAITING -> ScriptCatalog.followUp(praiseLevel)
            current.followUpUsed -> ScriptCatalog.main(current.level)
            else -> ScriptCatalog.main(current.level, current.guardian)
        }
    }
    init {
        player.onInterrupted = {
            previewing = false
            session = session?.stop()
            message = "다른 소리로 재생을 멈췄어요. 자동으로 다시 재생하지 않아요."
        }
        player.onError = {
            previewing = false; session = session?.stop(); message = it
        }
    }
    fun chooseLevel(value: Int) {
        if (session != null) return
        player.stop(); previewing = false
        level = value.coerceIn(1, 5); store.selection(level, guardian)
    }
    fun chooseGuardian(value: Guardian) {
        if (session != null) return
        player.stop(); previewing = false
        guardian = value; store.selection(level, guardian)
    }
    fun preview() {
        if (session != null) return
        if (previewing) { player.stop(); previewing = false; return }
        message = null; previewing = true
        player.play(ScriptCatalog.mainAsset(level)) { previewing = false }
    }
    fun start() {
        if (session != null) return
        player.stop(); previewing = false; message = null
        countdownJob?.cancel()
        countdownSeconds = 3
        session = CallSession(UUID.randomUUID().toString(), level, guardian, System.currentTimeMillis(), Phase.COUNTDOWN)
        countdownJob = viewModelScope.launch {
            for (second in 3 downTo 1) {
                countdownSeconds = second
                delay(1000)
            }
            val current = session ?: return@launch
            session = current.startSpeaking()
            player.play(ScriptCatalog.mainAsset(level)) {
                session = session?.speechFinished()
            }
        }
    }
    fun followUp() {
        val current = session ?: return
        val next = current.followUp()
        if (next == current) return
        session = next
        player.play(ScriptCatalog.mainAsset(current.level)) {
            session = session?.speechFinished()
        }
    }
    fun finish() {
        val current = session ?: return
        val next = current.finish()
        if (next == current) return
        session = next
        praisePlayed = false
        player.stop()
    }
    fun choosePraiseLevel(value: Int) {
        if (session?.phase != Phase.PRAISE_READY) return
        player.stop()
        praiseLevel = value.coerceIn(1, 5)
        praisePlayed = false
    }
    fun playPraise() {
        val current = session ?: return
        if (current.phase != Phase.PRAISE_READY) return
        countdownJob?.cancel()
        countdownSeconds = 3
        session = current.startPraiseCountdown()
        countdownJob = viewModelScope.launch {
            for (second in 3 downTo 1) {
                countdownSeconds = second
                delay(1000)
            }
            val ready = session ?: return@launch
            session = ready.startPraiseSpeaking()
            player.play(ScriptCatalog.followUpAsset(praiseLevel)) {
                praisePlayed = true
                session = session?.speechFinished()
            }
        }
    }
    fun complete() {
        val current = session ?: return
        if (!praisePlayed) return
        val next = current.complete()
        if (next == current) return
        session = next
        player.play("audio/finish.wav") { session = session?.speechFinished() }
    }
    fun stop() {
        countdownJob?.cancel(); countdownJob = null
        player.stop(); previewing = false; session = session?.stop()
    }
    fun hostStopped() {
        if (previewing || session?.phase in listOf(Phase.COUNTDOWN, Phase.SPEAKING, Phase.WAITING, Phase.PRAISE_READY, Phase.PRAISE_COUNTDOWN, Phase.PRAISE_SPEAKING, Phase.SUCCESS_WAITING, Phase.CLOSING, Phase.STAR_REWARD)) {
            stop()
        }
    }
    fun awardStar() {
        val current = session ?: return
        if (current.phase != Phase.STAR_REWARD) return
        stars = store.addStar()
        session = current.awardStar()
    }
    fun save(outcome: Outcome) {
        val current = session ?: return
        if (current.phase != Phase.RESULT) return
        val record = SessionRecord(current.id, current.startedAt, current.level, current.guardian, outcome)
        if (store.append(record)) {
            session = current.saved()
            records = store.records()
            message = null
        } else message = "기록을 저장하지 못했어요. 다시 시도해 주세요."
    }
    fun home() { countdownJob?.cancel(); player.stop(); previewing = false; session = null; history = false; message = null }
    fun showHistory() { player.stop(); previewing = false; history = true; message = null }
    fun clearHistory() {
        if (store.clear()) records = emptyList()
        else message = "기록을 지우지 못했어요. 다시 시도해 주세요."
    }
    override fun onCleared() { player.stop() }
}
