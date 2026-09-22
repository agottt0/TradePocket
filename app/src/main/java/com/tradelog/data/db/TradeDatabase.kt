package com.tradelog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Trade::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TradeDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao

    companion object {
        @Volatile
        private var instance: TradeDatabase? = null

        fun get(context: Context): TradeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TradeDatabase::class.java,
                    "tradelog.db",
                ).build().also { instance = it }
            }
    }
}
