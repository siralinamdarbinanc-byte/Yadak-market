package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.dao.ProductDao
import com.example.data.local.entity.ProductEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ProductRepository(
    private val context: Context,
    private val productDao: ProductDao
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()

    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.searchProducts(query)

    suspend fun checkAndSeedDatabase() = withContext(Dispatchers.IO) {
        val count = productDao.getCount()
        if (count == 0) {
            Log.d("ProductRepository", "Database is empty. Seeding from assets...")
            val products = loadProductsFromCsv()
            if (products.isNotEmpty()) {
                productDao.insertAll(products)
                Log.d("ProductRepository", "Successfully seeded ${products.size} products.")
            }
        } else {
            Log.d("ProductRepository", "Database already contains $count products. Skipping seeding.")
        }
    }

    private fun loadProductsFromCsv(): List<ProductEntity> {
        val products = mutableListOf<ProductEntity>()
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("products.csv")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            var line = reader.readLine() // Read header row (ردیف,نام کالا,برند,قیمت فروش به ریال)
            
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                
                val tokens = parseCsvLine(row)
                if (tokens.size >= 4) {
                    val idStr = tokens[0]
                    val name = tokens[1]
                    val brand = tokens[2]
                    val price = tokens[3]
                    
                    val id = idStr.toIntOrNull() ?: continue
                    val priceCleaned = price.replace("\"", "").replace(",", "").trim()
                    val priceNumeric = priceCleaned.toLongOrNull() ?: 0L
                    
                    products.add(
                        ProductEntity(
                            id = id,
                            name = name,
                            brand = brand,
                            price = price.replace("\"", ""),
                            priceNumeric = priceNumeric
                        )
                    )
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("ProductRepository", "Error reading CSV file", e)
        }
        return products
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val currentToken = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> {
                    inQuotes = !inQuotes
                }
                char == ',' && !inQuotes -> {
                    tokens.add(currentToken.toString().trim())
                    currentToken.setLength(0)
                }
                else -> {
                    currentToken.append(char)
                }
            }
        }
        tokens.add(currentToken.toString().trim())
        return tokens
    }
}
