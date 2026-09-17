package com.fitnesslemon.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(news: List<NewsEntity>)

    @Query("SELECT * FROM news ORDER BY id DESC")
    suspend fun getAll(): List<NewsEntity>

    @Query("SELECT * FROM news ORDER BY id DESC")
    fun getAllFlow(): Flow<List<NewsEntity>>

    @Query("SELECT MAX(cachedAt) FROM news")
    suspend fun getLatestCachedAt(): Long?

    @Query("DELETE FROM news")
    suspend fun clearAll()

    @Query("SELECT * FROM news WHERE id = :id")
    suspend fun getById(id: Int): NewsEntity?
}
