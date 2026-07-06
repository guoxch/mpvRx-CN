package app.gyrolet.mpvrx.repository.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class SiliconFlowModel(
  val id: String,
  val pricing: JsonObject? = null,
)

@Serializable
private data class SiliconFlowModelListResponse(
  val data: List<SiliconFlowModel> = emptyList(),
)

@Serializable
private data class SiliconFlowMessage(
  val role: String,
  val content: String,
)

@Serializable
private data class SiliconFlowChoice(
  val message: SiliconFlowMessage? = null,
)

@Serializable
private data class SiliconFlowResponse(
  val choices: List<SiliconFlowChoice>? = null,
)

@Serializable
private data class SiliconFlowErrorBody(val error: SiliconFlowErrorDetail? = null)

@Serializable
private data class SiliconFlowErrorDetail(val message: String? = null)

@Serializable
private data class SiliconFlowChatRequest(
  val model: String,
  val messages: List<SiliconFlowMessage>,
  val temperature: Double = 0.3,
  @SerialName("max_tokens") val maxTokens: Int = 200,
)

class SiliconFlowClient(
  private val client: OkHttpClient,
  private val json: Json,
) : AiClient {
  companion object {
    private const val TAG = "SiliconFlowClient"
    private const val BASE_URL = "https://api.siliconflow.cn/v1"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
  }

  private val apiClient: OkHttpClient =
    client.newBuilder()
      .connectTimeout(60, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$BASE_URL/models")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) throw Exception("SiliconFlow API error ${response.code}: ${parseError(body)}")

      val parsed = json.decodeFromString<SiliconFlowModelListResponse>(body)
      parsed.data.map { model ->
        AiModelInfo(
          id = model.id,
          displayName = model.id,
          isFree = AiModelPricing.isZeroCost(model.pricing),
        )
      }
    }
  }

  override suspend fun verifyKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val request = Request.Builder()
        .url("$BASE_URL/models")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val response = apiClient.newCall(request).execute()
      if (!response.isSuccessful) throw Exception("Invalid API key: ${response.code}")
      "API key verified successfully"
    }
  }

  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val chatRequest = SiliconFlowChatRequest(
        model = model,
        messages = listOf(
          SiliconFlowMessage(role = "system", content = instruction),
          SiliconFlowMessage(role = "user", content = userInput),
        ),
        temperature = options.temperature,
        maxTokens = options.maxTokens,
      )

      val requestBody = json.encodeToString(SiliconFlowChatRequest.serializer(), chatRequest)
        .toRequestBody(JSON_MEDIA_TYPE)

      val request = Request.Builder()
        .url("$BASE_URL/chat/completions")
        .header("Authorization", "Bearer $apiKey")
        .post(requestBody)
        .build()

      val response = apiClient.newCall(request).execute()
      val body = response.body.string()

      if (!response.isSuccessful) {
        throw Exception("SiliconFlow API error ${response.code}: ${parseError(body)}")
      }

      val parsed = json.decodeFromString<SiliconFlowResponse>(body)
      parsed.choices?.firstOrNull()?.message?.content
        ?: throw Exception("SiliconFlow returned empty response")
    }
  }

  private fun parseError(body: String): String {
    return try {
      val errorBody = json.decodeFromString<SiliconFlowErrorBody>(body)
      errorBody.error?.message ?: body.take(200)
    } catch (_: Exception) {
      body.take(200)
    }
  }
}
