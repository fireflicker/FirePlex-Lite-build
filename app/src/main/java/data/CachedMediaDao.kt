package com.fireflicker.fireplex2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class CachedMediaDao {
    @Query("SELECT * FROM cached_media WHERE categoryKey = :categoryKey ORDER BY position LIMIT :limit OFFSET :offset")
    abstract suspend fun page(categoryKey: String, offset: Int, limit: Int): List<CachedMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(items: List<CachedMediaEntity>)

    @Query("DELETE FROM cached_media WHERE categoryKey = :categoryKey")
    abstract suspend fun deleteCategory(categoryKey: String)

    @Query("DELETE FROM cached_media WHERE categoryKey != :categoryKey")
    abstract suspend fun deleteOtherCategories(categoryKey: String)

    @Query("DELETE FROM cached_media")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceFirstPage(categoryKey: String, items: List<CachedMediaEntity>) {
        deleteCategory(categoryKey)
        deleteOtherCategories(categoryKey)
        if (items.isNotEmpty()) insert(items)
    }
}
