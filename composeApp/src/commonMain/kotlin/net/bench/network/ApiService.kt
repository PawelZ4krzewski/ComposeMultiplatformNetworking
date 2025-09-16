package net.bench.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.bench.core.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class ApiService(private val client: HttpClient, private val enableRetry: Boolean = ApiConfig.enableRetry) {
    suspend fun fetch(path: String): ApiResult<SampleDto> = withContext(Dispatchers.Default) {
        val maxAttempts = if (enableRetry) 2 else 1
        var attempt = 0
        var lastError: ApiResult.NetworkError? = null

        while (attempt < maxAttempts) {
            attempt++
            var status: Int
            var payloadText: String
            var successDto: SampleDto? = null
            val mark = TimeSource.Monotonic.markNow()
            try {
                val response: HttpResponse = client.get(path)
                status = response.status.value
                payloadText = response.bodyAsText()
                if (response.status.isSuccess()) {
                    val text = payloadText
                    successDto = decodeSample(text)
                }
            } catch (t: Throwable) {
                val ms = mark.elapsedNow().inWholeMilliseconds
                logDebug("NET_GET_MS: $ms")
                val err = mapError(t).copy(durationMs = ms)
                lastError = err
                if (attempt < maxAttempts) {
                    delay(500)
                    continue
                } else break
            }

            val ms = mark.elapsedNow().inWholeMilliseconds
            logDebug("NET_GET_MS: $ms")

            if (status in 200..299 && successDto != null) {
                return@withContext ApiResult.Success(requireNotNull(successDto), status, ms)
            } else {
                val kind = when {
                    status in 400..499 -> ApiResult.NetworkError.Kind.Http4xx
                    status in 500..599 -> ApiResult.NetworkError.Kind.Http5xx
                    else -> ApiResult.NetworkError.Kind.Unknown
                }
        val err = ApiResult.NetworkError(kind, status, payloadText, ms)
                lastError = err
                if (attempt < maxAttempts) delay(500) else break
            }
        }
    return@withContext lastError ?: ApiResult.NetworkError(ApiResult.NetworkError.Kind.Unknown, null, "No result", 0)
    }

    suspend fun fetchList(path: String): ApiResult<List<SampleDto>> = withContext(Dispatchers.Default) {
        val maxAttempts = if (enableRetry) 2 else 1
        var attempt = 0
        var lastError: ApiResult.NetworkError? = null

        while (attempt < maxAttempts) {
            attempt++
            var status: Int
            var payloadText: String
            var successList: List<SampleDto>? = null
            val mark = TimeSource.Monotonic.markNow()
            try {
                val response: HttpResponse = client.get(path)
                status = response.status.value
                payloadText = response.bodyAsText()
                if (response.status.isSuccess()) {
                    successList = decodeSampleList(payloadText)
                }
            } catch (t: Throwable) {
                val ms = mark.elapsedNow().inWholeMilliseconds
                logDebug("NET_GET_MS: $ms")
                val err = mapError(t).copy(durationMs = ms)
                lastError = err
                if (attempt < maxAttempts) {
                    delay(500)
                    continue
                } else break
            }

            val ms = mark.elapsedNow().inWholeMilliseconds
            logDebug("NET_GET_MS: $ms")

            if (status in 200..299 && successList != null) {
                return@withContext ApiResult.Success(successList, status, ms)
            } else {
                val kind = when {
                    status in 400..499 -> ApiResult.NetworkError.Kind.Http4xx
                    status in 500..599 -> ApiResult.NetworkError.Kind.Http5xx
                    else -> ApiResult.NetworkError.Kind.Unknown
                }
        val err = ApiResult.NetworkError(kind, status, payloadText, ms)
                lastError = err
                if (attempt < maxAttempts) delay(500) else break
            }
        }
    return@withContext lastError ?: ApiResult.NetworkError(ApiResult.NetworkError.Kind.Unknown, null, "No result", 0)
    }

    private fun decodeSample(text: String): SampleDto? {
        val el = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
        val obj: JsonObject? = when (el) {
            is JsonObject -> el
            is JsonArray -> el.firstOrNull() as? JsonObject
            else -> null
        }
        obj ?: return null
        val id = (obj["id"] as? JsonPrimitive)?.content?.toIntOrNull()
        val title = (obj["title"] as? JsonPrimitive)?.content
        val body = (obj["body"] as? JsonPrimitive)?.content
        return SampleDto(id = id, title = title, body = body)
    }

    private fun decodeSampleList(text: String): List<SampleDto> {
        val el = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val array: JsonArray = when (el) {
            is JsonArray -> el
            is JsonObject -> JsonArray(listOf(el))
            else -> return emptyList()
        }
        
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.content?.toIntOrNull()
            val title = (obj["title"] as? JsonPrimitive)?.content
            val body = (obj["body"] as? JsonPrimitive)?.content
            SampleDto(id = id, title = title, body = body)
        }
    }

    private fun mapError(t: Throwable): ApiResult.NetworkError = when (t) {
        is CancellationException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Cancel, null, t.message, 0)
        is HttpRequestTimeoutException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Timeout, null, t.message, 0)
        is IOException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.NoInternet, null, t.message, 0)
        is SerializationException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Unknown, null, t.message, 0)
        else -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Unknown, null, t.message, 0)
    }
}
