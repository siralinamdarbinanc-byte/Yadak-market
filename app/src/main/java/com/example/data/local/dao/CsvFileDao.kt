package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CsvFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CsvFileDao {
    @Query("SELECT * FROM csv_files ORDER BY importedAt DESC")
    fun getAllCsvFiles(): Flow<List<CsvFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(csvFile: CsvFileEntity): Long

    @Query("DELETE FROM csv_files WHERE id = :csvId")
    suspend fun deleteById(csvId: Int)
}
