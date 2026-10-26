package com.heru.prototype.data

import android.content.Context
import android.annotation.SuppressLint
import androidx.room.Room
import com.heru.prototype.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@SuppressLint("ApplySharedPref") // Commits run on IO; the selected child must be durable before a snapshot is read.
class FamilyRepository(context: Context, databaseOverride: HeruDatabase? = null,
    private val legacy: LocalStore = LocalStore(context), settingsName: String = "heru_family_settings") {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(settingsName, Context.MODE_PRIVATE)
    private val db = databaseOverride ?: database(app)
    private val dao = db.family()

    suspend fun initialize(): FamilySnapshot = withContext(Dispatchers.IO) {
        val legacyImage = if (dao.imported("legacy-v1") == 0) legacy.rewardImageUri()?.let { original ->
            runCatching { importRewardImage(app, original) }.getOrDefault(original)
        } else null
        db.runInTransaction {
            if (dao.imported("legacy-v1") == 0) {
                val child = ChildProfile(DEFAULT_CHILD_ID, "우리 아이")
                dao.putChild(child.toRow())
                val total = legacy.stars()
                val goal = RewardGoal(UUID.randomUUID().toString(), legacy.rewardTitle(),
                    legacyImage, legacy.rewardTarget().coerceIn(1, 30),
                    celebrated = total >= legacy.rewardTarget().coerceIn(1, 30))
                dao.putGoal(goal.toRow(child.id))
                if (total > 0) dao.putStar(HeruDatabase.StarRow().apply {
                    id = "legacy-stars"; childId = child.id; earnedAt = System.currentTimeMillis(); amount = total
                })
                legacy.records().forEach { dao.putRecord(it.toRow()) }
                dao.putMeta(HeruDatabase.MetaRow().apply { key = "legacy-v1" })
            }
        }
        snapshot()
    }

    suspend fun reload(): FamilySnapshot = withContext(Dispatchers.IO) { snapshot() }

    suspend fun selectChild(id: String): FamilySnapshot = withContext(Dispatchers.IO) {
        require(dao.children().any { it.id == id })
        check(prefs.edit().putString("active_child", id).commit())
        snapshot()
    }

    suspend fun saveChild(profile: ChildProfile): FamilySnapshot = withContext(Dispatchers.IO) {
        require(profile.name.isNotBlank() && profile.name.length <= 24)
        require(profile.plans.isNotEmpty() && profile.plans.values.all { it in 1..3 })
        check(prefs.edit().putString("active_child", profile.id).commit())
        db.runInTransaction {
            dao.putChild(profile.toRow())
            if (dao.goal(profile.id) == null) dao.putGoal(RewardGoal(UUID.randomUUID().toString()).toRow(profile.id))
        }
        snapshot()
    }

    suspend fun saveActivity(session: CallSession) = withContext(Dispatchers.IO) {
        db.runInTransaction {
            if (session.phase != Phase.SAVED && !session.starAwarded && dao.hasRecord(session.id) == 0) dao.putActivity(session.paused().toRow())
        }
    }

    suspend fun discardActivity(id: String): FamilySnapshot = withContext(Dispatchers.IO) {
        dao.removeActivity(id); snapshot()
    }

    suspend fun complete(session: CallSession): FamilySnapshot = withContext(Dispatchers.IO) {
        db.runInTransaction {
            val now = System.currentTimeMillis()
            val record = SessionRecord(session.id, session.startedAt, session.level, session.guardian,
                Outcome.COMPLETED, session.followUpLevels, session.routine, session.childId,
                now, session.entryMode, session.warningPlayed, starAwarded = true)
            if (dao.putRecord(record.toRow()) != -1L) {
                dao.putStar(HeruDatabase.StarRow().apply {
                    id = session.id; childId = session.childId; earnedAt = now; amount = 1
                })
                val goal = requireNotNull(dao.goal(session.childId))
                if (!goal.celebrated && dao.stars(session.childId) - goal.startStars >= goal.target) {
                    goal.celebrationPending = true
                    dao.putGoal(goal)
                }
            }
            dao.removeActivity(session.id)
        }
        snapshot()
    }

    suspend fun finishWithoutStar(session: CallSession, outcome: Outcome): FamilySnapshot = withContext(Dispatchers.IO) {
        require(outcome == Outcome.UNCHANGED || outcome == Outcome.STOPPED)
        db.runInTransaction {
            dao.putRecord(SessionRecord(session.id, session.startedAt, session.level, session.guardian,
                outcome, session.followUpLevels, session.routine, session.childId,
                System.currentTimeMillis(), session.entryMode, session.warningPlayed).toRow())
            dao.removeActivity(session.id)
        }
        snapshot()
    }

    suspend fun saveGoal(childId: String, title: String, imageUri: String?, target: Int, newGoal: Boolean): FamilySnapshot = withContext(Dispatchers.IO) {
        require(title.isNotBlank() && title.length <= 40 && target in 1..30)
        db.runInTransaction {
            val current = requireNotNull(dao.goal(childId)).toModel()
            val goal = if (newGoal) RewardGoal(UUID.randomUUID().toString(), title, imageUri, target,
                startStars = dao.stars(childId)) else current.copy(title = title, imageUri = imageUri, target = target)
            dao.putGoal(goal.copy(celebrationPending = goal.celebrationPending ||
                (!goal.celebrated && goal.reached(dao.stars(childId)))).toRow(childId))
        }
        snapshot()
    }

    suspend fun acknowledgeGoal(childId: String): FamilySnapshot = withContext(Dispatchers.IO) {
        db.runInTransaction {
            val goal = requireNotNull(dao.goal(childId))
            goal.celebrated = true; goal.celebrationPending = false; dao.putGoal(goal)
        }
        snapshot()
    }

    private fun snapshot(): FamilySnapshot = db.runInTransaction<FamilySnapshot> {
        val children = dao.children().map { it.toModel() }
        val selected = prefs.getString("active_child", DEFAULT_CHILD_ID)
        val active = children.firstOrNull { it.id == selected } ?: children.first()
        FamilySnapshot(children, active.id, dao.stars(active.id), requireNotNull(dao.goal(active.id)).toModel(),
            dao.activities(active.id).map { sessionFromJson(JSONObject(it.payload)) },
            dao.records(active.id).map { recordFromJson(JSONObject(it.payload)) })
    }

    companion object {
        @Volatile private var instance: HeruDatabase? = null
        private fun database(context: Context): HeruDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, HeruDatabase::class.java, "heru-family.db").build().also { instance = it }
        }
    }
}

private fun ChildProfile.toRow() = HeruDatabase.ChildRow().also { row ->
    row.id = id; row.name = name; row.createdAt = createdAt
    row.plans = JSONObject().apply { plans.forEach { (routine, count) -> put(routine.name, count) } }.toString()
}
private fun HeruDatabase.ChildRow.toModel(): ChildProfile {
    val json = JSONObject(plans)
    return ChildProfile(id, name, Routine.entries.mapNotNull { routine ->
        json.optInt(routine.name).takeIf { it in 1..3 }?.let { routine to it }
    }.toMap(), createdAt)
}
private fun RewardGoal.toRow(child: String) = HeruDatabase.GoalRow().also {
    it.id = id; it.childId = child; it.title = title; it.imageUri = imageUri
    it.target = target; it.startStars = startStars; it.celebrated = celebrated; it.celebrationPending = celebrationPending
}
private fun HeruDatabase.GoalRow.toModel() = RewardGoal(id, title, imageUri, target, startStars, celebrated, celebrationPending)

private fun CallSession.toRow() = HeruDatabase.ActivityRow().also {
    it.id = id; it.childId = childId; it.routine = routine.name; it.startedAt = startedAt
    it.payload = JSONObject().put("id", id).put("child", childId).put("routine", routine.name)
        .put("at", startedAt).put("level", level).put("guardian", guardian.name)
        .put("phase", phase.name).put("followUps", JSONArray(followUpLevels)).put("praise", praiseLevel)
        .put("entry", entryMode.name).put("warning", warningPlayed)
        .put("rewardReturnPhase", rewardReturnPhase?.name).toString()
}
private fun sessionFromJson(json: JSONObject) = CallSession(
    id = json.getString("id"), level = json.getInt("level"), guardian = Guardian.valueOf(json.getString("guardian")),
    startedAt = json.getLong("at"), phase = Phase.valueOf(json.getString("phase")),
    followUpLevels = json.getJSONArray("followUps").let { a -> (0 until a.length()).map { a.getInt(it) } },
    routine = Routine.valueOf(json.getString("routine")), childId = json.getString("child"),
    praiseLevel = json.getInt("praise"), entryMode = EntryMode.valueOf(json.getString("entry")),
    warningPlayed = json.getBoolean("warning"),
    rewardReturnPhase = json.optString("rewardReturnPhase").takeIf { it.isNotBlank() && it != "null" }
        ?.let(Phase::valueOf)
)
private fun SessionRecord.toRow() = HeruDatabase.RecordRow().also {
    it.id = id; it.childId = childId; it.finishedAt = finishedAt
    it.payload = JSONObject().put("id", id).put("child", childId).put("at", startedAt).put("finished", finishedAt)
        .put("level", level).put("guardian", guardian.name).put("outcome", outcome.name)
        .put("routine", routine.name).put("followUps", JSONArray(followUpLevels)).put("entry", entryMode.name)
        .put("warning", warningPlayed).put("star", starAwarded).toString()
}
private fun recordFromJson(json: JSONObject) = SessionRecord(
    id = json.getString("id"), startedAt = json.getLong("at"), level = json.getInt("level"),
    guardian = Guardian.valueOf(json.getString("guardian")), outcome = Outcome.valueOf(json.getString("outcome")),
    followUpLevels = json.getJSONArray("followUps").let { a -> (0 until a.length()).map { a.getInt(it) } },
    routine = Routine.valueOf(json.getString("routine")), childId = json.getString("child"), finishedAt = json.getLong("finished"),
    entryMode = EntryMode.valueOf(json.getString("entry")), warningPlayed = json.getBoolean("warning"), starAwarded = json.getBoolean("star")
)
