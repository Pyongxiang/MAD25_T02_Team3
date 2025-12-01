package np.ict.mad.madassg2025

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*



data class HourlyForecast(
    val dt: Long,
    val temp: Double,
    val weather: List<WeatherInfo>
)

data class WeatherInfo(
    val description: String,
    val icon: String
)

data class ForecastResponse(
    val hourly: List<HourlyForecast>
)


interface WeatherApiService {

    // OpenWeather OneCall API (48-hour forecast)
    @GET("data/2.5/onecall")
    suspend fun getHourlyForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("exclude") exclude: String = "current,minutely,daily,alerts",
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): ForecastResponse
}

private fun createWeatherApi(): WeatherApiService {
    return Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WeatherApiService::class.java)
}

private val weatherApi = createWeatherApi()



class WeatherRepository {
    suspend fun fetchForecast(lat: Double, lon: Double, apiKey: String): ForecastResponse {
        return weatherApi.getHourlyForecast(lat, lon, apiKey = apiKey)
    }
}



class ForecastViewModel : ViewModel() {

    private val repo = WeatherRepository()

    var forecastList by mutableStateOf<List<HourlyForecast>>(emptyList())
        private set

    var loading by mutableStateOf(false)
    var errorMsg by mutableStateOf("")

    fun loadForecast(latitude: Double, longitude: Double, apiKey: String) {
        loading = true
        errorMsg = ""

        viewModelScope.launch {
            try {
                val result = repo.fetchForecast(latitude, longitude, apiKey)
                // Limit to 48 hours
                forecastList = result.hourly.take(48)
            } catch (e: Exception) {
                errorMsg = "Failed to load forecast: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
}



@SuppressLint("SimpleDateFormat")
@Composable
fun ForecastScreen(
    viewModel: ForecastViewModel,
    lat: Double,
    lon: Double,
    apiKey: String
) {
    LaunchedEffect(true) {
        viewModel.loadForecast(lat, lon, apiKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("48 Hour Weather Forecast") })
        }
    ) { padding ->

        Box(modifier = Modifier.padding(padding)) {

            when {
                viewModel.loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                viewModel.errorMsg.isNotEmpty() -> {
                    Text(
                        text = viewModel.errorMsg,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    ForecastList(forecasts = viewModel.forecastList)
                }
            }
        }
    }
}



@Composable
fun ForecastList(forecasts: List<HourlyForecast>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(forecasts) { hour ->
            ForecastItem(hour)
        }
    }
}



@SuppressLint("SimpleDateFormat")
@Composable
fun ForecastItem(f: HourlyForecast) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a")
    val time = sdf.format(Date(f.dt * 1000))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Weather icon
            Image(
                painter = rememberAsyncImagePainter("https://openweathermap.org/img/wn/${f.weather[0].icon}@2x.png"),
                contentDescription = null,
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(time, fontWeight = FontWeight.Bold)
                Text("Temperature: ${f.temp}°C")
                Text("Condition: ${f.weather[0].description}")
            }
        }
    }
}
