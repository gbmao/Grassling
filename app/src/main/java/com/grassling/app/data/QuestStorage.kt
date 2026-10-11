package com.grassling.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.grassling.app.quest.QuestTrackingMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val QUESTS_FILE_NAME = "quests.json"

private fun currentTimestamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
}

class QuestStorage(context: Context) {
    data class StoredQuest(
        val description: String,
        val targetSteps: Int,
        val targetMinutes: Int = 0,
        val trackingMode: QuestTrackingMode = QuestTrackingMode.NONE,
        val completed: Boolean = false,
        val creationDate: String = currentTimestamp(),
        val completedAt: String? = null,
    )

    private val questsFile = File(context.applicationContext.filesDir, QUESTS_FILE_NAME)

    init {
        ensureFileExists()
    }

    @Synchronized
    fun loadQuests(): List<StoredQuest> {
        ensureFileExists()
        val jsonArray = JSONArray(questsFile.readText(Charsets.UTF_8))

        return List(jsonArray.length()) { index ->
            val quest = jsonArray.getJSONObject(index)
            val description = quest.getString("description")
            val targetSteps = quest.getInt("targetSteps")
            val targetMinutes = quest.optInt("targetMinutes", inferTargetMinutes(description))
            val trackingMode = quest.optString(
                "trackingMode",
                when {
                    targetSteps > 0 -> QuestTrackingMode.STEPS.name
                    targetMinutes > 0 -> QuestTrackingMode.MINUTES.name
                    description.startsWith("Walk ", ignoreCase = true) ||
                        description.startsWith("Take a short walk", ignoreCase = true) -> QuestTrackingMode.STEPS.name
                    else -> QuestTrackingMode.NONE.name
                },
            ).let { value -> runCatching { QuestTrackingMode.valueOf(value) }.getOrDefault(QuestTrackingMode.NONE) }
            StoredQuest(
                description = description,
                targetSteps = targetSteps,
                targetMinutes = targetMinutes,
                trackingMode = trackingMode,
                completed = quest.getBoolean("completed"),
                creationDate = quest.getString("creationDate"),
                completedAt = if (quest.isNull("completedAt")) null else quest.getString("completedAt"),
            )
        }
    }

    @Synchronized
    fun saveQuests(quests: List<StoredQuest>) {
        ensureFileExists()
        val jsonArray = JSONArray()

        quests.forEach { quest ->
            jsonArray.put(
                JSONObject()
                    .put("description", quest.description)
                    .put("targetSteps", quest.targetSteps)
                    .put("targetMinutes", quest.targetMinutes)
                    .put("trackingMode", quest.trackingMode.name)
                    .put("completed", quest.completed)
                    .put("creationDate", quest.creationDate)
                    .put("completedAt", quest.completedAt ?: JSONObject.NULL),
            )
        }

        questsFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
    }

    @Synchronized
    fun addQuest(quest: StoredQuest) {
        saveQuests(loadQuests() + quest)
    }

    @Synchronized
    fun getActiveQuest(): StoredQuest? = loadQuests().lastOrNull { !it.completed }

    @Synchronized
    fun addActiveQuest(quest: StoredQuest): Boolean {
        val quests = loadQuests()
        if (quests.any { !it.completed }) return false
        saveQuests(quests + quest.copy(completed = false, completedAt = null))
        return true
    }

    private fun ensureFileExists() {
        questsFile.parentFile?.mkdirs()
        if (!questsFile.exists()) {
            questsFile.writeText("[]", Charsets.UTF_8)
        }
    }

    private fun inferTargetMinutes(description: String): Int =
        Regex("\\b(\\d+)\\s*[- ]?minutes?\\b", RegexOption.IGNORE_CASE)
            .find(description)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: 0

}
