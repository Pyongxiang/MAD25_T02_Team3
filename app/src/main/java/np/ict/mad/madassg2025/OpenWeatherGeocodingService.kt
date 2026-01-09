package np.ict.mad.madassg2025

import retrofit2.http.GET
import retrofit2.http.Query

data class ReverseGeoResult(
    val name: String?,
    val local_names: Map<String, String>?,
    val lat: Double?,
    val lon: Double?,
    val country: String?,
    val state: String?
)

interface OpenWeatherGeocodingService {

    // Reverse geocoding: lat/lon -> place name
    // Docs: OpenWeather Geocoding API supports reverse geocoding. :contentReference[oaicite:3]{index=3}
    @GET("geo/1.0/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<ReverseGeoResult>
}
