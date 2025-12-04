package np.ict.mad.madassg2025

import retrofit2.http.GET
import retrofit2.http.Query

data class MainInfo(
    val temp: Double
)

data class WeatherInfo(
    val description: String
)

data class WeatherResponse(
    val name: String,
    val main: MainInfo,
    val weather: List<WeatherInfo>
)

interface WeatherApiService {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}
