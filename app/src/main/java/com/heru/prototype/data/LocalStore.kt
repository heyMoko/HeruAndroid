package com.heru.prototype.data

import android.content.Context
import com.heru.prototype.domain.*
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context, preferencesName: String = "heru_prototype") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    fun level() = prefs.getInt("level", 1).coerceIn(1, 5)
    fun selfViewEnabled() = prefs.getBoolean("self_view_enabled", false)
    fun setSelfViewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("self_view_enabled", enabled).apply()
    }
    fun stars() = prefs.getInt("stars", 0).coerceAtLeast(0)
    fun rewardTitle() = prefs.getString("reward_title", "놀이터 가기") ?: "놀이터 가기"
    fun rewardImageUri() = prefs.getString("reward_image_uri", null)
    fun rewardTarget() = prefs.getInt("reward_target", 8).coerceIn(1, 50)
    fun setReward(title: String, imageUri: String?, target: Int) {
        prefs.edit().putString("reward_title", title).putString("reward_image_uri", imageUri)
            .putInt("reward_target", target.coerceIn(1, 50)).apply()
    }
    fun addStar(): Int {
        val next = stars() + 1
        prefs.edit().putInt("stars", next).apply()
        return next
    }
    fun guardian() = runCatching {
        Guardian.valueOf(prefs.getString("guardian", "MOM") ?: "MOM")
    }.getOrDefault(Guardian.MOM)
    fun routine() = runCatching {
        Routine.valueOf(prefs.getString("routine", Routine.MEAL.name) ?: Routine.MEAL.name)
    }.getOrDefault(Routine.MEAL)
    fun selection(level: Int, guardian: Guardian, routine: Routine) {
        prefs.edit().putInt("level", level).putString("guardian", guardian.name)
            .putString("routine", routine.name).apply()
    }
    fun records(): List<SessionRecord> = runCatching {
        val array = JSONArray(prefs.getString("records", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val row = array.getJSONObject(index)
            val followUps = row.optJSONArray("followUpLevels")?.let { levels ->
                (0 until levels.length()).mapNotNull { i -> levels.optInt(i).takeIf { it in 1..5 } }
            } ?: row.optInt("followUpLevel").takeIf { it in 1..5 }?.let(::listOf).orEmpty()
            SessionRecord(row.getString("id"), row.getLong("at"), row.getInt("level"),
                Guardian.valueOf(row.getString("guardian")),
                Outcome.valueOf(row.getString("outcome")),
                followUps,
                runCatching { Routine.valueOf(row.optString("routine", Routine.MEAL.name)) }
                    .getOrDefault(Routine.MEAL))
        }.getOrNull() }
    }.getOrDefault(emptyList())

    @Synchronized
    fun append(record: SessionRecord): Boolean {
        val existing = records()
        if (existing.any { it.id == record.id }) return true
        val array = JSONArray()
        (listOf(record) + existing).take(200).forEach { row ->
            val json = JSONObject().put("id", row.id).put("at", row.startedAt)
                .put("level", row.level).put("guardian", row.guardian.name)
                .put("outcome", row.outcome.name)
            json.put("followUpLevels", JSONArray().apply { row.followUpLevels.forEach(::put) })
            json.put("routine", row.routine.name)
            array.put(json)
        }
        return prefs.edit().putString("records", array.toString()).commit()
    }
    fun clear() = prefs.edit().remove("records").commit()
}
