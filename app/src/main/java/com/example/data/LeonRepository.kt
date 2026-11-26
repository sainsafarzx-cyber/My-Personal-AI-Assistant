package com.example.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LeonRepository(
    private val database: LeonDatabase,
    private val appContext: Context? = null
) {
    private val prefsDao = database.userPreferencesDao()
    private val memoryDao = database.memoryDao()
    private val routineDao = database.routineDao()
    private val taskDao = database.taskDao()
    private val conversationDao = database.conversationDao()

    private val identityPrefs by lazy {
        appContext?.applicationContext?.getSharedPreferences(CREATOR_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun readFallbackCreatorName(): String {
        val spName = identityPrefs?.getString(KEY_OWNER_NAME, "").orEmpty().trim()
        if (spName.isNotEmpty()) return spName
        val resName = runCatching {
            appContext?.getString(com.example.R.string.default_creator_name).orEmpty().trim()
        }.getOrDefault("")
        return resName
    }

    private fun readFallbackTagline(): String =
        identityPrefs?.getString(KEY_CREATOR_TAGLINE, "").orEmpty().trim()

    private fun persistIdentityCache(ownerName: String, tagline: String) {
        identityPrefs?.edit {
            putString(KEY_OWNER_NAME, ownerName.trim().take(60))
            putString(KEY_CREATOR_TAGLINE, tagline.trim().take(160))
        }
    }

    // --- USER PREFERENCES & OWNER IDENTITY ---

    fun observePreferences(): Flow<UserPreferences> =
        prefsDao.observePreferences().map { stored ->
            val base = stored ?: UserPreferences()
            if (base.ownerName.isBlank()) {
                val cached = readFallbackCreatorName()
                if (cached.isNotBlank()) base.copy(ownerName = cached, creatorTagline = base.creatorTagline.ifBlank { readFallbackTagline() })
                else base
            } else {
                base
            }
        }

    suspend fun ensureDefaultPreferences(): Result<Unit> {
        return try {
            val existing = prefsDao.getPreferences()
            val cachedName = readFallbackCreatorName()
            val cachedTagline = readFallbackTagline()
            if (existing == null) {
                prefsDao.upsertPreferences(
                    UserPreferences(
                        ownerName = cachedName,
                        creatorTagline = cachedTagline,
                        memoryConsentGranted = true
                    )
                )
            } else if (existing.ownerName.isBlank() && cachedName.isNotBlank()) {
                prefsDao.upsertPreferences(
                    existing.copy(
                        ownerName = cachedName,
                        creatorTagline = existing.creatorTagline.ifBlank { cachedTagline },
                        memoryConsentGranted = true,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class BackgroundSnapshot(
        val preferences: UserPreferences,
        val memories: List<MemoryItem>,
        val routines: List<AutomationRoutine>,
        val tasks: List<DailyTask>,
        val history: List<ConversationMessage>
    )

    suspend fun loadBackgroundSnapshot(): BackgroundSnapshot {
        val prefs = getPreferencesOnce()
        return BackgroundSnapshot(
            preferences = prefs,
            memories = memoryDao.getMemoriesOnce(),
            routines = routineDao.getRoutinesOnce(),
            tasks = taskDao.getTasksOnce(),
            history = conversationDao.getRecentConversationsOnce()
        )
    }

    suspend fun getPreferencesOnce(): UserPreferences {
        val base = prefsDao.getPreferences() ?: UserPreferences()
        return if (base.ownerName.isBlank()) {
            val cached = readFallbackCreatorName()
            if (cached.isNotBlank()) base.copy(ownerName = cached, creatorTagline = base.creatorTagline.ifBlank { readFallbackTagline() })
            else base
        } else {
            base
        }
    }

    suspend fun updatePreferences(
        defaultLanguage: AssistantLanguage? = null,
        alwaysReadyEnabled: Boolean? = null,
        lockScreenModeEnabled: Boolean? = null,
        memoryConsentGranted: Boolean? = null,
        voiceSpeed: Double? = null
    ): Result<Unit> {
        return try {
            val current = getPreferencesOnce()
            val updated = current.copy(
                assistantName = "LEON AI",
                defaultLanguage = defaultLanguage?.code ?: current.defaultLanguage,
                alwaysReadyEnabled = alwaysReadyEnabled ?: current.alwaysReadyEnabled,
                lockScreenModeEnabled = lockScreenModeEnabled ?: current.lockScreenModeEnabled,
                memoryConsentGranted = memoryConsentGranted ?: current.memoryConsentGranted,
                voiceSpeed = voiceSpeed?.coerceIn(0.5, 2.0) ?: current.voiceSpeed,
                updatedAtMillis = System.currentTimeMillis()
            )
            prefsDao.upsertPreferences(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setOwnerIdentity(
        ownerName: String,
        creatorTagline: String = "",
        @Suppress("UNUSED_PARAMETER") syncCloud: Boolean = true
    ): Result<UserPreferences> {
        val cleanName = ownerName.trim().take(60)
        val cleanTagline = creatorTagline.trim().take(160)
        return try {
            require(cleanName.isNotEmpty()) { "Owner name cannot be empty." }
            val current = getPreferencesOnce()
            val resolvedTagline = cleanTagline.ifBlank { current.creatorTagline }
            val updated = current.copy(
                ownerName = cleanName,
                creatorTagline = resolvedTagline,
                memoryConsentGranted = true,
                updatedAtMillis = System.currentTimeMillis()
            )
            prefsDao.upsertPreferences(updated)
            persistIdentityCache(cleanName, resolvedTagline)

            // Also store/update permanent memory fact so ALI AI & Memory Vault both showcase it forever
            saveMemory(
                key = OWNER_IDENTITY_MEMORY_KEY,
                value = "मेरा नाम $cleanName है (App Owner & Creator — कोई ऐप डाउनलोड करे तो $cleanName का ही नाम बताना है)",
                category = MemoryCategory.FACT,
                isPermanent = true
            )
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- LONG-TERM & FOREVER MEMORIES ---

    fun observeMemories(): Flow<List<MemoryItem>> = memoryDao.observeMemories()

    suspend fun getMemoriesOnce(): List<MemoryItem> = memoryDao.getMemoriesOnce()

    suspend fun saveMemory(
        key: String,
        value: String,
        category: MemoryCategory,
        isPermanent: Boolean = true
    ): Result<MemoryItem> {
        val cleanKey = key.trim().take(80)
        val cleanVal = value.trim().take(500)
        return try {
            require(cleanKey.isNotEmpty()) { "Memory title cannot be empty." }
            require(cleanVal.isNotEmpty()) { "Memory detail cannot be empty." }
            val now = System.currentTimeMillis()
            val existing = memoryDao.findByKeyIgnoreCase(cleanKey)
            val docId = existing?.cloudDocId?.ifBlank { FirebaseCloudSync.stableMemoryDocId(cleanKey) }
                ?: FirebaseCloudSync.stableMemoryDocId(cleanKey)

            val item = MemoryItem(
                id = existing?.id ?: 0L,
                memoryKey = cleanKey,
                memoryValue = cleanVal,
                category = category.wireValue,
                approved = true,
                isPermanent = isPermanent || (existing?.isPermanent == true),
                cloudDocId = docId,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            val rowId = memoryDao.insertMemory(item)
            val savedItem = if (item.id == 0L) item.copy(id = rowId) else item
            Result.success(savedItem)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeCloudMemory(
        cloudDocId: String,
        key: String,
        value: String,
        category: MemoryCategory,
        isPermanent: Boolean
    ) {
        val cleanKey = key.trim().take(80)
        val cleanVal = value.trim().take(500)
        if (cleanKey.isEmpty() || cleanVal.isEmpty()) return
        val existingByDoc = memoryDao.findByCloudDocId(cloudDocId)
        val existingByKey = existingByDoc ?: memoryDao.findByKeyIgnoreCase(cleanKey)
        val now = System.currentTimeMillis()
        val item = MemoryItem(
            id = existingByKey?.id ?: 0L,
            memoryKey = cleanKey,
            memoryValue = cleanVal,
            category = category.wireValue,
            approved = true,
            isPermanent = isPermanent,
            cloudDocId = cloudDocId.ifBlank { FirebaseCloudSync.stableMemoryDocId(cleanKey) },
            createdAtMillis = existingByKey?.createdAtMillis ?: now,
            updatedAtMillis = now
        )
        memoryDao.insertMemory(item)
    }

    suspend fun getMemoryById(memoryId: Long): MemoryItem? =
        memoryDao.getMemoriesOnce().firstOrNull { it.id == memoryId }

    suspend fun deleteMemory(memoryId: Long): Result<Unit> {
        return try {
            memoryDao.deleteMemoryById(memoryId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- AUTOMATION ROUTINES ---

    fun observeRoutines(): Flow<List<AutomationRoutine>> = routineDao.observeRoutines()

    suspend fun createRoutine(
        title: String,
        triggerPhrase: String,
        steps: List<String>
    ): Result<Unit> {
        val cleanTitle = title.trim().take(80)
        val cleanTrigger = triggerPhrase.trim().take(120)
        val cleanSteps = steps.map { it.trim().take(240) }.filter { it.isNotEmpty() }.take(10)
        return try {
            require(cleanTitle.isNotEmpty()) { "Routine title is required." }
            require(cleanTrigger.isNotEmpty()) { "Trigger phrase is required." }
            require(cleanSteps.isNotEmpty()) { "At least one step is required." }
            val now = System.currentTimeMillis()
            routineDao.insertRoutine(
                AutomationRoutine(
                    title = cleanTitle,
                    triggerPhrase = cleanTrigger,
                    steps = cleanSteps,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleRoutine(routineId: Long, enabled: Boolean): Result<Unit> {
        return try {
            routineDao.setRoutineEnabled(routineId, enabled, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(routineId: Long): Result<Unit> {
        return try {
            routineDao.deleteRoutineById(routineId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- DAILY TASKS & REMINDERS ---

    fun observeTasks(): Flow<List<DailyTask>> = taskDao.observeTasks()

    suspend fun createTask(
        title: String,
        notes: String = "",
        dueLabel: String = "",
        priority: TaskPriority = TaskPriority.MEDIUM
    ): Result<Unit> {
        val cleanTitle = title.trim().take(120)
        val cleanNotes = notes.trim().take(500)
        val cleanDue = dueLabel.trim().take(80)
        return try {
            require(cleanTitle.isNotEmpty()) { "Task title is required." }
            val now = System.currentTimeMillis()
            taskDao.insertTask(
                DailyTask(
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueLabel = cleanDue,
                    priority = priority.wireValue,
                    completed = false,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTaskCompleted(taskId: Long, completed: Boolean): Result<Unit> {
        return try {
            taskDao.setTaskCompleted(taskId, completed, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: Long): Result<Unit> {
        return try {
            taskDao.deleteTaskById(taskId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- CONVERSATIONS ---

    fun observeConversations(): Flow<List<ConversationMessage>> =
        conversationDao.observeRecentConversations()

    suspend fun addConversationMessage(
        role: String,
        text: String,
        language: AssistantLanguage,
        actionSummary: String = ""
    ): Result<Unit> {
        val cleanText = text.trim().take(4000)
        val cleanSummary = actionSummary.trim().take(400)
        val safeRole = if (role == "model") "model" else "user"
        return try {
            require(cleanText.isNotEmpty())
            conversationDao.insertMessage(
                ConversationMessage(
                    role = safeRole,
                    text = cleanText,
                    language = language.code,
                    actionSummary = cleanSummary,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearConversations(): Result<Unit> {
        return try {
            conversationDao.clearAll()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val CREATOR_PREFS_NAME = "leon_creator_prefs"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_CREATOR_TAGLINE = "creator_tagline"
        const val OWNER_IDENTITY_MEMORY_KEY = "Owner & App Creator"
    }
}
