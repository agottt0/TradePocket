package com.tradelog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    /** IGNORE, not REPLACE: an already-imported message must never overwrite an edited row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(trade: Trade): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(trades: List<Trade>): List<Long>

    @Update
    suspend fun update(trade: Trade)

    @Delete
    suspend fun delete(trade: Trade)

    @Query("SELECT * FROM trades ORDER BY tradeDate DESC, id DESC")
    fun observeAll(): Flow<List<Trade>>

    @Query("SELECT * FROM trades ORDER BY tradeDate DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun byId(id: Long): Trade?

    @Query("SELECT COUNT(*) FROM trades")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM trades WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM trades WHERE messageKey = :key)")
    suspend fun exists(key: String): Boolean

}
