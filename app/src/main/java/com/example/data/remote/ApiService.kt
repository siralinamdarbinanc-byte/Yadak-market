package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @GET("api/products")
    suspend fun getProducts(): List<ApiProduct>

    @PUT("api/products/{id}/barcode")
    suspend fun updateBarcode(
        @Path("id") id: Int,
        @Body body: UpdateBarcodeRequest
    )

    @POST("api/products/bulk")
    suspend fun bulkUpload(
        @Body body: BulkUploadRequest
    ): BulkUploadResponse

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(
        @Path("id") id: Int
    )
}
