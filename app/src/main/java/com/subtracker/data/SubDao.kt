package com.subtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Upsert
    suspend fun upsert(sub: Subscription)

    /** Restore replaces the whole table; callers wrap both calls in one transaction. */
    @Upsert
    suspend fun upsertAll(subs: List<Subscription>)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()

    @Delete
    suspend fun delete(sub: Subscription)
}
