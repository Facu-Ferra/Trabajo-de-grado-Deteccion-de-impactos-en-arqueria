package com.surendramaran.yolov8tflite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class, Round::class], version = 3, exportSchema = false)
abstract class ArcheryDatabase : RoomDatabase() {
    abstract fun archeryDao(): ArcheryDao

    companion object {
        @Volatile
        private var INSTANCE: ArcheryDatabase? = null

        fun getDatabase(context: Context): ArcheryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ArcheryDatabase::class.java,
                    "archery_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
