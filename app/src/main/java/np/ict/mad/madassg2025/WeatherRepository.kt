package np.ict.mad.madassg2025

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WeatherRepository {

    private const val BASE_URL = "https://api.openweathermap.org/"
    private const val API_KEY = "e25a0c31ecc92cc51c1c7548568af374" // keep your friend's key

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val weatherApi: WeatherApiService by lazy {
        retrofit.create(WeatherApiService::class.java)
    }

    private val geoApi: OpenWeatherGeocodingService by lazy {
        retrofit.create(OpenWeatherGeocodingService::class.java)
    }

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        return weatherApi.getCurrentWeatherByCoords(lat = lat, lon = lon, apiKey = API_KEY)
    }

    /**
     * Returns a better place label than WeatherResponse.name.
     * Uses OpenWeather reverse geocoding API. :contentReference[oaicite:4]{index=4}
     */
    suspend fun getPlaceName(lat: Double, lon: Double): String? {
        val results = geoApi.reverseGeocode(lat = lat, lon = lon, limit = 1, apiKey = API_KEY)
        val first = results.firstOrNull() ?: return null

        // Prefer English name if available, otherwise "name"
        return first.local_names?.get("en") ?: first.name
    }
}
