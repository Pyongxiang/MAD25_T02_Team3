package np.ict.mad.madassg2025

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WeatherRepository {

    private const val BASE_URL = "https://api.openweathermap.org/"
    private const val API_KEY = "e25a0c31ecc92cc51c1c7548568af374" // keep your friend's key

    private val api: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    // ✅ Old call style: WeatherRepository.getCurrentWeather("Singapore")
    suspend fun getCurrentWeather(city: String): WeatherResponse {
        return api.getCurrentWeatherByCity(city = city, apiKey = API_KEY)
    }

    // ✅ New call style: WeatherRepository.getCurrentWeather(lat, lon)
    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        return api.getCurrentWeatherByCoords(lat = lat, lon = lon, apiKey = API_KEY)
    }
}
