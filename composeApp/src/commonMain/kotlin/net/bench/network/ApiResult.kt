package net.bench.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T, val statusCode: Int, val durationMs: Long) : ApiResult<T>()
    data class NetworkError(
        val kind: Kind,
        val statusCode: Int? = null,
        val message: String? = null
    ) : ApiResult<Nothing>() {
        enum class Kind { Timeout, NoInternet, Http4xx, Http5xx, Cancel, Unknown }
    }
}
