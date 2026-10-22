package com.heru.prototype.data

import android.content.Context
import com.heru.prototype.domain.*
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("heru_prototype", Context.MODE_PRIVATE)
    fun level() = prefs.getInt("level", 1).coerceIn(1, 5)
    fun stars() = prefs.getInt("stars", 0).coerceAtLeast(0)
    fun addStar(): Int {
        val next = stars() + 1
        prefs.edit().putInt("stars", next).apply()
        return next
    }
    fun guardian() = runCatching {
        Guardian.valueOf(prefs.getString("guardian", "MOM") ?: "MOM")
    }.getOrDefault(Guardian.MOM)
    fun selection(level: Int, guardian: Guardian) {
        prefs.edit().putInt("level", level).putString("guardian", guardian.name).apply()
    }
    fun records(): List<SessionRecord> = runCatching {
        val array = JSONArray(prefs.getString("records", "[]"))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            SessionRecord(row.getString("id"), row.getLong("at"), row.getInt("level"),
                Guardian.valueOf(row.getString("guardian")),
                Outcome.valueOf(row.getString("outcome")))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun append(record: SessionRecord): Boolean {
        val existing = records()
        if (existing.any { it.id == record.id }) return true
        val array = JSONArray()
        (listOf(record) + existing).take(200).forEach { row ->
            array.put(JSONObject().put("id", row.id).put("at", row.startedAt)
                .put("level", row.level).put("guardian", row.guardian.name)
                .put("outcome", row.outcome.name))
        }
        return prefs.edit().putString("records", array.toString()).commit()
    }
    fun clear() = prefs.edit().remove("records").commit()
}
