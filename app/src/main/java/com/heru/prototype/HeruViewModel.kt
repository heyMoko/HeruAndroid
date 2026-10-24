package com.heru.prototype

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heru.prototype.audio.ClipPlayer
import com.heru.prototype.data.*
import com.heru.prototype.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class HeruViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = LocalStore(app)
    private val repository = FamilyRepository(app)
    private val writes = Mutex()
    private val player = ClipPlayer(app)
    private var snapshot by mutableStateOf<FamilySnapshot?>(null)
    var loading by mutableStateOf(true); private set
    var loadFailed by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var tab by mutableIntStateOf(0); private set
    var preparingCall by mutableStateOf(false); private set
    var today by mutableStateOf(LocalDate.now()); private set
    var level by mutableIntStateOf(settings.level()); private set
    var guardian by mutableStateOf(settings.guardian()); private set
    var routine by mutableStateOf(settings.routine()); private set
    var selfViewEnabled by mutableStateOf(settings.selfViewEnabled()); private set
    var session by mutableStateOf<CallSession?>(null); private set
    var previewing by mutableStateOf(false); private set
    var countdownSeconds by mutableIntStateOf(3); private set
    val children get() = snapshot?.children.orEmpty()
    val activeChild get() = children.firstOrNull { it.id == snapshot?.activeChildId }
        ?: ChildProfile(DEFAULT_CHILD_ID, "우리 아이")
    val pending get() = snapshot?.activities.orEmpty()
    val records get() = snapshot?.records.orEmpty()
    val stars get() = snapshot?.stars ?: 0
    val goal get() = snapshot?.goal ?: RewardGoal("preview")
    val rewardTitle get() = goal.title
    val rewardImageUri get() = goal.imageUri
    val rewardTarget get() = goal.target
    val goalReached get() = goal.celebrationPending
    val praiseLevel get() = session?.praiseLevel ?: 3
    val sessionScript: String get() {
        val current = session ?: return ""
        return when (current.phase) {
            Phase.GREETING -> "여보세요."
            Phase.CLOSING -> ScriptCatalog.finish(current.routine)
            Phase.PRAISE_SPEAKING, Phase.SUCCESS_WAITING -> ScriptCatalog.followUp(current.praiseLevel, current.routine)
            else -> ScriptCatalog.main(current.followUpLevel ?: current.level, current.guardian, current.routine)
        }
    }
    private var foreground = true
    private var presentationGeneration = 0L
    private var countdownJob: Job? = null
    private var greetingJob: Job? = null
    private var previewJob: Job? = null
    private var scriptJob: Job? = null
    private var greetingTts: TextToSpeech? = null

    init {
        player.onInterrupted = { playbackIssue("다른 소리로 잠시 멈췄어요. 홈에서 이어갈 수 있어요.") }
        player.onError = { playbackIssue(it) }
        load()
        viewModelScope.launch { while (isActive) { delay(60_000); today = LocalDate.now() } }
    }

    fun load() {
        loading = true; loadFailed = false
        viewModelScope.launch {
            try { writes.withLock { snapshot = repository.initialize() } }
            catch (_: Exception) { loadFailed = true; message = "저장된 내용을 열지 못했어요. 다시 시도해 주세요." }
            finally { loading = false }
        }
    }
    private fun operate(errorMessage: String = "저장하지 못했어요. 내용을 유지하고 있으니 다시 시도해 주세요.", block: suspend () -> Unit) {
        if (busy || loading) return
        busy = true
        viewModelScope.launch {
            try { writes.withLock { block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = errorMessage }
            finally { busy = false }
        }
    }
    private fun move(next: CallSession, then: () -> Unit = {}) {
        val expectedGeneration = presentationGeneration
        operate {
            repository.saveActivity(next)
            snapshot = repository.reload()
            if (foreground && presentationGeneration == expectedGeneration) { session = next; then() }
        }
    }
    fun dismissMessage() { message = null }
    fun selectTab(value: Int) { if (!busy) { stopPreview(); preparingCall = false; tab = value.coerceIn(0, 2) } }
    fun selectChild(id: String) { if (session == null) operate { snapshot = repository.selectChild(id); preparingCall = false } }
    fun saveChild(id: String?, name: String, plans: Map<Routine, Int>, onSaved: () -> Unit) {
        val existing = children.firstOrNull { it.id == id }
        operate {
            snapshot = repository.saveChild(ChildProfile(id ?: UUID.randomUUID().toString(), name.trim(), plans,
                existing?.createdAt ?: System.currentTimeMillis()))
            onSaved()
        }
    }
    fun saveReward(title: String, imageUri: String?, target: Int, newGoal: Boolean, onSaved: () -> Unit) = operate {
        snapshot = repository.saveGoal(activeChild.id, title.trim(), imageUri, target, newGoal)
        onSaved()
    }
    fun importRewardPhoto(uri: String, onImported: (String) -> Unit) = operate("사진을 열지 못했어요. 다른 사진을 선택해 주세요.") {
        onImported(importRewardImage(getApplication(), uri))
    }
    fun dismissGoalReached() = operate { snapshot = repository.acknowledgeGoal(activeChild.id) }

    fun chooseLevel(value: Int) {
        if (session != null || busy) return
        stopPreview(); level = value.coerceIn(1, 5); settings.selection(level, guardian, routine)
    }
    fun chooseGuardian(value: Guardian) {
        if (session != null || busy) return
        stopPreview(); guardian = value; settings.selection(level, guardian, routine)
    }
    fun chooseRoutine(value: Routine) {
        if (session != null || busy) return
        stopPreview(); routine = value; settings.selection(level, guardian, routine)
    }
    fun chooseSelfView(enabled: Boolean) { selfViewEnabled = enabled; settings.setSelfViewEnabled(enabled) }
    fun prepareCall(value: Routine) {
        if (busy) return
        chooseRoutine(value)
        val existing = pending.firstOrNull { it.routine == value }
        if (existing != null) resumeActivity(existing.id) else preparingCall = true
    }
    fun closeCallSetup() { stopPreview(); preparingCall = false }
    private fun stopPreview() { player.stop(); previewJob?.cancel(); previewing = false }
    fun preview() {
        if (session != null) return
        if (previewing) { stopPreview(); return }
        previewing = true
        if (routine.voiceReady) player.play(ScriptCatalog.mainAsset(level)) { previewing = false }
        else previewJob = viewModelScope.launch { delay(3200); previewing = false }
    }
    private fun begin(value: Routine, mode: EntryMode) {
        if (busy || session != null) return
        stopPreview(); chooseRoutine(value)
        val phase = when (mode) {
            EntryMode.CALL -> Phase.DIALING
            EntryMode.PRAISE -> Phase.PRAISE_READY
            EntryMode.REWARD -> Phase.STAR_REWARD
        }
        val next = pending.firstOrNull { it.routine == value }?.copy(phase = phase)
            ?: CallSession(UUID.randomUUID().toString(), level, guardian, System.currentTimeMillis(),
                phase = phase, routine = value, childId = activeChild.id, entryMode = mode)
        preparingCall = false
        move(next) { if (phase == Phase.DIALING) player.play("audio/ringback.wav", looping = true) {} }
    }
    fun start() = begin(routine, EntryMode.CALL)
    fun praise(value: Routine) = begin(value, EntryMode.PRAISE)
    fun reward(value: Routine) = begin(value, EntryMode.REWARD)
    fun resumeActivity(id: String) {
        if (busy) return
        val next = pending.firstOrNull { it.id == id }?.paused() ?: return
        chooseRoutine(next.routine); preparingCall = false
        move(next) { if (next.phase == Phase.DIALING) player.play("audio/ringback.wav", looping = true) {} }
    }
    fun cancelDialing() {
        if (busy) return
        val current = session?.takeIf { it.phase == Phase.DIALING } ?: return
        stopAllMedia()
        operate { snapshot = repository.discardActivity(current.id); session = null }
    }
    fun connect() {
        if (busy) return
        val next = session?.takeIf { it.phase == Phase.DIALING }?.connect() ?: return
        player.stop()
        move(next) { playGreeting() }
    }
    private fun playGreeting() {
        val expectedId = session?.id ?: return
        val expectedGeneration = presentationGeneration
        fun finishThisGreeting() {
            if (session?.id == expectedId && presentationGeneration == expectedGeneration) greetingFinished()
        }
        val recorded = runCatching { getApplication<Application>().assets.openFd("audio/hello.wav").use { }; true }.getOrDefault(false)
        if (recorded) player.play("audio/hello.wav") { finishThisGreeting() }
        else {
            greetingJob = viewModelScope.launch { delay(4000); finishThisGreeting() }
            greetingTts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS && session?.phase == Phase.GREETING && session?.id == expectedId && presentationGeneration == expectedGeneration) greetingTts?.apply {
                    if (setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE) {
                        setPitch(.72f); setSpeechRate(.85f)
                        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) = Unit
                            override fun onDone(utteranceId: String) { viewModelScope.launch { finishThisGreeting() } }
                            override fun onError(utteranceId: String) { viewModelScope.launch { finishThisGreeting() } }
                        })
                        speak("여보세요.", TextToSpeech.QUEUE_FLUSH, null, "heru_hello")
                    }
                }
            }
        }
    }
    private fun greetingFinished() {
        val next = session?.takeIf { it.phase == Phase.GREETING }?.greetingFinished() ?: return
        stopGreeting(); move(next)
    }
    private fun stopGreeting() { greetingJob?.cancel(); greetingJob = null; greetingTts?.stop(); greetingTts?.shutdown(); greetingTts = null }
    fun parentSpoke() {
        val next = session?.takeIf { it.phase == Phase.PARENT_TURN }?.parentSpoke() ?: return
        move(next) { speakRoutine(ScriptCatalog.mainAsset(next.level)) }
    }
    fun followUp(selectedLevel: Int) {
        val current = session ?: return
        val next = current.followUp(selectedLevel)
        if (current != next) move(next) { speakRoutine(ScriptCatalog.mainAsset(selectedLevel)) }
    }
    fun finish() {
        val current = session ?: return
        val next = current.finish()
        if (next != current) move(next)
    }
    fun choosePraiseLevel(value: Int) {
        val current = session?.takeIf { it.phase == Phase.PRAISE_READY } ?: return
        if (!busy) move(current.copy(praiseLevel = value.coerceIn(1, 5)))
    }
    fun playPraise() {
        val current = session?.takeIf { it.phase == Phase.PRAISE_READY } ?: return
        countdownSeconds = 3
        move(current.startPraiseCountdown()) {
            countdownJob = viewModelScope.launch {
                for (second in 3 downTo 1) { countdownSeconds = second; delay(1000) }
                val next = session?.takeIf { it.id == current.id && it.phase == Phase.PRAISE_COUNTDOWN }?.startPraiseSpeaking() ?: return@launch
                move(next) { speakRoutine(ScriptCatalog.followUpAsset(next.praiseLevel)) }
            }
        }
    }
    fun complete() {
        val current = session?.takeIf { it.phase == Phase.SUCCESS_WAITING } ?: return
        move(current.complete()) { speakRoutine("audio/finish.wav") }
    }
    fun rewardCurrent() {
        if (busy) return
        val current = session?.takeIf { !it.starAwarded && it.phase in listOf(Phase.WAITING, Phase.PRAISE_READY, Phase.SUCCESS_WAITING, Phase.RESULT) } ?: return
        stopAllMedia(); move(current.copy(phase = Phase.STAR_REWARD))
    }
    private fun speakRoutine(asset: String) {
        scriptJob?.cancel()
        if (session?.routine?.voiceReady == true) player.play(asset) { speechFinished() }
        else scriptJob = viewModelScope.launch { delay((sessionScript.length * 65L).coerceIn(2600, 6500)); speechFinished() }
    }
    fun finishPreviewSpeech() { scriptJob?.cancel(); speechFinished() }
    private fun speechFinished() {
        val current = session ?: return
        val next = current.speechFinished()
        if (next != current) move(next)
    }
    private fun stopAllMedia() { stopPreview(); countdownJob?.cancel(); scriptJob?.cancel(); stopGreeting() }
    private fun playbackIssue(note: String) {
        if (session != null) pauseCurrent(note) else { stopPreview(); message = note }
    }
    fun pauseCurrent(note: String? = null) {
        val current = session
        presentationGeneration++
        stopAllMedia(); session = null; preparingCall = false; tab = 0
        if (note != null) message = note
        if (current != null && !current.starAwarded && current.phase != Phase.SAVED) viewModelScope.launch {
            // Every transition already checkpoints a non-playing phase before presenting it.
            try { writes.withLock { snapshot = repository.reload() } }
            catch (_: Exception) { message = "활동을 저장하지 못했어요. 다시 열어 확인해 주세요." }
        }
    }
    fun stop() {
        if (busy) return
        val current = session ?: return
        stopAllMedia(); move(current.stop())
    }
    fun hostResumed() { foreground = true; today = LocalDate.now() }
    fun hostStopped() {
        foreground = false
        if (session != null) pauseCurrent() else { presentationGeneration++; stopAllMedia() }
    }
    fun awardStar() {
        val current = session?.takeIf { it.phase == Phase.STAR_REWARD && !it.starAwarded } ?: return
        val expectedGeneration = presentationGeneration
        operate {
            snapshot = repository.complete(current)
            if (foreground && expectedGeneration == presentationGeneration) session = current.copy(starAwarded = true)
        }
    }
    fun finishRewardPresentation() {
        session?.takeIf { it.starAwarded }?.let { session = it.copy(phase = Phase.SAVED) }
    }
    fun save(outcome: Outcome) {
        val current = session?.takeIf { it.phase == Phase.RESULT } ?: return
        if (outcome !in listOf(Outcome.UNCHANGED, Outcome.STOPPED)) return
        val expectedGeneration = presentationGeneration
        operate { snapshot = repository.finishWithoutStar(current, outcome); session = if (foreground && expectedGeneration == presentationGeneration) current.saved() else null }
    }
    fun home() { if (session?.phase == Phase.SAVED) { stopAllMedia(); session = null; tab = 0 } else pauseCurrent() }
    override fun onCleared() { stopAllMedia() }
}
