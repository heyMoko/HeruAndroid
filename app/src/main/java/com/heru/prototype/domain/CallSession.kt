package com.heru.prototype.domain

enum class Guardian(val label: String, val assetKey: String) {
    MOM("엄마", "mom"), DAD("아빠", "dad");
}

enum class Outcome(val label: String) {
    COMPLETED("약속을 지켰어요"),
    STARTED("행동을 시작했어요"),
    UNCHANGED("변화가 없었어요"),
    STOPPED("불편해서 중단했어요")
}

enum class Routine(val label: String, val icon: String, val action: String) {
    MEAL("식사", "🍚", "식사 자리에 함께 앉기"),
    BRUSH("양치", "🪥", "양치 준비하고 함께 닦기"),
    TIDY("정리", "🧸", "장난감을 제자리에 정리하기"),
    BEDTIME("잠자리", "🌙", "잠자리에 들고 쉬기");

    val voiceReady: Boolean get() = this == MEAL
    val parentExample: String get() = when (this) {
        MEAL -> "밥을 안 먹어요"
        BRUSH -> "양치를 안 해요"
        BEDTIME -> "잠자리에 안 들어요"
        TIDY -> "장난감을 정리하지 않아요"
    }
    val objectPhrase: String get() = when (this) {
        MEAL -> "식사를"
        BRUSH -> "양치를"
        BEDTIME -> "잠자리를"
        TIDY -> "정리를"
    }
}

enum class Phase { DIALING, GREETING, PARENT_TURN, SPEAKING, WAITING, PRAISE_READY, PRAISE_COUNTDOWN, PRAISE_SPEAKING, SUCCESS_WAITING, CLOSING, STAR_REWARD, RESULT, SAVED }

data class CallSession(
    val id: String,
    val level: Int,
    val guardian: Guardian,
    val startedAt: Long,
    val phase: Phase = Phase.SPEAKING,
    val followUpLevels: List<Int> = emptyList(),
    val routine: Routine = Routine.MEAL,
    val childId: String = DEFAULT_CHILD_ID,
    val praiseLevel: Int = 3,
    val entryMode: EntryMode = EntryMode.CALL,
    val warningPlayed: Boolean = false,
    val starAwarded: Boolean = false,
    val rewardReturnPhase: Phase? = null,
) {
    init {
        require(level in 1..5)
        require(followUpLevels.all { it in 1..5 })
        require(praiseLevel in 1..5)
    }
    val followUpUsed: Boolean get() = followUpLevels.isNotEmpty()
    val followUpLevel: Int? get() = followUpLevels.lastOrNull()
    fun connect(): CallSession =
        if (phase == Phase.DIALING) copy(phase = Phase.GREETING) else this
    fun greetingFinished(): CallSession =
        if (phase == Phase.GREETING) copy(phase = Phase.PARENT_TURN) else this
    fun parentSpoke(): CallSession =
        if (phase == Phase.PARENT_TURN) copy(phase = Phase.SPEAKING, warningPlayed = true) else this
    fun speechFinished() = when (phase) {
        Phase.SPEAKING -> copy(phase = Phase.WAITING)
        Phase.PRAISE_SPEAKING -> copy(phase = Phase.SUCCESS_WAITING)
        Phase.CLOSING -> copy(phase = Phase.STAR_REWARD)
        else -> this
    }
    fun followUp(selectedLevel: Int): CallSession =
        if (phase == Phase.WAITING && selectedLevel in 1..5)
            copy(phase = Phase.SPEAKING, warningPlayed = true, followUpLevels = followUpLevels + selectedLevel) else this
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
    fun backFromStarReward(): CallSession =
        if (phase == Phase.STAR_REWARD && !starAwarded)
            copy(phase = rewardReturnPhase ?: Phase.WAITING, rewardReturnPhase = null) else this
    fun stop(): CallSession = if (phase == Phase.SAVED) this else copy(phase = Phase.RESULT)
    fun saved(): CallSession = if (phase == Phase.RESULT) copy(phase = Phase.SAVED) else this
    fun paused(): CallSession = copy(phase = when (phase) {
        Phase.GREETING -> Phase.PARENT_TURN
        Phase.SPEAKING -> Phase.WAITING
        Phase.PRAISE_COUNTDOWN, Phase.PRAISE_SPEAKING -> Phase.PRAISE_READY
        Phase.CLOSING -> Phase.SUCCESS_WAITING
        else -> phase
    })
}

data class SessionRecord(
    val id: String, val startedAt: Long, val level: Int,
    val guardian: Guardian, val outcome: Outcome,
    val followUpLevels: List<Int> = emptyList(),
    val routine: Routine = Routine.MEAL,
    val childId: String = DEFAULT_CHILD_ID,
    val finishedAt: Long = startedAt,
    val entryMode: EntryMode = EntryMode.CALL,
    val warningPlayed: Boolean = false,
    val starAwarded: Boolean = false,
)

object ScriptCatalog {
    val labels = listOf("부드럽게", "차분하게", "단호하게", "엄하게", "강하게")

    fun main(level: Int, guardian: Guardian = Guardian.MOM, routine: Routine = Routine.MEAL): String {
        require(level in 1..5)
        if (routine != Routine.MEAL) {
            val parent = guardian.label
            val scripts = when (routine) {
                Routine.BRUSH -> listOf(
                    "이제 양치할 시간이야. 놀이를 잠깐 멈추고 ${parent}랑 화장실에 가볼까?",
                    "이제 양치해야 해. 장난감은 내려놓고 ${parent}가 부르면 양치하러 와야 해.",
                    "양치할 시간이 지났는데 아직도 놀고 있구나. 지금 화장실로 가서 이를 닦아라.",
                    "누구야! 누가 양치할 시간에 아직도 놀고 있어! ${parent}가 양치하라고 했지! 지금 바로 화장실로 와!",
                    "누구야! 누가 양치를 안 해! 누가 ${parent} 말을 안 들어! 지금 바로 와서 이를 닦아!"
                )
                Routine.BEDTIME -> listOf(
                    "이제 잘 시간이야. 하던 놀이를 마치고 잠옷을 입어볼까?",
                    "이제 잘 시간이야. 장난감을 정리하고 잠자리에 들어야 해.",
                    "잘 시간이 지났는데 아직도 놀고 있구나. 놀이를 멈추고 침대로 가라.",
                    "누구야! 누가 잘 시간에 아직도 안 자고 있어! 지금 바로 장난감을 내려놓고 침대로 가!",
                    "누구야! 누가 아직도 안 자! 지금 당장 놀이를 멈추고 잠자리에 들어!"
                )
                Routine.TIDY -> listOf(
                    "놀이가 끝났네. 장난감 하나씩 제자리에 놓아볼까?",
                    "이제 장난감을 정리할 시간이야. 놀이는 멈추고 제자리에 놓아야 해.",
                    "정리할 시간이 지났는데 장난감이 그대로구나. 지금 바로 제자리에 정리해라.",
                    "누구야! 누가 정리하자고 했는데 아직도 안 하고 있어! 지금 바로 장난감을 치워!",
                    "누구야! 누가 정리를 안 해! 장난감 내려놓고 지금 바로 제자리에 정리해!"
                )
                Routine.MEAL -> error("Meal scripts are defined below")
            }
            return scripts[level - 1]
        }
        return when (level) {
            1 -> "이제 밥 먹을 시간이야. 장난감은 잠깐 내려놓고 엄마랑 식탁에 앉아볼까?"
            2 -> "이제 밥 먹을 시간이야. 놀이는 멈추고 엄마가 부르면 식탁으로 와야 해."
            3 -> "밥 먹을 시간이 지났는데 아직도 놀고 있구나. 장난감 내려놓고 식탁에 앉아라."
            4 -> "누구야! 누가 밥 먹을 시간에 아직도 놀고 있어! 엄마가 식사 시간이라고 했지! 지금 바로 장난감 내려놓고 식탁으로 와!"
            else -> "누구야! 누가 밥을 안 먹어! 누가 엄마 말을 안 들어! 장난감 내려놓고 얼른 일어나! 지금 바로 식탁으로 앉아!"
        }
    }

    fun followUp(level: Int, routine: Routine = Routine.MEAL): String {
        if (routine != Routine.MEAL) return when (routine) {
            Routine.BRUSH -> listOf("잘했어! 해루가 기다리고 있었어.", "그래, 천천히 꼼꼼하게 닦아보자.", "바로 양치하러 왔구나. 약속을 지켜서 멋지다.", "그래, 이제야 왔구나. 해루가 깨끗하게 닦는지 지켜볼게.", "아주 잘했다. 이를 닦는 멋진 모습을 해루에게 보여줘!")[level - 1]
            Routine.BEDTIME -> listOf("잘했어! 이제 편하게 쉴 수 있겠다.", "그래, 이제 잠옷을 입고 침대에 누워보자.", "바로 준비했구나. 약속을 지켜서 멋지다.", "그래, 이제야 왔구나. 해루가 잘 준비하는 걸 지켜볼게.", "아주 잘했다. 멋지게 잠자리에 들었구나!")[level - 1]
            Routine.TIDY -> listOf("잘했어! 장난감이 집으로 돌아가네.", "그래, 하나씩 제자리에 놓아보자.", "바로 정리했구나. 약속을 지켜서 멋지다.", "그래, 이제야 정리하는구나. 해루가 지켜볼게.", "아주 잘했다. 정리하는 멋진 모습을 보여줘!")[level - 1]
            Routine.MEAL -> error("Meal praise scripts are defined below")
        }
        return when (level) {
        1 -> "잘했어! 해루가 기다리고 있었는데 정말 잘 왔구나!"
        2 -> "그래, 잘했어. 이제 천천히 밥을 먹어보자."
        3 -> "바로 앉았구나. 약속을 지키는 모습이 보기 좋다."
        4 -> "그래, 이제야 왔구나. 잘했어. 밥을 먹으면 해루가 지켜보고 있을게."
        else -> "그래, 바로 앉았구나. 아주 잘했다. 이제 밥을 먹는 모습을 해루에게 보여줘라!"
        }
    }

    fun finish(routine: Routine = Routine.MEAL): String = when (routine) {
        Routine.MEAL -> "오늘 식사 약속을 잘 지켰구나. 해루가 기억해둘게. 다음에도 멋지게 해보자."
        Routine.BRUSH -> "오늘 양치 약속을 잘 지켰구나. 해루가 기억해둘게. 다음에도 멋지게 해보자."
        Routine.BEDTIME -> "오늘 잠자리 약속을 잘 지켰구나. 해루가 기억해둘게. 다음에도 멋지게 해보자."
        Routine.TIDY -> "오늘 정리 약속을 잘 지켰구나. 해루가 기억해둘게. 다음에도 멋지게 해보자."
    }
    fun mainAsset(level: Int) = "audio/main_$level.wav"
    fun followUpAsset(level: Int) = "audio/follow_$level.wav"
}
