package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("DELETE FROM products WHERE csvId = :csvId")
    suspend fun deleteByCsvId(csvId: Int)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getCount(): Int

    @Query("SELECT * FROM products WHERE name IN (:names) AND brand IN (:brands)")
    suspend fun getExistingByNameAndBrand(names: List<String>, brands: List<String>): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(products: List<ProductEntity>)

    @Query("UPDATE products SET price = :price, priceNumeric = :priceNumeric WHERE name = :name AND brand = :brand")
    suspend fun updatePriceByNameAndBrand(name: String, brand: String, price: String, priceNumeric: Long)
}
