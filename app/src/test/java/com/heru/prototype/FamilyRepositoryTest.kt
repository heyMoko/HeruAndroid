package com.heru.prototype

import android.content.Context
import androidx.room.Room
import com.heru.prototype.data.*
import com.heru.prototype.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** Uses an isolated in-memory database and isolated legacy preferences, never the family's real data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FamilyRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: HeruDatabase
    private lateinit var legacy: LocalStore
    private lateinit var repository: FamilyRepository
    private lateinit var settingsName: String

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, HeruDatabase::class.java).build()
        val prefix = UUID.randomUUID().toString()
        legacy = LocalStore(context, "legacy-$prefix")
        settingsName = "family-$prefix"
        repository = FamilyRepository(context, database, legacy, settingsName)
    }
    @After fun close() { database.close() }

    private fun activity(id: String = UUID.randomUUID().toString(), childId: String = DEFAULT_CHILD_ID) =
        CallSession(id, 2, Guardian.MOM, System.currentTimeMillis(), Phase.STAR_REWARD,
            routine = Routine.TIDY, childId = childId, entryMode = EntryMode.REWARD)

    @Test fun starAndCompletionAreCommittedExactlyOnce() = runBlocking {
        repository.initialize()
        val current = activity()
        repository.saveActivity(current)
        repository.complete(current)
        val retried = repository.complete(current)
        assertEquals(1, retried.stars)
        assertEquals(1, retried.records.size)
        assertEquals(Outcome.COMPLETED, retried.records.single().outcome)
        assertTrue(retried.records.single().starAwarded)
        assertTrue(retried.activities.isEmpty())
        repository.saveActivity(current)
        assertTrue(repository.reload().activities.isEmpty())
    }

    @Test fun renamingChildPreservesForeignKeyData() = runBlocking {
        val initial = repository.initialize()
        repository.complete(activity())
        val result = repository.saveChild(initial.children.single().copy(name = "새 별명"))
        assertEquals(1, result.stars)
        assertEquals(1, result.records.size)
        assertEquals(initial.goal.id, result.goal.id)
    }

    @Test fun editingKeepsPhotoAndProgressButNewGoalStartsAtZero() = runBlocking {
        repository.initialize()
        repository.complete(activity())
        val edited = repository.saveGoal(DEFAULT_CHILD_ID, "사진 선물", "file:///photo.jpg", 3, false)
        assertEquals(1, edited.goal.progress(edited.stars))
        assertEquals("file:///photo.jpg", edited.goal.imageUri)
        val next = repository.saveGoal(DEFAULT_CHILD_ID, "다음 선물", null, 5, true)
        assertEquals(1, next.stars)
        assertEquals(0, next.goal.progress(next.stars))
        assertNotEquals(edited.goal.id, next.goal.id)
    }

    @Test fun goalCelebrationIsPersistentAndAcknowledgedOnlyOnce() = runBlocking {
        repository.initialize()
        repository.saveGoal(DEFAULT_CHILD_ID, "책", null, 1, false)
        assertTrue(repository.complete(activity()).goal.celebrationPending)
        assertTrue(repository.reload().goal.celebrationPending)
        repository.acknowledgeGoal(DEFAULT_CHILD_ID)
        val more = repository.complete(activity())
        assertFalse(more.goal.celebrationPending)
        assertTrue(more.goal.celebrated)
    }

    @Test fun legacyMigrationRunsOnceAndKeepsOldRecords() = runBlocking {
        legacy.addStar(); legacy.addStar()
        legacy.setReward("기존 선물", "file:///legacy.jpg", 8)
        legacy.append(SessionRecord("old", 123, 2, Guardian.MOM, Outcome.STARTED))
        repository.initialize()
        val again = repository.initialize()
        assertEquals(2, again.stars)
        assertEquals(1, again.records.size)
        assertEquals("기존 선물", again.goal.title)
        assertEquals("file:///legacy.jpg", again.goal.imageUri)
    }

    @Test fun activitiesRestoreWithoutPlayingAndStayWithTheirChild() = runBlocking {
        repository.initialize()
        repository.saveActivity(activity().copy(phase = Phase.PRAISE_SPEAKING, praiseLevel = 5))
        val reloaded = FamilyRepository(context, database, legacy, settingsName).initialize()
        assertEquals(Phase.PRAISE_READY, reloaded.activities.single().phase)
        assertEquals(5, reloaded.activities.single().praiseLevel)
        repository.saveChild(ChildProfile("sibling", "둘째"))
        assertEquals(0, repository.reload().stars)
        assertTrue(repository.reload().activities.isEmpty())
        assertEquals(1, repository.selectChild(DEFAULT_CHILD_ID).activities.size)
    }

    @Test fun aRecordedUnfinishedAttemptCannotLaterMintAStarWithTheSameId() = runBlocking {
        repository.initialize()
        val current = activity()
        repository.finishWithoutStar(current, Outcome.STOPPED)
        val result = repository.complete(current)
        assertEquals(0, result.stars)
        assertEquals(Outcome.STOPPED, result.records.single().outcome)
        assertFalse(result.records.single().starAwarded)
    }

    @Test fun simultaneousCompletionRequestsStillProduceOneStar() = runBlocking {
        repository.initialize()
        val current = activity()
        coroutineScope { (1..4).map { async { repository.complete(current) } }.awaitAll() }
        val result = repository.reload()
        assertEquals(1, result.stars)
        assertEquals(1, result.records.size)
    }

    @Test fun failedCompletionRollsBackBothTheRecordAndStar() = runBlocking {
        repository.initialize()
        val current = activity()
        repository.saveActivity(current)
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DELETE FROM goals") }
        assertTrue(runCatching { repository.complete(current) }.isFailure)
        withContext(Dispatchers.IO) {
            assertEquals(0, database.family().stars(DEFAULT_CHILD_ID))
            assertTrue(database.family().records(DEFAULT_CHILD_ID).isEmpty())
            assertEquals(1, database.family().activities(DEFAULT_CHILD_ID).size)
        }
    }
}
