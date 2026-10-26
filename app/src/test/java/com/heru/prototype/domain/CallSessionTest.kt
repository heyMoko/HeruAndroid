package com.heru.prototype.domain

import org.junit.Assert.*
import org.junit.Test

class CallSessionTest {
    private fun session() = CallSession("test", 5, Guardian.MOM, 100L)

    @Test fun completionWaitsForParent() {
        assertEquals(Phase.WAITING, session().speechFinished().phase)
    }

    @Test fun warningWaitsForConnectionAndParentExplanation() {
        val dialing = session().copy(phase = Phase.DIALING)
        assertEquals(dialing, dialing.parentSpoke())
        val greeting = dialing.connect()
        assertEquals(Phase.GREETING, greeting.phase)
        assertEquals(greeting, greeting.parentSpoke())
        val parentTurn = greeting.greetingFinished()
        assertEquals(Phase.PARENT_TURN, parentTurn.phase)
        assertEquals(Phase.SPEAKING, parentTurn.parentSpoke().phase)
    }

    @Test fun followUpCanBeRepeatedWithChosenLevels() {
        val initial = session().copy(level = 2).speechFinished()
        val speaking = initial.followUp(5)
        assertEquals(Phase.SPEAKING, speaking.phase)
        assertEquals(2, speaking.level)
        assertEquals(5, speaking.followUpLevel)
        val once = speaking.speechFinished()
        assertTrue(once.followUpUsed)
        val twice = once.followUp(4)
        assertEquals(Phase.SPEAKING, twice.phase)
        assertEquals(listOf(5, 4), twice.followUpLevels)
        assertEquals(listOf(5, 4), twice.speechFinished().followUpLevels)
        assertEquals(initial, initial.followUp(6))
    }

    @Test fun parentCannotStartClosingWhileMainIsSpeaking() {
        assertEquals(session(), session().finish())
    }

    @Test fun closingEndsAtResultNotAutomaticSuccess() {
        val result = session().speechFinished().finish().startPraiseCountdown()
            .startPraiseSpeaking().speechFinished().complete().speechFinished().awardStar()
        assertEquals(Phase.RESULT, result.phase)
    }

    @Test fun backingOutOfStarRewardReturnsToTheScreenThatOpenedIt() {
        val waiting = session().copy(phase = Phase.WAITING)
        val reward = waiting.copy(phase = Phase.STAR_REWARD, rewardReturnPhase = waiting.phase)
        val returned = reward.backFromStarReward()
        assertEquals(Phase.WAITING, returned.phase)
        assertNull(returned.rewardReturnPhase)
        val awarded = reward.copy(starAwarded = true)
        assertEquals(awarded, awarded.backFromStarReward())
    }

    @Test fun successAudioMustBeCompletedBeforeEndAudio() {
        val started = session().speechFinished().finish()
        assertEquals(Phase.PRAISE_READY, started.phase)
        val countdown = started.startPraiseCountdown()
        assertEquals(Phase.PRAISE_COUNTDOWN, countdown.phase)
        val speaking = countdown.startPraiseSpeaking()
        assertEquals(Phase.PRAISE_SPEAKING, speaking.phase)
        assertEquals(Phase.SUCCESS_WAITING, speaking.speechFinished().phase)
    }

    @Test fun stopNeverChangesSelectedLevelOrGuardian() {
        val result = session().stop()
        assertEquals(5, result.level)
        assertEquals(Guardian.MOM, result.guardian)
        assertEquals(Phase.RESULT, result.phase)
    }

    @Test fun onlyResultCanBecomeSaved() {
        assertEquals(Phase.SPEAKING, session().saved().phase)
        assertEquals(Phase.SAVED, session().stop().saved().phase)
    }

    @Test fun lateAudioCompletionDoesNotReopenStoppedSession() {
        val stopped = session().stop()
        assertEquals(stopped, stopped.speechFinished())
    }

    @Test fun everyLevelHasOneScriptAndAsset() {
        for (level in 1..5) {
            assertTrue(ScriptCatalog.main(level).isNotBlank())
            assertTrue(ScriptCatalog.followUp(level).isNotBlank())
            assertEquals("audio/main_$level.wav", ScriptCatalog.mainAsset(level))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLevelIsRejected() {
        session().copy(level = 6)
    }
}
