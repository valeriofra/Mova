package io.github.valeriofra.mova.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = true)
abstract class MovaDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun create(context: Context): MovaDatabase =
            Room.databaseBuilder(context, MovaDatabase::class.java, "mova.db").build()
    }
}
