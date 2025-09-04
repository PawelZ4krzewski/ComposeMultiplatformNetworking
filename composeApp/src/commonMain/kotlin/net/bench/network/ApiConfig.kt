package net.bench.network

import net.bench.config.BuildConfig

expect fun platformBaseUrl(): String

object ApiConfig {
    val baseUrl: String = platformBaseUrl()
    val connectTimeoutMs: Long = BuildConfig.CONNECT_TIMEOUT_MS
    val sendTimeoutMs: Long = BuildConfig.SEND_TIMEOUT_MS
    val receiveTimeoutMs: Long = BuildConfig.RECEIVE_TIMEOUT_MS
    val enableRetry: Boolean = BuildConfig.ENABLE_RETRY
}
