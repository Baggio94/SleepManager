package com.med.sleepmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object EventHistoryStore {
    private const val PREFS = "event_history"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 20

    data class Event(
        val timestamp: Long,
        val message: String
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(context: Context, message: String, timestamp: Long = System.currentTimeMillis()) {
        val existing = recent(context).toMutableList()
        existing.add(0, Event(timestamp, message))

        val json = JSONArray()
        existing.take(MAX_EVENTS).forEach { event ->
            json.put(
                JSONObject()
                    .put("timestamp", event.timestamp)
                    .put("message", event.message)
            )
        }

        prefs(context).edit()
            .putString(KEY_EVENTS, json.toString())
            .apply()
    }

    fun recent(context: Context): List<Event> {
        val raw = prefs(context).getString(KEY_EVENTS, null) ?: return emptyList()

        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val timestamp = item.optLong("timestamp", 0L)
                    val message = item.optString("message", "")
                    if (timestamp > 0L && message.isNotBlank()) {
                        add(Event(timestamp, message))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
