package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.CsvFileDao
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.CsvFileEntity

@Database(
    entities = [ProductEntity::class, CsvFileEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun csvFileDao(): CsvFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "products_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
