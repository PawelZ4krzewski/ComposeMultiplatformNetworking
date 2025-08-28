package net.bench.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect object EngineProvider { val engine: HttpClientEngine }

fun createHttpClient(
    baseUrl: String = ApiConfig.baseUrl,
    engine: HttpClientEngine = EngineProvider.engine,
    connectTimeoutMs: Long = ApiConfig.connectTimeoutMs,
    sendTimeoutMs: Long = ApiConfig.sendTimeoutMs,
    receiveTimeoutMs: Long = ApiConfig.receiveTimeoutMs,
    enableLogging: Boolean = false
): HttpClient = HttpClient(engine) {
    defaultRequest {
        url(baseUrl)
    headers["User-Agent"] = "NetBench/1.0"
        headers["Cache-Control"] = "no-cache"
        headers[HttpHeaders.Accept] = ContentType.Application.Json.toString()
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }

    install(HttpTimeout) {
        connectTimeoutMillis = connectTimeoutMs
        requestTimeoutMillis = sendTimeoutMs
        socketTimeoutMillis = receiveTimeoutMs
    }

    install(Logging) {
        // Enforce no HTTP logging in release; optionally allow in debug
        level = if (enableLogging) LogLevel.HEADERS else LogLevel.NONE
    }
}
