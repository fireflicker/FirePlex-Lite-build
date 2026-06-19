package com.fireflicker.fireplex2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedMediaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FirePlexDatabase : RoomDatabase() {
    abstract fun cachedMediaDao(): CachedMediaDao

    companion object {
        @Volatile
        private var instance: FirePlexDatabase? = null

        fun get(context: Context): FirePlexDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FirePlexDatabase::class.java,
                    "fireplex_media.db"
                ).build().also { instance = it }
            }
        }
    }
}
