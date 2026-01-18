package np.ict.mad.madassg2025

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class WeatherNarrator(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

                // Set speech rate slightly slower for better comprehension
                tts?.setSpeechRate(0.9f)

                Log.d("WeatherNarrator", "TTS initialized: $isTtsReady")
            } else {
                Log.e("WeatherNarrator", "TTS initialization failed")
            }
        }
    }

    /**
     * Generate and speak weather narrative using Claude AI
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
        onStart()

        try {
            val narrative = generateNarrative(
                place = place,
                tempC = tempC,
                condition = condition,
                weatherId = weatherId,
                sunriseUtc = sunriseUtc,
                sunsetUtc = sunsetUtc,
                tzOffsetSec = tzOffsetSec
            )

            if (narrative != null) {
                speak(narrative)
                onComplete()
            } else {
                onError("Failed to generate weather narrative")
            }
        } catch (e: Exception) {
            Log.e("WeatherNarrator", "Narration error: ${e.message}", e)
            onError(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Generate weather narrative using Claude API
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
            val prompt = buildPrompt(
                place = place,
                tempC = tempC,
                condition = condition,
                weatherId = weatherId,
                sunriseUtc = sunriseUtc,
                sunsetUtc = sunsetUtc,
                tzOffsetSec = tzOffsetSec
            )

            val requestBody = JSONObject().apply {
                put("model", "claude-sonnet-4-20250514")
                put("max_tokens", 1000)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .post(RequestBody.create(
                    MediaType.parse("application/json"),
                    requestBody.toString()
                ))
                .header("Content-Type", "application/json")
                .header("anthropic-version", "2023-06-01")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("WeatherNarrator", "API call failed: ${response.code()}")  // ✅ FIXED: Added ()
                return@withContext null
            }

            val responseBody = response.body()?.string() ?: return@withContext null
            val json = JSONObject(responseBody)

            val contentArray = json.getJSONArray("content")
            if (contentArray.length() > 0) {
                val firstContent = contentArray.getJSONObject(0)
                return@withContext firstContent.getString("text")
            }

            null
        } catch (e: Exception) {
            Log.e("WeatherNarrator", "Generate narrative error: ${e.message}", e)
            null
        }
    }

    /**
     * Build the prompt for Claude AI
     */
    private fun buildPrompt(
        place: String,
        tempC: Double,
        condition: String,
        weatherId: Int?,
        sunriseUtc: Long?,
        sunsetUtc: Long?,
        tzOffsetSec: Int?
    ): String {
        val tempF = (tempC * 9.0 / 5.0 + 32.0).toInt()

        val sunInfo = if (sunriseUtc != null && sunsetUtc != null && tzOffsetSec != null) {
            val sunrise = formatLocalTime(sunriseUtc, tzOffsetSec)
            val sunset = formatLocalTime(sunsetUtc, tzOffsetSec)
            "Sunrise is at $sunrise and sunset is at $sunset."
        } else {
            ""
        }

        val weatherEmoji = when (weatherId) {
            in 200..232 -> "stormy"
            in 300..321 -> "drizzly"
            in 500..531 -> "rainy"
            in 600..622 -> "snowy"
            in 701..781 -> "foggy"
            800 -> "clear and sunny"
            in 801..804 -> "cloudy"
            else -> "pleasant"
        }

        return """
You are a friendly weather narrator for a mobile app. Create a brief, natural-sounding weather report (2-3 sentences max) for text-to-speech.

Location: $place
Temperature: ${tempC.toInt()}°C (${tempF}°F)
Conditions: $condition ($weatherEmoji)
$sunInfo

Guidelines:
- Be conversational and warm, like a radio weather presenter
- Keep it concise (under 50 words)
- Include the temperature in both Celsius and Fahrenheit naturally
- Mention any notable weather features
- Add a helpful tip if relevant (e.g., "bring an umbrella" for rain)
- Don't use markdown, bullet points, or special formatting
- Write in complete sentences that flow naturally when spoken

Generate the weather report now:
        """.trimIndent()
    }

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
        if (!isTtsReady) {
            Log.e("WeatherNarrator", "TTS not ready")
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "weather_narration")
    }

    /**
     * Stop current narration
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}