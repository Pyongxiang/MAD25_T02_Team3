package np.ict.mad.madassg2025

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WeatherRepository {

    private const val BASE_URL = "https://api.openweathermap.org/"

    // Simple direct API key (ok for school/private repo)
    private const val API_KEY = "enter_your_api_here"

    private val api: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    suspend fun getCurrentWeather(city: String): WeatherResponse {
        return api.getCurrentWeather(
            city = city,
            apiKey = API_KEY
        )
    }
}
