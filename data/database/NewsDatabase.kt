package com.fitnesslemon.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [NewsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao

    companion object {
        @Volatile
        private var INSTANCE: NewsDatabase? = null

        fun getInstance(context: Context): NewsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context = context.applicationContext,
                    klass = NewsDatabase::class.java,
                    name = "news_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Конвертеры для списков
class Converters {
    @androidx.room.TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotEmpty() }
    }

    @androidx.room.TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}