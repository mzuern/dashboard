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

/** The current in-progress candidate rows, so an interrupted review can resume. There is only ever one draft (id = 0). No history table - that's intentionally out of scope for this version. */
@Entity(tableName = "draft")
data class DraftEntity(
    @PrimaryKey val id: Int = 0,
    val rowsJson: String,
    val updatedAt: Long,
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

@Database(entities = [DraftEntity::class], version = 1, exportSchema = false)
abstract class DraftDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile private var instance: DraftDatabase? = null

        fun get(context: Context): DraftDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, DraftDatabase::class.java, "draft.db")
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
