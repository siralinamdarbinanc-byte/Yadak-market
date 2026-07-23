package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiProduct(
    val id: Int,
    val name: String,
    val brand: String,
    val price: String,
    val priceNumeric: Long,
    val csvId: Int = 0,
    val barcode: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateBarcodeRequest(
    val barcode: String
)

@JsonClass(generateAdapter = true)
data class BulkUploadRequest(
    val items: List<ApiProduct>,
    val mode: String = "replace"
)

@JsonClass(generateAdapter = true)
data class BulkUploadResponse(
    val count: Int
)
