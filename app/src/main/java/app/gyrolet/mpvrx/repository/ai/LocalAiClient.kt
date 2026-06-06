package app.gyrolet.mpvrx.repository.ai

import android.content.Context
import android.util.Log
import app.gyrolet.mpvrx.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

class LocalAiClient(
  private val context: Context,
  private val inference: LlmInference,
) : AiClient {

  companion object {
    private const val TAG = "LocalAiClient"
  }

  private val generationMutex = Mutex()
  private var loadedModelPath: String? = null
  private var loadedModelId: String? = null

  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>> {
    return Result.success(
      LocalModelCatalog.models.map { model ->
        AiModelInfo(
          id = model.id,
          displayName = "${model.displayName} (${model.tier.label}, ${model.sizeLabel})",
        )
      },
    )
  }

  override suspend fun verifyKey(apiKey: String): Result<String> {
    if (!inference.isAvailable()) {
      return Result.failure(
        IllegalStateException(
          context.getString(R.string.local_ai_library_not_available),
        ),
      )
    }
    return Result.success(context.getString(R.string.local_ai_no_api_key_needed))
  }

  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions,
  ): Result<String> = withContext(Dispatchers.IO) {
    generationMutex.withLock {
      runCatching {
        if (!inference.isAvailable()) {
          throw IllegalStateException(
            context.getString(R.string.local_ai_not_supported),
          )
        }

        val modelPath = apiKey
        if (modelPath.isBlank()) {
          throw IllegalStateException(
            context.getString(R.string.local_ai_no_model_downloaded),
          )
        }
        if (!inference.isLoaded() || loadedModelPath != modelPath || loadedModelId != model) {
          val info = LocalModelCatalog.getById(model)
          val nCtx = info?.contextLength ?: 2048
          inference.loadModel(modelPath, nCtx).getOrThrow()
          loadedModelPath = modelPath
          loadedModelId = model
        }

        val prompt = inference.applyChatTemplate(instruction, userInput)
          .getOrElse { LocalModelCatalog.formatPrompt(model, instruction, userInput) }

        inference.generate(
          prompt = prompt,
          maxTokens = options.maxTokens,
          temperature = options.temperature.toFloat(),
        ).getOrThrow()
      }
    }
  }

  suspend fun benchmark(
    modelPath: String,
    modelId: String,
  ): Result<LocalModelBenchmark> = withContext(Dispatchers.IO) {
    generationMutex.withLock {
      runCatching {
        val info = LocalModelCatalog.getById(modelId)
          ?: throw IllegalArgumentException("Unknown model: $modelId")
        val loadStarted = System.currentTimeMillis()
        if (!inference.isLoaded() || loadedModelPath != modelPath || loadedModelId != modelId) {
          inference.loadModel(modelPath, info.contextLength).getOrThrow()
          loadedModelPath = modelPath
          loadedModelId = modelId
        }
        val loadMs = System.currentTimeMillis() - loadStarted
        val prompt = LocalModelCatalog.formatPrompt(
          modelId,
          "Translate the sentence naturally. Return only the translation.",
          "Translate to Hindi: This movie is getting interesting now.",
        )
        val generationStarted = System.currentTimeMillis()
        val output = inference.generate(
          prompt = prompt,
          maxTokens = 48,
          temperature = 0.2f,
        ).getOrThrow()
        val generationMs = max(1L, System.currentTimeMillis() - generationStarted)
        val tokenEstimate = output.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        LocalModelBenchmark(
          modelId = modelId,
          loadMs = loadMs,
          tokensPerSecond = tokenEstimate * 1000f / generationMs,
          memoryEstimateMb = (info.quantSizeMb * 1.35f).toInt(),
          benchmarkedAtMs = System.currentTimeMillis(),
        )
      }
    }
  }

  fun isModelLoaded(): Boolean = inference.isLoaded()

  fun unloadModel() {
    if (inference.isLoaded()) {
      inference.unload()
      loadedModelPath = null
      loadedModelId = null
      Log.i(TAG, "Model unloaded")
    }
  }
}
