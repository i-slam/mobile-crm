package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY endTimeMillis DESC")
    fun getAllRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE id = :id")
    fun getRecordById(id: Long): Flow<CallRecord?>

    @Query("SELECT * FROM call_records WHERE syncStatus = 'PENDING' ORDER BY endTimeMillis ASC")
    suspend fun getPendingSyncRecords(): List<CallRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CallRecord): Long

    @Update
    suspend fun updateRecord(record: CallRecord)

    @Delete
    suspend fun deleteRecord(record: CallRecord)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM call_records")
    suspend fun deleteAllRecords()

    @Query("SELECT COUNT(*) FROM call_records")
    fun getRecordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_records")
    suspend fun getRecordCountDirect(): Int

    @Query("SELECT COUNT(*) FROM call_records WHERE syncStatus = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}
