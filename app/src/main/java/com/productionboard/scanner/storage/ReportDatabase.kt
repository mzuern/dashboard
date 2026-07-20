package com.productionboard.scanner.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.productionboard.scanner.domain.ReviewRow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A single in-progress scan's rows, so an interrupted review can resume. There is only ever one draft (id = 0). */
@Entity(tableName = "draft")
data class DraftEntity(
    @PrimaryKey val id: Int = 0,
    val rowsJson: String,
    val updatedAt: Long,
)

/** A previously generated (not necessarily sent) daily report, kept as local history only. */
@Entity(tableName = "report_history")
data class ReportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val body: String,
    val createdAt: Long,
    val rowCount: Int,
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM draft WHERE id = 0")
    suspend fun get(): DraftEntity?

    @Query("SELECT * FROM draft WHERE id = 0")
    fun observe(): Flow<DraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity)

    @Query("DELETE FROM draft")
    suspend fun clear()
}

@Dao
interface ReportHistoryDao {
    @Query("SELECT * FROM report_history ORDER BY createdAt DESC LIMIT 20")
    fun recent(): Flow<List<ReportHistoryEntity>>

    @Query("SELECT * FROM report_history ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): ReportHistoryEntity?

    @Insert
    suspend fun insert(entity: ReportHistoryEntity): Long

    @Query("DELETE FROM report_history")
    suspend fun clearAll()
}

@Database(entities = [DraftEntity::class, ReportHistoryEntity::class], version = 1, exportSchema = false)
abstract class ReportDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun reportHistoryDao(): ReportHistoryDao

    companion object {
        @Volatile private var instance: ReportDatabase? = null

        fun get(context: Context): ReportDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, ReportDatabase::class.java, "reports.db")
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}

/** Room only stores strings/primitives well; rows are kept as a JSON blob rather than a normalized table since there's no need to query into individual fields. */
object RowsJsonConverter {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(rows: List<ReviewRow>): String = json.encodeToString(rows)
    fun decode(raw: String): List<ReviewRow> = json.decodeFromString(raw)
}
