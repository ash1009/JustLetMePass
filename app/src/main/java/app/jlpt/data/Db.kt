package app.jlpt.data

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
import kotlinx.coroutines.flow.Flow

/** How well one thing (a kana, word, kanji, grammar point or phrase) is known. */
@Entity
data class ItemState(
    @PrimaryKey val id: String,
    val kind: String,
    val stage: Int = 0,
    val due: Long = 0,
    val lapses: Int = 0,
    val right: Int = 0,
    val wrong: Int = 0,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
)

/** One day of study. [day] is like 2026-10-13. */
@Entity
data class DayLog(
    @PrimaryKey val day: String,
    val seconds: Int = 0,
    val answers: Int = 0,
    val correct: Int = 0,
    val newItems: Int = 0,
)

/** A reading or listening exercise that has been done. */
@Entity
data class ExerciseDone(@PrimaryKey val id: String, val score: Int, val total: Int, val time: Long)

@Entity
data class ChatMsg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val text: String,
    val time: Long,
)

@Entity
data class TestResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: Int,
    val time: Long,
    /** Section name to "got/total", like vocab=12/15;grammar=6/10. */
    val sections: String,
    val score: Int,
    val total: Int,
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM ItemState")
    suspend fun allStates(): List<ItemState>

    @Query("SELECT * FROM ItemState")
    fun statesFlow(): Flow<List<ItemState>>

    @Query("SELECT * FROM ItemState WHERE id = :id")
    suspend fun state(id: String): ItemState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(s: ItemState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStates(s: List<ItemState>)

    @Query("SELECT * FROM DayLog WHERE day = :day")
    suspend fun day(day: String): DayLog?

    @Query("SELECT * FROM DayLog ORDER BY day DESC LIMIT 400")
    fun daysFlow(): Flow<List<DayLog>>

    @Query("SELECT * FROM DayLog ORDER BY day DESC LIMIT 400")
    suspend fun days(): List<DayLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDay(d: DayLog)

    @Query("SELECT * FROM ExerciseDone")
    fun exercisesFlow(): Flow<List<ExerciseDone>>

    @Query("SELECT * FROM ExerciseDone")
    suspend fun exercises(): List<ExerciseDone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putExercise(e: ExerciseDone)

    @Query("SELECT * FROM ChatMsg ORDER BY id")
    fun chatFlow(): Flow<List<ChatMsg>>

    @Query("SELECT * FROM ChatMsg ORDER BY id")
    suspend fun chat(): List<ChatMsg>

    @Insert
    suspend fun addChat(m: ChatMsg): Long

    @Query("DELETE FROM ChatMsg")
    suspend fun clearChat()

    @Query("SELECT * FROM TestResult ORDER BY time DESC")
    fun testsFlow(): Flow<List<TestResult>>

    @Insert
    suspend fun addTest(t: TestResult)

    @Query("DELETE FROM ItemState")
    suspend fun clearStates()

    @Query("DELETE FROM DayLog")
    suspend fun clearDays()

    @Query("DELETE FROM ExerciseDone")
    suspend fun clearExercises()

    @Query("DELETE FROM TestResult")
    suspend fun clearTests()
}

@Database(
    entities = [ItemState::class, DayLog::class, ExerciseDone::class, ChatMsg::class, TestResult::class],
    version = 1,
    exportSchema = false,
)
abstract class StudyDb : RoomDatabase() {
    abstract fun dao(): StudyDao

    companion object {
        fun open(context: Context): StudyDb =
            Room.databaseBuilder(context, StudyDb::class.java, "study.db").build()
    }
}
