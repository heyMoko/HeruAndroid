package com.heru.prototype.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FamilyModelsTest {
    @Test fun newGoalStartsFromItsOwnBaseline() {
        val goal = RewardGoal("new", target = 8, startStars = 27)
        assertEquals(0, goal.progress(27))
        assertEquals(1, goal.progress(28))
        assertFalse(goal.reached(34))
        assertTrue(goal.reached(35))
    }

    @Test fun editingGoalKeepsItsEarnedStars() {
        val original = RewardGoal("goal", target = 8, startStars = 10)
        val edited = original.copy(title = "책 읽기", target = 12, imageUri = "file:///photo.jpg")
        assertEquals(4, edited.progress(14))
        assertEquals(original.id, edited.id)
        assertEquals("file:///photo.jpg", edited.imageUri)
    }

    @Test fun progressCannotWrapAroundOrBecomeNegative() {
        val goal = RewardGoal("goal", target = 30, startStars = 7)
        assertEquals(0, goal.progress(0))
        assertEquals(30, goal.progress(100))
        assertTrue(goal.reached(100))
    }

    @Test fun interruptedSpeechRestoresOnlyAParentControlledPhase() {
        val session = CallSession("s", 3, Guardian.MOM, 100, childId = "child", routine = Routine.BRUSH)
        val expected = mapOf(Phase.GREETING to Phase.PARENT_TURN, Phase.SPEAKING to Phase.WAITING,
            Phase.PRAISE_COUNTDOWN to Phase.PRAISE_READY, Phase.PRAISE_SPEAKING to Phase.PRAISE_READY,
            Phase.CLOSING to Phase.SUCCESS_WAITING, Phase.STAR_REWARD to Phase.STAR_REWARD)
        expected.forEach { (before, after) ->
            val paused = session.copy(phase = before).paused()
            assertEquals(after, paused.phase)
            assertEquals("child", paused.childId)
            assertEquals(Routine.BRUSH, paused.routine)
            assertEquals(paused, paused.paused())
        }
    }

    @Test fun directPraiseNeedsNoWarningAndItsSelectionSurvivesPause() {
        val session = CallSession("s", 1, Guardian.MOM, 100, Phase.PRAISE_READY,
            entryMode = EntryMode.PRAISE, praiseLevel = 5)
        val waiting = session.startPraiseCountdown().startPraiseSpeaking().speechFinished()
        assertEquals(Phase.SUCCESS_WAITING, waiting.phase)
        assertFalse(waiting.warningPlayed)
        assertEquals(5, waiting.paused().praiseLevel)
    }

    @Test fun warningIsRecordedOnlyAfterTheParentStartsIt() {
        val session = CallSession("s", 2, Guardian.MOM, 100, Phase.DIALING)
        assertFalse(session.warningPlayed)
        assertFalse(session.connect().greetingFinished().warningPlayed)
        assertTrue(session.connect().greetingFinished().parentSpoke().warningPlayed)
    }

    @Test fun dailyCountsUseCompletionDateAndSupportTwoBrushings() {
        val date = LocalDate.of(2026, 10, 24)
        val time = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun record(id: String, outcome: Outcome, offset: Long) = SessionRecord(id, time - 86_400_000,
            1, Guardian.MOM, outcome, routine = Routine.BRUSH, finishedAt = time + offset)
        val records = listOf(record("morning", Outcome.COMPLETED, 1000), record("night", Outcome.COMPLETED, 2000),
            record("attempt", Outcome.UNCHANGED, 3000), record("legacy", Outcome.STARTED, 4000),
            record("yesterday", Outcome.COMPLETED, -1000))
        assertEquals(2, records.completionsOn(date, Routine.BRUSH))
        assertEquals(0, records.completionsOn(date, Routine.MEAL))
        assertEquals(1, records.completionsOn(date.minusDays(1)))
    }
}
