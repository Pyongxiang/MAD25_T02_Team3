package np.ict.mad.madassg2025

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WeatherNarrator(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        Log.d("WeatherNarrator", "Initializing WeatherNarrator")
        initializeTTS()
    }

    private fun initializeTTS() {
        Log.d("WeatherNarrator", "Starting TTS initialization")
        tts = TextToSpeech(context) { status ->
            Log.d("WeatherNarrator", "TTS init callback received with status: $status")
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                Log.d("WeatherNarrator", "Set language result: $result")
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

                if (isTtsReady) {
                    // Set speech rate slightly slower for better comprehension
                    tts?.setSpeechRate(0.9f)
                    tts?.setPitch(1.0f)

                    // Add utterance listener for debugging
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d("WeatherNarrator", "Speech started: $utteranceId")
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.d("WeatherNarrator", "Speech completed: $utteranceId")
                        }

                        override fun onError(utteranceId: String?) {
                            Log.e("WeatherNarrator", "Speech error: $utteranceId")
                        }
                    })

                    Log.d("WeatherNarrator", "TTS initialized successfully")
                } else {
                    Log.e("WeatherNarrator", "TTS language not supported")
                }
            } else {
                Log.e("WeatherNarrator", "TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Generate and speak weather narrative locally
     */
    suspend fun narrateWeather(
        place: String,
        tempC: Double,
        condition: String,
        weatherId: Int?,
        sunriseUtc: Long?,
        sunsetUtc: Long?,
        tzOffsetSec: Int?,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        Log.d("WeatherNarrator", "narrateWeather called - place: $place, temp: $tempC, condition: $condition")

        onStart()

        try {
            // Check if TTS is ready
            if (!isTtsReady) {
                Log.e("WeatherNarrator", "TTS not ready yet, waiting...")
                // Wait a bit for TTS to initialize
                delay(1000)
                if (!isTtsReady) {
                    Log.e("WeatherNarrator", "TTS still not ready after waiting")
                    onError("Text-to-speech not ready. Please try again.")
                    return
                }
            }

            Log.d("WeatherNarrator", "Generating narrative...")
            val narrative = generateNarrative(
                place = place,
                tempC = tempC,
                condition = condition,
                weatherId = weatherId,
                sunriseUtc = sunriseUtc,
                sunsetUtc = sunsetUtc,
                tzOffsetSec = tzOffsetSec
            )

            Log.d("WeatherNarrator", "Generated narrative: $narrative")

            if (narrative != null) {
                speak(narrative)
                // Give TTS time to start speaking before calling onComplete
                delay(500)
                onComplete()
            } else {
                Log.e("WeatherNarrator", "Narrative generation returned null")
                onError("Failed to generate weather narrative")
            }
        } catch (e: Exception) {
            Log.e("WeatherNarrator", "Narration error: ${e.message}", e)
            onError(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Generate weather narrative locally without AI API
     */
    private suspend fun generateNarrative(
        place: String,
        tempC: Double,
        condition: String,
        weatherId: Int?,
        sunriseUtc: Long?,
        sunsetUtc: Long?,
        tzOffsetSec: Int?
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("WeatherNarrator", "Building narrative parts...")
            val tempF = (tempC * 9.0 / 5.0 + 32.0).toInt()
            val greeting = getTimeBasedGreeting()

            // Get weather context and suggestions
            val weatherContext = getWeatherContext(weatherId, tempC, condition)

            // Build the narrative
            val narrativeParts = mutableListOf<String>()

            // Part 1: Greeting and temperature
            narrativeParts.add("$greeting It's currently ${tempC.toInt()} degrees Celsius, that's $tempF Fahrenheit in $place with ${condition.lowercase()}.")

            // Part 2: Weather-specific suggestion
            narrativeParts.add(weatherContext.suggestion)

            // Part 3: Optional sunrise/sunset info (only if available and relevant)
            if (sunriseUtc != null && sunsetUtc != null && tzOffsetSec != null && weatherContext.includeSunInfo) {
                val sunrise = formatLocalTime(sunriseUtc, tzOffsetSec)
                val sunset = formatLocalTime(sunsetUtc, tzOffsetSec)
                narrativeParts.add("Sunrise was at $sunrise and sunset will be at $sunset.")
            }

            val result = narrativeParts.joinToString(" ")
            Log.d("WeatherNarrator", "Narrative built successfully, length: ${result.length}")
            result
        } catch (e: Exception) {
            Log.e("WeatherNarrator", "Generate narrative error: ${e.message}", e)
            null
        }
    }

    /**
     * Get time-based greeting
     */
    private fun getTimeBasedGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Good morning!"
            in 12..17 -> "Good afternoon!"
            in 18..21 -> "Good evening!"
            else -> "Hello!"
        }
    }

    /**
     * Get weather context and suggestions based on conditions
     */
    private fun getWeatherContext(weatherId: Int?, tempC: Double, condition: String): WeatherContext {
        Log.d("WeatherNarrator", "Getting weather context for weatherId: $weatherId, temp: $tempC")
        return when (weatherId) {
            // Thunderstorm (200-232)
            in 200..232 -> WeatherContext(
                suggestion = "It's best to stay indoors today. Perfect weather for catching up on your favorite shows, reading a book, or trying out that new recipe.",
                includeSunInfo = false
            )

            // Drizzle (300-321)
            in 300..321 -> WeatherContext(
                suggestion = "Don't forget to bring an umbrella and a light jacket. It's still a good day for a cozy café visit or some indoor shopping.",
                includeSunInfo = false
            )

            // Rain (500-531)
            in 500..531 -> {
                val isHeavyRain = weatherId in 520..531
                if (isHeavyRain) {
                    WeatherContext(
                        suggestion = "Heavy rain is expected, so it's wise to stay indoors if possible. Great weather for indoor activities like gaming, cooking, or watching movies.",
                        includeSunInfo = false
                    )
                } else {
                    WeatherContext(
                        suggestion = "Make sure to bring your umbrella and a waterproof jacket. If you're driving, allow extra time for your journey.",
                        includeSunInfo = false
                    )
                }
            }

            // Snow (600-622)
            in 600..622 -> WeatherContext(
                suggestion = "Bundle up in warm layers and wear waterproof boots. Roads may be slippery, so drive carefully. Perfect day for hot chocolate!",
                includeSunInfo = false
            )

            // Fog/Mist (701-781)
            in 701..781 -> WeatherContext(
                suggestion = "Visibility is reduced, so drive carefully with your headlights on. Allow extra time for travel and consider indoor activities if the fog is thick.",
                includeSunInfo = false
            )

            // Clear (800)
            800 -> when {
                tempC > 30 -> WeatherContext(
                    suggestion = "It's quite hot out there! Stay hydrated, wear sunscreen, and consider indoor activities during peak hours. Perfect weather for swimming or visiting an air-conditioned mall.",
                    includeSunInfo = true
                )
                tempC < 10 -> WeatherContext(
                    suggestion = "It's cold but beautiful! Dress in warm layers and enjoy the clear skies. Great day for a brisk walk or outdoor photography.",
                    includeSunInfo = true
                )
                else -> WeatherContext(
                    suggestion = "Perfect weather for outdoor activities! Consider going for a walk in the park, having a picnic, or doing some outdoor exercise. Make the most of this beautiful day!",
                    includeSunInfo = true
                )
            }

            // Cloudy (801-804)
            in 801..804 -> when {
                tempC > 28 -> WeatherContext(
                    suggestion = "The clouds are providing some relief from the direct sun. Still warm, so stay hydrated. Good weather for outdoor activities without harsh sunlight.",
                    includeSunInfo = false
                )
                tempC < 12 -> WeatherContext(
                    suggestion = "It might feel cooler than it looks. Bring a light jacket or sweater. Perfect weather for a comfortable outdoor walk or some window shopping.",
                    includeSunInfo = false
                )
                else -> WeatherContext(
                    suggestion = "Comfortable weather for outdoor activities without harsh sun. Great for photography, walking, or visiting outdoor markets.",
                    includeSunInfo = false
                )
            }

            // Default/Unknown
            else -> WeatherContext(
                suggestion = "Check the weather conditions before heading out and dress appropriately. Have a great day!",
                includeSunInfo = false
            )
        }
    }

    /**
     * Data class to hold weather context information
     */
    private data class WeatherContext(
        val suggestion: String,
        val includeSunInfo: Boolean
    )

    private fun formatLocalTime(utcEpochSec: Long, tzOffsetSec: Int): String {
        val localEpochSec = utcEpochSec + tzOffsetSec.toLong()
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(localEpochSec * 1000L))
    }

    /**
     * Speak the given text using TTS
     */
    private fun speak(text: String) {
        Log.d("WeatherNarrator", "speak() called with text length: ${text.length}")
        Log.d("WeatherNarrator", "isTtsReady: $isTtsReady, tts: ${tts != null}")

        if (!isTtsReady) {
            Log.e("WeatherNarrator", "TTS not ready, cannot speak")
            return
        }

        if (tts == null) {
            Log.e("WeatherNarrator", "TTS object is null")
            return
        }

        Log.d("WeatherNarrator", "Speaking: $text")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "weather_narration_${System.currentTimeMillis()}")
        Log.d("WeatherNarrator", "TTS speak() returned: $result")

        when (result) {
            TextToSpeech.SUCCESS -> Log.d("WeatherNarrator", "TTS speak SUCCESS")
            TextToSpeech.ERROR -> Log.e("WeatherNarrator", "TTS speak ERROR")
            else -> Log.e("WeatherNarrator", "TTS speak returned: $result")
        }
    }

    /**
     * Stop current narration
     */
    fun stop() {
        Log.d("WeatherNarrator", "stop() called")
        tts?.stop()
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean {
        val speaking = tts?.isSpeaking ?: false
        Log.d("WeatherNarrator", "isSpeaking(): $speaking")
        return speaking
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        Log.d("WeatherNarrator", "shutdown() called")
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
