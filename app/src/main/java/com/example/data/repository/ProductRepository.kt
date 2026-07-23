package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.CsvFileDao
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.CsvFileEntity
import com.example.data.remote.ApiProduct
import com.example.data.remote.BulkUploadRequest
import com.example.data.remote.RetrofitClient
import com.example.data.remote.UpdateBarcodeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class DuplicateMatch(
    val name: String,
    val brand: String,
    val oldPrice: String,
    val newPrice: String,
    val oldPriceNumeric: Long,
    val newPriceNumeric: Long
)

data class CsvPreview(
    val newProducts: List<ProductEntity>,
    val duplicateMatches: List<DuplicateMatch>,
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
        val CSV_VERSION = 4
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedVersion = prefs.getInt("csv_version", 0)
        if (savedVersion >= CSV_VERSION) return@withContext
        val existingProducts = productDao.getAllProducts().first()
        val existingById = existingProducts.associateBy { it.id }
        val products = loadProductsFromCsv().map { newProduct ->
            val old = existingById[newProduct.id]
            if (old != null && newProduct.barcode.isNullOrEmpty() && !old.barcode.isNullOrEmpty()) {
                newProduct.copy(barcode = old.barcode)
            } else {
                newProduct
            }
        }
        if (products.isNotEmpty()) {
            productDao.insertAll(products)
            prefs.edit().putInt("csv_version", CSV_VERSION).apply()
            Log.d("ProductRepository", "Synced ${products.size} products (barcodes preserved).")
        }
    }

    // مرحله ۱: بخون و چک تکراری بر اساس نام + برند
    suspend fun previewCsv(uri: Uri, fileName: String): CsvPreview? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            reader.readLine()
            data class Parsed(val name: String, val brand: String, val price: String, val priceNumeric: Long)
            val parsed = mutableListOf<Parsed>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val row = line ?: continue
                if (row.isBlank()) continue
                val tokens = parseCsvLine(row)
                if (tokens.size >= 4) {
                    val name = normalizeArabic(tokens[1].trim())
                    val brand = normalizeArabic(tokens[2].trim().replace("\u200c", "").replace("\u00a0", " "))
                    val priceRaw = tokens[3]
                    val priceClean = priceRaw.replace("\"", "").trim()
                    val priceNumeric = priceClean.replace(",", "").replace(".", "").replace(" ", "").replace("تومان", "").toLongOrNull() ?: 0L
                    if (name.isNotBlank()) {
                        parsed.add(Parsed(name, brand, priceClean, priceNumeric))
                    }
                }
            }
            reader.close()

            // ثبت CSV
            val csvRecord = CsvFileEntity(fileName = fileName, importedAt = System.currentTimeMillis(), productCount = parsed.size)
            val csvId = csvFileDao.insert(csvRecord).toInt()

            // چک تکراری بر اساس نام + برند
            val names = parsed.map { it.name }
            val brands = parsed.map { it.brand }
            Log.d("CSV_CHECK", "IMPORT FILE=$fileName rows=${parsed.size}")
            Log.d("CSV_CHECK", "brands=${parsed.map { it.brand }.distinct()}")
            val existing = productDao.getExistingByNameAndBrand(names, brands)
            val existingMap = existing.associateBy { it.name to it.brand }

            val newProducts = mutableListOf<ProductEntity>()
            val duplicateMatches = mutableListOf<DuplicateMatch>()

            parsed.forEach { p ->
                val key = p.name to p.brand
                val existingProduct = existingMap[key]
                if (existingProduct != null) {
                    duplicateMatches.add(
                        DuplicateMatch(
                            name = p.name,
                            brand = p.brand,
                            oldPrice = existingProduct.price,
                            newPrice = p.price,
                            oldPriceNumeric = existingProduct.priceNumeric,
                            newPriceNumeric = p.priceNumeric
                        )
                    )
                } else {
                    newProducts.add(ProductEntity(name = p.name, brand = p.brand, price = p.price, priceNumeric = p.priceNumeric, csvId = csvId))
                }
            }

            CsvPreview(newProducts, duplicateMatches, fileName, csvId)
        } catch (e: Exception) {
            Log.e("ProductRepository", "Error previewing CSV", e)
            null
        }
    }

    // مرحله ۲: import نهایی
    suspend fun confirmImport(preview: CsvPreview, updateDuplicates: Boolean) = withContext(Dispatchers.IO) {
        if (preview.newProducts.isNotEmpty()) {
            productDao.insertAllIgnore(preview.newProducts)
        }
        if (updateDuplicates) {
            preview.duplicateMatches.forEach { match ->
                productDao.updatePriceByNameAndBrand(match.name, match.brand, match.newPrice, match.newPriceNumeric)
            }
        }
        val totalImported = preview.newProducts.size + if (updateDuplicates) preview.duplicateMatches.size else 0
        csvFileDao.insert(CsvFileEntity(
            id = preview.csvId,
            fileName = preview.fileName,
            importedAt = System.currentTimeMillis(),
            productCount = totalImported
        ))
        Log.d("ProductRepository", "Imported ${preview.newProducts.size} new, updated ${if (updateDuplicates) preview.duplicateMatches.size else 0} duplicates")
    }

    suspend fun deleteCsvAndProducts(csvId: Int) = withContext(Dispatchers.IO) {
        productDao.deleteByCsvId(csvId)
        csvFileDao.deleteById(csvId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        productDao.deleteAll()
        csvFileDao.deleteAll()
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("csv_version").apply()
        Log.d("ProductRepository", "All data cleared.")
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
                    val name = normalizeArabic(tokens[1].trim())
                    val brand = normalizeArabic(tokens[2].trim())
                    val priceRaw = tokens[3]
                    val priceClean = priceRaw.replace("\"", "").trim()
                    val priceNumeric = priceClean.replace(",", "").toLongOrNull() ?: 0L
                    if (name.isNotBlank()) {
                        products.add(ProductEntity(name = name, brand = brand, price = priceClean, priceNumeric = priceNumeric, csvId = 0))
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("ProductRepository", "Error reading CSV file", e)
        }
        return products
    }

    suspend fun updateBarcode(productId: Int, barcode: String): Result<Unit> {
        productDao.updateBarcode(productId, barcode)
        // ارسال به سرور محلی؛ اگه سرور در دسترس نبود، دیتای محلی همچنان درست ثبت شده
        return try {
            RetrofitClient.apiService.updateBarcode(productId, UpdateBarcodeRequest(barcode))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProductRepository", "Failed to push barcode to server", e)
            Result.failure(e)
        }
    }

    // گرفتن کل کاتالوگ از سرور و آپدیت/اضافه کردن بدون پاک کردن دیتای محلی
    suspend fun syncFromServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val serverProducts = RetrofitClient.apiService.getProducts()
            val entities = serverProducts.map {
                ProductEntity(
                    id = it.id,
                    name = it.name,
                    brand = it.brand,
                    price = it.price,
                    priceNumeric = it.priceNumeric,
                    csvId = it.csvId,
                    barcode = it.barcode
                )
            }
            // به جای پاک کردن همه، فقط آپدیت/اضافه می‌کنیم
            // REPLACE strategy: اگه id تکراری بود آپدیت، اگه نبود اضافه
            if (entities.isNotEmpty()) {
                productDao.insertAll(entities)
            }
            Result.success(entities.size)
        } catch (e: Exception) {
            Log.e("ProductRepository", "Error syncing from server", e)
            Result.failure(e)
        }
    }

    // ارسال کل کاتالوگ محلی به سرور (جایگزین کامل روی سرور)
    suspend fun pushCatalogToServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val localProducts = productDao.getAllProducts().first()
            val items = localProducts.map {
                ApiProduct(
                    id = it.id,
                    name = it.name,
                    brand = it.brand,
                    price = it.price,
                    priceNumeric = it.priceNumeric,
                    csvId = it.csvId,
                    barcode = it.barcode
                )
            }
            val response = RetrofitClient.apiService.bulkUpload(BulkUploadRequest(items = items, mode = "replace"))
            Result.success(response.count)
        } catch (e: Exception) {
            Log.e("ProductRepository", "Error pushing catalog to server", e)
            Result.failure(e)
        }
    }

    suspend fun getProductByBarcode(barcode: String) = productDao.getProductByBarcode(barcode)

    private fun normalizeArabic(s: String): String = s.replace('ك', 'ک').replace('ي', 'ی')

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
