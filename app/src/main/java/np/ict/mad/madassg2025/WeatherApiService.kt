package np.ict.mad.madassg2025

import retrofit2.http.GET
import retrofit2.http.Query

data class MainInfo(val temp: Double)

// ✅ UPDATED: includes "id" for reliable icon mapping
data class WeatherInfo(
    val id: Int,
    val description: String
)

data class WeatherResponse(
    val name: String,
    val main: MainInfo,
    val weather: List<WeatherInfo>
)

interface WeatherApiService {

    // City-based (optional)
    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCity(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    // Coords-based (recommended)
    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCoords(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}
