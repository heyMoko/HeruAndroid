package com.heru.prototype.domain

import org.junit.Assert.*
import org.junit.Test

class CallSessionTest {
    private fun session() = CallSession("test", 5, Guardian.MOM, 100L)

    @Test fun completionWaitsForParent() {
        assertEquals(Phase.WAITING, session().speechFinished().phase)
    }

    @Test fun followUpCanOnlyRunOnce() {
        val once = session().speechFinished().followUp().speechFinished()
        assertTrue(once.followUpUsed)
        assertEquals(once, once.followUp())
    }

    @Test fun parentCannotStartClosingWhileMainIsSpeaking() {
        assertEquals(session(), session().finish())
    }

    @Test fun closingEndsAtResultNotAutomaticSuccess() {
        val result = session().speechFinished().finish().startPraiseCountdown()
            .startPraiseSpeaking().speechFinished().complete().speechFinished().awardStar()
        assertEquals(Phase.RESULT, result.phase)
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
