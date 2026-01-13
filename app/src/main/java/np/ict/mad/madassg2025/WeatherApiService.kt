package np.ict.mad.madassg2025

import retrofit2.http.GET
import retrofit2.http.Query

data class MainInfo(val temp: Double)

// ✅ includes "id" for reliable emoji mapping
data class WeatherInfo(
    val id: Int,
    val description: String
)

// ✅ NEW: sunrise/sunset comes from sys
data class SysInfo(
    val sunrise: Long, // unix UTC seconds
    val sunset: Long   // unix UTC seconds
)

// ✅ UPDATED: add timezone + sys (OpenWeather current weather endpoint provides these)
data class WeatherResponse(
    val name: String,
    val main: MainInfo,
    val weather: List<WeatherInfo>,
    val timezone: Int,  // seconds offset from UTC for the location
    val sys: SysInfo
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
