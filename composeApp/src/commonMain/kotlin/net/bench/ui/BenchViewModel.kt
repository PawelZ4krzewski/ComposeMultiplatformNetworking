package net.bench.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import net.bench.network.ApiConfig
import net.bench.network.ApiResult
import net.bench.network.ApiService
import net.bench.network.createHttpClient
import kotlin.math.floor

data class BenchEntry(val ms: Long, val status: Int?, val kind: ApiResult.NetworkError.Kind?)

data class BenchState(
    val baseUrl: String = ApiConfig.baseUrl,
    val path: String = "/todos/1",
    val runs: Int = 30,
    val warmup: Boolean = true,
    val connectMs: Long = ApiConfig.connectTimeoutMs,
    val sendMs: Long = ApiConfig.sendTimeoutMs,
    val receiveMs: Long = ApiConfig.receiveTimeoutMs,
    val enableRetry: Boolean = ApiConfig.enableRetry,
    val running: Boolean = false,
    val results: List<BenchEntry> = emptyList(),
    val lastPayloadPreview: String = "",
    val aggregates: Aggregates = Aggregates()
)

data class Aggregates(
    val count: Int = 0,
    val median: Long = 0,
    val p95: Long = 0,
    val min: Long = 0,
    val max: Long = 0,
    val timeout: Int = 0,
    val noInternet: Int = 0,
    val http4xx: Int = 0,
    val http5xx: Int = 0,
    val cancel: Int = 0,
    val unknown: Int = 0
)

class BenchViewModel {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()) + Dispatchers.Default
    private val _state = MutableStateFlow(BenchState())
    val state: StateFlow<BenchState> = _state

    fun updatePath(value: String) = _state.update { it.copy(path = value) }
    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }
    fun updateRuns(value: Int) = _state.update { it.copy(runs = value.coerceAtLeast(1)) }
    fun updateWarmup(value: Boolean) = _state.update { it.copy(warmup = value) }
    fun updateConnectMs(value: Long) = _state.update { it.copy(connectMs = value.coerceAtLeast(1)) }
    fun updateSendMs(value: Long) = _state.update { it.copy(sendMs = value.coerceAtLeast(1)) }
    fun updateReceiveMs(value: Long) = _state.update { it.copy(receiveMs = value.coerceAtLeast(1)) }
    fun updateRetry(value: Boolean) = _state.update { it.copy(enableRetry = value) }

    fun runBench() {
        val s = _state.value
        val client = createHttpClient(
            baseUrl = s.baseUrl,
            connectTimeoutMs = s.connectMs,
            sendTimeoutMs = s.sendMs,
            receiveTimeoutMs = s.receiveMs,
            enableLogging = false
        )
        val service = ApiService(client, enableRetry = s.enableRetry)

        scope.launch {
            try {
                _state.update {
                    it.copy(
                        running = true,
                        results = emptyList(),
                        lastPayloadPreview = "",
                        aggregates = Aggregates()
                    )
                }
                val all = mutableListOf<BenchEntry>()
                val runs = s.runs
                repeat(runs) { idx ->
                    when (val res = service.fetch(s.path)) {
                        is ApiResult.Success -> {
                            if (!(s.warmup && idx == 0)) all += BenchEntry(
                                res.durationMs,
                                res.statusCode,
                                null
                            )
                            _state.update { st ->
                                st.copy(
                                    lastPayloadPreview = res.data.toString().take(200)
                                )
                            }
                            println("BENCH_ATTEMPT idx=$idx status=${res.statusCode} ms=${res.durationMs} kind=OK")
                        }

                        is ApiResult.NetworkError -> {
                            if (!(s.warmup && idx == 0)) all += BenchEntry(
                                res.durationMs,
                                res.statusCode,
                                res.kind
                            )
                            println("BENCH_ATTEMPT idx=$idx status=${res.statusCode} ms=${res.durationMs} kind=${res.kind}")
                        }
                    }
                }
                val aggs = computeAggregates(all)
                _state.update {
                    it.copy(
                        running = false,
                        results = all.toList(),
                        aggregates = aggs
                    )
                }
                println("BENCH_SUMMARY count=${aggs.count} median=${aggs.median} p95=${aggs.p95} min=${aggs.min} max=${aggs.max} timeout=${aggs.timeout} noNet=${aggs.noInternet} http4xx=${aggs.http4xx} http5xx=${aggs.http5xx} cancel=${aggs.cancel} unknown=${aggs.unknown}")
            } finally {
                client.close()
            }
        }
    }

    fun runPreset(presetBase: net.bench.bench.ScenarioPreset, runs: Int, warmup: Boolean) {
        val preset = presetBase
        _state.update {
            it.copy(
                baseUrl = preset.baseUrl,
                path = preset.path,
                runs = runs,
                warmup = warmup,
                connectMs = preset.connectTimeoutMs.toLong(),
                sendMs = preset.sendTimeoutMs.toLong(),
                receiveMs = preset.receiveTimeoutMs.toLong(),
                enableRetry = preset.enableRetry
            )
        }
        runBench()
    }

    fun buildCsv(scenario: net.bench.bench.ScenarioPreset?): String {
        val st = _state.value
        val header = "index,status,errorKind,durationMs";
        val rows = st.results.mapIndexed { i, r -> "$i,${r.status ?: "-"},${r.kind ?: ""},${r.ms}" }
        val agg = st.aggregates
        val meta =
            "# tool=compose-kmp platform=? baseUrl=${st.baseUrl} path=${st.path} runs=${st.runs} warmup=${st.warmup} timeouts=${st.connectMs}/${st.sendMs}/${st.receiveMs} retry=${st.enableRetry} ua=NetBench/1.0"
        val summary =
            "# aggregates count=${agg.count} median=${agg.median} p95=${agg.p95} min=${agg.min} max=${agg.max} timeouts=${agg.timeout} noInternet=${agg.noInternet} http4xx=${agg.http4xx} http5xx=${agg.http5xx} cancel=${agg.cancel} unknown=${agg.unknown} scenario=${scenario?.id}";
        return (listOf(meta, header) + rows + summary).joinToString("\n")
    }

    fun buildMarkdown(scenario: net.bench.bench.ScenarioPreset?): String {
        val st = _state.value
        val a = st.aggregates
        val meta =
            "|tool|platform|scenario|baseUrl|path|runs|warmup|median|p95|min|max|timeouts|noNet|4xx|5xx|cancel|unknown|";
        val sep = "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|";
        val row =
            "|compose-kmp|?|${scenario?.id}|${st.baseUrl}|${st.path}|${st.runs}|${st.warmup}|${a.median}|${a.p95}|${a.min}|${a.max}|${a.timeout}|${a.noInternet}|${a.http4xx}|${a.http5xx}|${a.cancel}|${a.unknown}|";
        return listOf(meta, sep, row).joinToString("\n")
    }

    private fun computeAggregates(list: List<BenchEntry>): Aggregates {
        if (list.isEmpty()) return Aggregates()
        val times = list.map { it.ms }.filter { it > 0 }.sorted()
        val count = list.size
        val median = if (times.isEmpty()) 0 else if (times.size % 2 == 1) {
            times[times.size / 2]
        } else {
            val a = times[times.size / 2 - 1]
            val b = times[times.size / 2]
            (a + b) / 2
        }
        val p95Index = if (times.isEmpty()) 0 else floor(0.95 * (times.size - 1)).toInt()
            .coerceIn(0, times.lastIndex)
        val p95 = if (times.isEmpty()) 0 else times[p95Index]
        val min = times.firstOrNull() ?: 0
        val max = times.lastOrNull() ?: 0
        return Aggregates(
            count = count,
            median = median,
            p95 = p95,
            min = min,
            max = max,
            timeout = list.count { it.kind == ApiResult.NetworkError.Kind.Timeout },
            noInternet = list.count { it.kind == ApiResult.NetworkError.Kind.NoInternet },
            http4xx = list.count { it.kind == ApiResult.NetworkError.Kind.Http4xx },
            http5xx = list.count { it.kind == ApiResult.NetworkError.Kind.Http5xx },
            cancel = list.count { it.kind == ApiResult.NetworkError.Kind.Cancel },
            unknown = list.count { it.kind == ApiResult.NetworkError.Kind.Unknown }
        )
    }
}
