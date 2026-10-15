package com.gazette.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(entry: LedgerEntry)

    @Query("SELECT * FROM entries ORDER BY epochMs DESC, id DESC")
    fun all(): Flow<List<LedgerEntry>>
}

@Database(entities = [LedgerEntry::class], version = 1, exportSchema = false)
abstract class LedgerDb : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var instance: LedgerDb? = null

        fun get(context: Context): LedgerDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LedgerDb::class.java, "ledger.db")
                .build()
                .also { instance = it }
        }
    }
}
