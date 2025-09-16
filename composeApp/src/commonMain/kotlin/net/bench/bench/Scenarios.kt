package net.bench.bench

enum class ScenarioId { S1_SMALL, S2_LIST, S3_ERROR, S4_TIMEOUT, S5_OFFLINE, S6_HEADERS }

data class ScenarioPreset(
    val id: ScenarioId,
    val title: String,
    val baseUrl: String,
    val path: String,
    val connectTimeoutMs: Int,
    val sendTimeoutMs: Int,
    val receiveTimeoutMs: Int,
    val enableRetry: Boolean = false,
    val note: String = ""
)

object BenchScenarios {
    val all: List<ScenarioPreset> = listOf(
    ScenarioPreset(ScenarioId.S1_SMALL,   "S1 Small",   "https://dummyjson.com", "/posts/1",            8000, 8000, 8000, false, "Latency single small payload"),
    ScenarioPreset(ScenarioId.S2_LIST,    "S2 List",    "https://dummyjson.com", "/posts?limit=100",    8000, 8000, 8000, false, "List parsing"),
    ScenarioPreset(ScenarioId.S3_ERROR,   "S3 Error",   "https://httpbingo.org", "/status/500",         8000, 8000, 8000, false, "Deterministic 500"),
    ScenarioPreset(ScenarioId.S4_TIMEOUT, "S4 Timeout", "https://httpbingo.org", "/delay/10",           1000, 1000, 1000, false, "200 delayed -> timeout"),
    ScenarioPreset(ScenarioId.S5_OFFLINE, "S5 Offline", "https://dummyjson.com", "/posts/1",            8000, 8000, 8000, false, "Enable airplane mode"),
    ScenarioPreset(ScenarioId.S6_HEADERS, "S6 Headers", "https://httpbingo.org", "/headers",            8000, 8000, 8000, false, "Echo headers (httpbin)")
    )
}
