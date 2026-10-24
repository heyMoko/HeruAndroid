package com.heru.prototype.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val DEFAULT_CHILD_ID = "legacy-child"
enum class EntryMode { CALL, PRAISE, REWARD }

data class ChildProfile(
    val id: String,
    val name: String,
    val plans: Map<Routine, Int> = defaultPlans(),
    val createdAt: Long = System.currentTimeMillis(),
)

fun defaultPlans(): Map<Routine, Int> = linkedMapOf(
    Routine.MEAL to 1, Routine.BRUSH to 2, Routine.TIDY to 1, Routine.BEDTIME to 1
)

data class RewardGoal(
    val id: String,
    val title: String = "함께 놀이터 가기",
    val imageUri: String? = null,
    val target: Int = 8,
    val startStars: Int = 0,
    val celebrated: Boolean = false,
    val celebrationPending: Boolean = false,
) {
    init { require(target in 1..30); require(startStars >= 0) }
    fun earned(totalStars: Int) = (totalStars - startStars).coerceAtLeast(0)
    fun progress(totalStars: Int) = earned(totalStars).coerceAtMost(target)
    fun reached(totalStars: Int) = earned(totalStars) >= target
}

fun Long.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun List<SessionRecord>.completionsOn(date: LocalDate, routine: Routine? = null) = count {
    it.outcome == Outcome.COMPLETED && it.finishedAt.localDate() == date && (routine == null || it.routine == routine)
}

data class FamilySnapshot(
    val children: List<ChildProfile>,
    val activeChildId: String,
    val stars: Int,
    val goal: RewardGoal,
    val activities: List<CallSession>,
    val records: List<SessionRecord>,
)
