package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.CsvFileDao
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.CsvFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class CsvPreview(
    val newProducts: List<ProductEntity>,
    val duplicateProducts: List<ProductEntity>,
    val fileName: String,
    val csvId: Int
)

class ProductRepository(
    private val context: Context,
    private val productDao: ProductDao,
    private val csvFileDao: CsvFileDao
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()
    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.searchProducts(query)
    fun getAllCsvFiles() = csvFileDao.getAllCsvFiles()

    suspend fun checkAndSeedDatabase() = withContext(Dispatchers.IO) {
        val CSV_VERSION = 3
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedVersion = prefs.getInt("csv_version", 0)
        if (savedVersion >= CSV_VERSION) return@withContext
        productDao.deleteAll()
        val products = loadProductsFromCsv()
        if (products.isNotEmpty()) {
            productDao.insertAll(products)
            prefs.edit().putInt("csv_version", CSV_VERSION).apply()
            Log.d("ProductRepository", "Synced ${products.size} products.")
        }
    }

    // مرحله ۱: فقط بخون و چک کن
    suspend fun previewCsv(uri: Uri, fileName: String): CsvPreview? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            reader.readLine()
            val products = mutableListOf<ProductEntity>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                val tokens = parseCsvLine(row)
                if (tokens.size >= 4) {
                    val id = tokens[0].toIntOrNull() ?: continue
                    val name = tokens[1]
                    val brand = tokens[2]
                    val price = tokens[3]
                    val priceNumeric = price.replace("\"", "").replace(",", "").trim().toLongOrNull() ?: 0L
                    products.add(ProductEntity(id = id, name = name, brand = brand, price = price.replace("\"", ""), priceNumeric = priceNumeric, csvId = 0))
                }
            }
            reader.close()

            // ثبت CSV
            val csvRecord = CsvFileEntity(fileName = fileName, importedAt = System.currentTimeMillis(), productCount = products.size)
            val csvId = csvFileDao.insert(csvRecord).toInt()

            // چک تکراری
            val allIds = products.map { it.id }
            val existingIds = productDao.getExistingIds(allIds).toSet()
            val newProducts = products.filter { it.id !in existingIds }.map { it.copy(csvId = csvId) }
            val duplicates = products.filter { it.id in existingIds }

            CsvPreview(newProducts, duplicates, fileName, csvId)
        } catch (e: Exception) {
            Log.e("ProductRepository", "Error previewing CSV", e)
            null
        }
    }

    // مرحله ۲: import با تصمیم کاربر
    suspend fun confirmImport(preview: CsvPreview, updateDuplicates: Boolean) = withContext(Dispatchers.IO) {
        // محصولات جدید رو اضافه کن
        if (preview.newProducts.isNotEmpty()) {
            productDao.insertAllIgnore(preview.newProducts)
        }
        // اگه کاربر بله زد، قیمت تکراری‌ها رو آپدیت کن
        if (updateDuplicates) {
            preview.duplicateProducts.forEach { product ->
                productDao.updatePrice(product.id, product.price, product.priceNumeric)
            }
        }
        // آپدیت تعداد محصولات توی csv_files
        val totalImported = preview.newProducts.size + if (updateDuplicates) preview.duplicateProducts.size else 0
        csvFileDao.insert(CsvFileEntity(
            id = preview.csvId,
            fileName = preview.fileName,
            importedAt = System.currentTimeMillis(),
            productCount = totalImported
        ))
        Log.d("ProductRepository", "Imported ${preview.newProducts.size} new, updated ${if (updateDuplicates) preview.duplicateProducts.size else 0} duplicates")
    }

    suspend fun deleteCsvAndProducts(csvId: Int) = withContext(Dispatchers.IO) {
        productDao.deleteByCsvId(csvId)
        csvFileDao.deleteById(csvId)
    }

    private fun loadProductsFromCsv(): List<ProductEntity> {
        val products = mutableListOf<ProductEntity>()
        try {
            val inputStream = context.assets.open("products.csv")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            reader.readLine()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                val tokens = parseCsvLine(row)
                if (tokens.size >= 4) {
                    val id = tokens[0].toIntOrNull() ?: continue
                    val name = tokens[1]
                    val brand = tokens[2]
                    val price = tokens[3]
                    val priceNumeric = price.replace("\"", "").replace(",", "").trim().toLongOrNull() ?: 0L
                    products.add(ProductEntity(id = id, name = name, brand = brand, price = price.replace("\"", ""), priceNumeric = priceNumeric, csvId = 0))
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
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    tokens.add(currentToken.toString().trim())
                    currentToken.setLength(0)
                }
                else -> currentToken.append(char)
            }
        }
        tokens.add(currentToken.toString().trim())
        return tokens
    }
}
