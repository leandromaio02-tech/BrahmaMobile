package com.jarvis.assistant

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class ChatMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null
)

data class ToolEvent(
    val step: String,
    val status: String,
    val detail: String,
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val role: String = "fast",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    val streaming: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val customHeaders: String = "{}",
    val timeoutMs: Long = 60000,
    val latencyMs: Long = 0,
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long,
    val modelId: String = ""
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val toolEvents: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val title: String,
    val content: String,
    val tags: String = "",
    val importance: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String = "queued",
    val plan: String = "[]",
    val progress: Int = 0,
    val result: String = "",
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val dueAt: Long,
    val repeatMinutes: Long = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val trigger: String,
    val condition: String = "",
    val action: String,
    val enabled: Boolean = true,
    val lastRun: Long = 0
)

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val detail: String,
    val at: Long = System.currentTimeMillis()
)

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt") fun all(): Flow<List<ProviderEntity>>
    @Query("SELECT * FROM providers ORDER BY createdAt") suspend fun list(): List<ProviderEntity>
    @Query("SELECT * FROM providers WHERE id = :id") suspend fun get(id: String): ProviderEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ProviderEntity)
    @Update suspend fun update(item: ProviderEntity)
    @Delete suspend fun delete(item: ProviderEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun all(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id") fun messages(id: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id DESC LIMIT 1") suspend fun last(id: String): MessageEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertConversation(item: ConversationEntity)
    @Insert suspend fun insertMessage(item: MessageEntity): Long
    @Query("DELETE FROM messages WHERE conversationId = :id") suspend fun clear(id: String)
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteConversation(id: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC") fun all(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' ORDER BY importance DESC") fun search(q: String): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories ORDER BY importance DESC, updatedAt DESC LIMIT :limit") suspend fun relevant(limit: Int): List<MemoryEntity>
    @Insert suspend fun insert(item: MemoryEntity): Long
    @Update suspend fun update(item: MemoryEntity)
    @Delete suspend fun delete(item: MemoryEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") fun all(): Flow<List<TaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: TaskEntity)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueAt") fun all(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE enabled = 1 AND dueAt <= :now") suspend fun due(now: Long): List<ReminderEntity>
    @Insert suspend fun insert(item: ReminderEntity): Long
    @Update suspend fun update(item: ReminderEntity)
    @Delete suspend fun delete(item: ReminderEntity)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY id DESC") fun all(): Flow<List<AutomationEntity>>
    @Insert suspend fun insert(item: AutomationEntity): Long
    @Update suspend fun update(item: AutomationEntity)
    @Delete suspend fun delete(item: AutomationEntity)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY at DESC LIMIT 200") fun recent(): Flow<List<ActivityEntity>>
    @Insert suspend fun insert(item: ActivityEntity)
    @Query("DELETE FROM activity") suspend fun clear()
}

@Database(
    entities = [ProviderEntity::class, ConversationEntity::class, MessageEntity::class,
        MemoryEntity::class, TaskEntity::class, ReminderEntity::class,
        AutomationEntity::class, ActivityEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun automationDao(): AutomationDao
    abstract fun activityDao(): ActivityDao
}
