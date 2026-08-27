package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.CallRecordDao
import com.example.data.dao.VehicleDao
import com.example.data.model.CallRecord
import com.example.data.model.StringListConverters
import com.example.data.model.Vehicle

@Database(entities = [CallRecord::class, Vehicle::class], version = 2, exportSchema = false)
@TypeConverters(StringListConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callRecordDao(): CallRecordDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_crm_database.db"
                )
                    .fallbackToDestructiveMigration()
                    // FloatingWindowOverlayService runs in a separate process (see
                    // AndroidManifest.xml) and opens its own AppDatabase instance against the
                    // same underlying file - this propagates Flow-based query invalidation
                    // across that process boundary instead of only within one process.
                    .enableMultiInstanceInvalidation()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
