package com.momenttrack.scanner.network

import com.momenttrack.scanner.data.ScanRecord
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// API Data Models
data class ScanPayload(
    val id: String,
    val code: String,
    val format: String,
    val timestamp: String,
    val deviceId: String,
    val locationId: String?,
    val gps: GpsCoords?
)

data class GpsCoords(
    val lat: Double,
    val lng: Double
)

data class LocationConfig(
    val id: String,
    val displayName: String?,
    val debounceSeconds: Int?
)

data class SyncResponse(
    val success: Boolean,
    val message: String?
)

// Retrofit API Interface
interface MomentTrackApi {

    @POST("scans")
    suspend fun postScan(
        @Header("X-Device-ID") deviceId: String,
        @Body scan: ScanPayload
    ): Response<SyncResponse>

    @POST("scans/batch")
    suspend fun postScanBatch(
        @Header("X-Device-ID") deviceId: String,
        @Body scans: List<ScanPayload>
    ): Response<SyncResponse>

    @GET("locations/{locationId}/config")
    suspend fun getLocationConfig(
        @Header("X-Device-ID") deviceId: String,
        @Path("locationId") locationId: String
    ): Response<LocationConfig>
}

// API Client Singleton
object ApiClient {
    
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ""

    fun getApi(baseUrl: String): MomentTrackApi {
        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        
        return retrofit!!.create(MomentTrackApi::class.java)
    }
}

// Extension to convert ScanRecord to API payload
fun ScanRecord.toPayload(): ScanPayload {
    return ScanPayload(
        id = "${deviceId}-${id}",
        code = code,
        format = format,
        timestamp = java.time.Instant.ofEpochMilli(timestamp).toString(),
        deviceId = deviceId,
        locationId = locationId,
        gps = if (latitude != null && longitude != null) {
            GpsCoords(latitude, longitude)
        } else null
    )
}
