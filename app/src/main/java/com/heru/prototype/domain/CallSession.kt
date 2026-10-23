package com.heru.prototype.domain

enum class Guardian(val label: String, val assetKey: String) {
    MOM("엄마", "mom"), DAD("아빠", "dad");
}

enum class Outcome(val label: String) {
    STARTED("행동을 시작했어요"),
    UNCHANGED("변화가 없었어요"),
    STOPPED("불편해서 중단했어요")
}

enum class Phase { DIALING, GREETING, PARENT_TURN, SPEAKING, WAITING, PRAISE_READY, PRAISE_COUNTDOWN, PRAISE_SPEAKING, SUCCESS_WAITING, CLOSING, STAR_REWARD, RESULT, SAVED }

data class CallSession(
    val id: String,
    val level: Int,
    val guardian: Guardian,
    val startedAt: Long,
    val phase: Phase = Phase.SPEAKING,
    val followUpUsed: Boolean = false,
) {
    init { require(level in 1..5) }
    fun connect(): CallSession =
        if (phase == Phase.DIALING) copy(phase = Phase.GREETING) else this
    fun greetingFinished(): CallSession =
        if (phase == Phase.GREETING) copy(phase = Phase.PARENT_TURN) else this
    fun parentSpoke(): CallSession =
        if (phase == Phase.PARENT_TURN) copy(phase = Phase.SPEAKING) else this
    fun speechFinished() = when (phase) {
        Phase.SPEAKING -> copy(phase = Phase.WAITING)
        Phase.PRAISE_SPEAKING -> copy(phase = Phase.SUCCESS_WAITING)
        Phase.CLOSING -> copy(phase = Phase.STAR_REWARD)
        else -> this
    }
    fun followUp(): CallSession =
        if (phase == Phase.WAITING && !followUpUsed)
            copy(phase = Phase.SPEAKING, followUpUsed = true) else this
    fun finish(): CallSession =
        if (phase == Phase.WAITING) copy(phase = Phase.PRAISE_READY) else this
    fun startPraiseCountdown(): CallSession =
        if (phase == Phase.PRAISE_READY) copy(phase = Phase.PRAISE_COUNTDOWN) else this
    fun startPraiseSpeaking(): CallSession =
        if (phase == Phase.PRAISE_COUNTDOWN) copy(phase = Phase.PRAISE_SPEAKING) else this
    fun complete(): CallSession =
        if (phase == Phase.SUCCESS_WAITING) copy(phase = Phase.CLOSING) else this
    fun awardStar(): CallSession =
        if (phase == Phase.STAR_REWARD) copy(phase = Phase.RESULT) else this
    fun stop(): CallSession = if (phase == Phase.SAVED) this else copy(phase = Phase.RESULT)
    fun saved(): CallSession = if (phase == Phase.RESULT) copy(phase = Phase.SAVED) else this
}

data class SessionRecord(
    val id: String, val startedAt: Long, val level: Int,
    val guardian: Guardian, val outcome: Outcome,
)

object ScriptCatalog {
    val labels = listOf("부드럽게", "차분하게", "단호하게", "엄하게", "강하게")

    fun main(level: Int, guardian: Guardian = Guardian.MOM): String {
        require(level in 1..5)
        return when (level) {
            1 -> "이제 밥 먹을 시간이야. 장난감은 잠깐 내려놓고 엄마랑 식탁에 앉아볼까?"
            2 -> "이제 밥 먹을 시간이야. 놀이는 멈추고 엄마가 부르면 식탁으로 와야 해."
            3 -> "밥 먹을 시간이 지났는데 아직도 놀고 있구나. 장난감 내려놓고 식탁에 앉아라."
            4 -> "누구야! 누가 밥 먹을 시간에 아직도 놀고 있어! 엄마가 식사 시간이라고 했지! 지금 바로 장난감 내려놓고 식탁으로 와!"
            else -> "누구야! 누가 밥을 안 먹어! 누가 엄마 말을 안 들어! 장난감 내려놓고 얼른 일어나! 지금 바로 식탁으로 앉아!"
        }
    }

    fun followUp(level: Int): String = when (level) {
        1 -> "잘했어! 해루가 기다리고 있었는데 정말 잘 왔구나!"
        2 -> "그래, 잘했어. 이제 천천히 밥을 먹어보자."
        3 -> "바로 앉았구나. 약속을 지키는 모습이 보기 좋다."
        4 -> "그래, 이제야 왔구나. 잘했어. 밥을 먹으면 해루가 지켜보고 있을게."
        else -> "그래, 바로 앉았구나. 아주 잘했다. 이제 밥을 먹는 모습을 해루에게 보여줘라!"
    }

    const val finish = "오늘 식사 약속을 잘 지켰구나. 해루가 기억해둘게. 다음에도 멋지게 해보자."
    fun mainAsset(level: Int) = "audio/main_$level.wav"
    fun followUpAsset(level: Int) = "audio/follow_$level.wav"
}
