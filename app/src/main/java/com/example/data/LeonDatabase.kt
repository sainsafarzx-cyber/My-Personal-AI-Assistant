package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class LeonTypeConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun observePreferences(): Flow<UserPreferences?>

    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferences(): UserPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(prefs: UserPreferences)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY isPermanent DESC, updatedAtMillis DESC")
    fun observeMemories(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memories ORDER BY isPermanent DESC, updatedAtMillis DESC")
    suspend fun getMemoriesOnce(): List<MemoryItem>

    @Query("SELECT * FROM memories WHERE LOWER(memoryKey) = LOWER(:key) LIMIT 1")
    suspend fun findByKeyIgnoreCase(key: String): MemoryItem?

    @Query("SELECT * FROM memories WHERE cloudDocId = :cloudDocId LIMIT 1")
    suspend fun findByCloudDocId(cloudDocId: String): MemoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(item: MemoryItem): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAtMillis DESC")
    fun observeRoutines(): Flow<List<AutomationRoutine>>

    @Query("SELECT * FROM routines ORDER BY createdAtMillis DESC")
    suspend fun getRoutinesOnce(): List<AutomationRoutine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: AutomationRoutine)

    @Query("UPDATE routines SET enabled = :enabled, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setRoutineEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAtMillis DESC")
    fun observeTasks(): Flow<List<DailyTask>>

    @Query("SELECT * FROM tasks ORDER BY createdAtMillis DESC")
    suspend fun getTasksOnce(): List<DailyTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DailyTask)

    @Query("UPDATE tasks SET completed = :completed, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM (SELECT * FROM conversations ORDER BY createdAtMillis DESC, id DESC LIMIT 50) ORDER BY createdAtMillis ASC, id ASC")
    fun observeRecentConversations(): Flow<List<ConversationMessage>>

    @Query("SELECT * FROM (SELECT * FROM conversations ORDER BY createdAtMillis DESC, id DESC LIMIT 20) ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getRecentConversationsOnce(): List<ConversationMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessage)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}

@Database(
    entities = [
        UserPreferences::class,
        MemoryItem::class,
        AutomationRoutine::class,
        DailyTask::class,
        ConversationMessage::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(LeonTypeConverters::class)
abstract class LeonDatabase : RoomDatabase() {
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun memoryDao(): MemoryDao
    abstract fun routineDao(): RoutineDao
    abstract fun taskDao(): TaskDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            sqlTypeAndDefault: String
        ) {
            if (!hasColumn(db, table, column)) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $sqlTypeAndDefault")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "user_preferences", "lockScreenModeEnabled", "INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "user_preferences", "lockScreenModeEnabled", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "user_preferences", "ownerName", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "user_preferences", "creatorTagline", "TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE user_preferences SET memoryConsentGranted = 1")
                addColumnIfMissing(db, "memories", "isPermanent", "INTEGER NOT NULL DEFAULT 1")
                addColumnIfMissing(db, "memories", "cloudDocId", "TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: LeonDatabase? = null

        fun getInstance(context: Context): LeonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LeonDatabase::class.java,
                    "leon_ai_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
