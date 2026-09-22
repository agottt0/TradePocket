package com.tradelog.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun sideToString(side: Side): String = side.name

    @TypeConverter
    fun stringToSide(value: String): Side = Side.valueOf(value)
}
