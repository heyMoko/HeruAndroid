package com.heru.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.media.MediaMetadataRetriever
import com.heru.prototype.data.LocalStore
import com.heru.prototype.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HeruFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun parentCanSelectStopAndRecord() {
        compose.onNodeWithTag("level_5").performClick()
        compose.onNodeWithTag("guardian_dad").performScrollTo().performClick()
        compose.onNodeWithTag("script").assertTextContains("아빠가", substring = true)
        compose.onNodeWithTag("start").performClick()
        compose.onNodeWithTag("stop").performScrollTo().performClick()
        compose.onNodeWithTag("outcome_STOPPED").performClick()
        compose.onNodeWithText("기록했어요").assertIsDisplayed()
        compose.onNodeWithTag("home").performClick()
        compose.onNodeWithTag("history").performClick()
        compose.onAllNodesWithText("불편해서 중단했어요").onFirst().assertIsDisplayed()
    }

    @Test fun leavingAppStopsTheSessionWithoutAutomaticResume() {
        compose.onNodeWithTag("start").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("방금은 어땠나요?").assertIsDisplayed()
        compose.onNodeWithTag("stop").assertDoesNotExist()
    }

    @Test fun bundledClipsArePlayableAndSavingIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = (1..5).flatMap { level ->
            Guardian.entries.map { ScriptCatalog.mainAsset(level, it) } +
                ScriptCatalog.followUpAsset(level)
        } + "audio/finish.wav"
        assets.forEach { path ->
            context.assets.openFd(path).use { fd ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    assertTrue("Empty clip: $path", (duration?.toLongOrNull() ?: 0L) > 0)
                } finally { retriever.release() }
            }
        }
        val store = LocalStore(context)
        val id = UUID.randomUUID().toString()
        val record = SessionRecord(id, 123L, 2, Guardian.MOM, Outcome.UNCHANGED)
        assertTrue(store.append(record))
        assertTrue(store.append(record))
        assertEquals(1, store.records().count { it.id == id })
    }
}
