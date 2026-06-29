package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "csv_files")
data class CsvFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val importedAt: Long = System.currentTimeMillis(),
    val productCount: Int = 0
)
