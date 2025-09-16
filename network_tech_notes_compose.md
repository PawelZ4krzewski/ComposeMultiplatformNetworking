# Project Snapshot

- Modules / layout (settings.gradle.kts)
  - Root project: `ComposeMultiplatformNetworking`
  - Included module: `:composeApp`
  - Source sets: `composeApp/src/commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidUnitTest`
- Key packages
  - `net.bench.network` (ApiConfig, HttpClientFactory, ApiService, ApiResult, engines, models)
  - `net.bench.ui` (BenchViewModel, BenchScreen, DataScreen)
  - `net.bench.repo` (DataRepository)
  - `net.bench.core` (logDebug)
  - `org.example.project` (App root composable)
- Language / runtime versions (gradle/libs.versions.toml)
  - Kotlin: 2.1.21
  - Compose Multiplatform: 1.8.2
  - Ktor: 3.0.3
  - kotlinx-serialization: 1.7.3
  - kotlinx-coroutines: 1.9.0
  - Android Gradle Plugin: 8.7.3
- Build plugins (root build.gradle.kts + composeApp/build.gradle.kts)
  - org.jetbrains.kotlin.multiplatform
  - com.android.application
  - org.jetbrains.compose
  - org.jetbrains.kotlin.plugin.compose
  - org.jetbrains.kotlin.plugin.serialization
  - com.github.gmazzo.buildconfig (5.4.0 - only applied in `composeApp`)
- Targets (composeApp/build.gradle.kts)
  - Android (applicationId `org.example.project`)
  - iOS: `iosX64`, `iosArm64`, `iosSimulatorArm64` (framework `ComposeApp`, static)

# Networking Stack Overview

- HTTP client library: Ktor Client (3.0.3) (`libs.versions.toml` -> `ktor-client-core`)
- Engines
  - Android: OkHttp (`HttpEngine.android.kt`) – disables retry and cache.
  - iOS: Darwin (`HttpEngine.ios.kt`).
- JSON: kotlinx.serialization (`ktor-serialization-kotlinx-json`) with `ContentNegotiation` plugin.
- Logging: Ktor `Logging` plugin installed with `level = LogLevel.NONE` unless `enableLogging=true` passed to `createHttpClient`.
- Default headers (HttpClientFactory.kt)
  - User-Agent: `NetBench/1.0`
  - Cache-Control: `no-cache`
  - Accept: `application/json`
- Timeouts: Only via `HttpTimeout` plugin (connect / request / socket). Values passed from parameters defaulting to `ApiConfig` (BuildConfig). Overridable per client creation (e.g., BenchViewModel) and build properties.
- Overridability: BuildConfig fields from gradle -P props; runtime override for Bench via state fields.

# Configuration & Build-Time Flags

- BuildConfig fields (composeApp/build.gradle.kts `buildConfig` block in `commonMain` + Android `defaultConfig`):
  - `String BASE_URL` default `https://jsonplaceholder.typicode.com`
  - `long CONNECT_TIMEOUT_MS` default `8000`
  - `long SEND_TIMEOUT_MS` default `8000`
  - `long RECEIVE_TIMEOUT_MS` default `8000`
  - `boolean ENABLE_RETRY` default `false`
- Provided by Gradle properties: `-PBASE_URL=... -PCONNECT_TIMEOUT_MS=...` etc.
- Access wrapper (`ApiConfig.kt`): exposes `baseUrl`, `connectTimeoutMs`, `sendTimeoutMs`, `receiveTimeoutMs`, `enableRetry` from BuildConfig.
- Platform actuals (ApiConfig.android.kt / ApiConfig.ios.kt) implement `platformBaseUrl()` returning `BuildConfig.BASE_URL`.

Code excerpt (ApiConfig.kt):
```kotlin
object ApiConfig {
    val baseUrl: String = platformBaseUrl()
    val connectTimeoutMs: Long = BuildConfig.CONNECT_TIMEOUT_MS
    val sendTimeoutMs: Long = BuildConfig.SEND_TIMEOUT_MS
    val receiveTimeoutMs: Long = BuildConfig.RECEIVE_TIMEOUT_MS
    val enableRetry: Boolean = BuildConfig.ENABLE_RETRY
}
```

# Request Flow (Step-by-Step)

1. UI triggers (BenchScreen Run button -> `BenchViewModel.runBench()`; DataScreen initial load / Refresh -> `load()` lambda).
2. ViewModel (Bench) or composable (Data) creates Ktor client via `createHttpClient(baseUrl = state.baseUrl, timeouts...)`.
3. Repository (DataScreen) uses `DataRepository` -> calls `ApiService.fetchList(path)`.
4. BenchViewModel directly creates `ApiService` and calls `fetch(path)` in a loop.
5. `ApiService` (fetch / fetchList): start monotonic mark, execute `client.get(path)`, collect status & body.
6. On success: decode JSON (single object or list) to `SampleDto` / `List<SampleDto>`.
7. Compute elapsed: `val ms = mark.elapsedNow().inWholeMilliseconds`; log via `logDebug("NET_GET_MS: $ms")` exactly once per attempt (both in success and in catch path).
8. Map to `ApiResult.Success(data, status, ms)` or classify error status/exception -> `ApiResult.NetworkError`.
9. Retry logic: if `enableRetry` -> up to 2 attempts (maxAttempts=2) with `delay(500)` on failure before final.
10. Bench aggregates computed after loop; Data updates UI state.

Timing log label: `NET_GET_MS` (ApiService.kt lines around measurements).

# Error Taxonomy

Mapping location: `ApiService.mapError(t)` and status branch in both fetch methods.

| Kind | Conditions | Mapped From | File/Function |
|------|------------|-------------|---------------|
| Timeout | Exception | HttpRequestTimeoutException | ApiService.mapError |
| NoInternet | Exception | IOException (`io.ktor.utils.io.errors.IOException`) | ApiService.mapError |
| Http4xx | Status 400..499 | Non-success HTTP response | ApiService.fetch / fetchList status branch |
| Http5xx | Status 500..599 | Non-success HTTP response | Same |
| Cancel | Exception | CancellationException | ApiService.mapError |
| Unknown | Exception fallback or other status | Any other Throwable / status else branch | ApiService.fetch / fetchList |

Excerpt (ApiService.kt lines ~157-166):
```kotlin
private fun mapError(t: Throwable): ApiResult.NetworkError = when (t) {
    is CancellationException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Cancel, null, t.message)
    is HttpRequestTimeoutException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Timeout, null, t.message)
    is IOException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.NoInternet, null, t.message)
    is SerializationException -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Unknown, null, t.message)
    else -> ApiResult.NetworkError(ApiResult.NetworkError.Kind.Unknown, null, t.message)
}
```

# Timeouts & Headers

## Timeouts Table

| Connect | Send/Write (request) | Receive/Socket | Default (ms) | Defined In | Override |
|---------|----------------------|----------------|--------------|-----------|----------|
| connectTimeoutMillis | requestTimeoutMillis | socketTimeoutMillis | 8000 each | `HttpClientFactory.kt` (install(HttpTimeout)) using params defaulting to `ApiConfig` | Gradle -P & runtime (BenchViewModel state / DataScreen client creation) |

## Headers Table

| Header | Value | Set In |
|--------|-------|--------|
| User-Agent | NetBench/1.0 | `HttpClientFactory.kt` defaultRequest |
| Cache-Control | no-cache | `HttpClientFactory.kt` defaultRequest |
| Accept | application/json | `HttpClientFactory.kt` defaultRequest |

Confirm no double timeouts: OkHttp engine config in `HttpEngine.android.kt` does not set timeouts (only disables retry & cache) -> only HttpTimeout authoritative.

Engine excerpt (HttpEngine.android.kt):
```kotlin
OkHttp.create {
    config {
        retryOnConnectionFailure(false)
        cache(null)
    }
}
```

# Data Models & Serialization

- DTOs: `SampleDto(id: Int?, title: String?, body: String?)` (`ApiModels.kt`).
- Serialization config (HttpClientFactory.kt): `ignoreUnknownKeys = true`, `explicitNulls = false`.

Excerpt (ApiModels.kt):
```kotlin
@Serializable
data class SampleDto(
    val id: Int? = null,
    val title: String? = null,
    val body: String? = null,
)
```

# Key Source Files (with excerpts)

## HttpClientFactory.kt (`composeApp/src/commonMain/kotlin/net/bench/network/HttpClientFactory.kt`)
- Builds HttpClient with headers, JSON, timeouts, logging off by default.
```kotlin
fun createHttpClient(
    baseUrl: String = ApiConfig.baseUrl,
    ...
): HttpClient = HttpClient(engine) {
    defaultRequest {
        url(baseUrl)
        headers["User-Agent"] = "NetBench/1.0"
        headers["Cache-Control"] = "no-cache"
        headers[HttpHeaders.Accept] = ContentType.Application.Json.toString()
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(HttpTimeout) { connectTimeoutMillis = connectTimeoutMs; requestTimeoutMillis = sendTimeoutMs; socketTimeoutMillis = receiveTimeoutMs }
    install(Logging) { level = if (enableLogging) LogLevel.HEADERS else LogLevel.NONE }
}
```

## ApiService.kt (`composeApp/src/commonMain/kotlin/net/bench/network/ApiService.kt`)
- Two methods: `fetch` (single) and `fetchList` (array). Retry loop, timing, error mapping.
```kotlin
while (attempt < maxAttempts) {
    attempt++
    val mark = TimeSource.Monotonic.markNow()
    try {
        val response: HttpResponse = client.get(path)
        ...
    } catch (t: Throwable) {
        val ms = mark.elapsedNow().inWholeMilliseconds
        logDebug("NET_GET_MS: $ms")
        val err = mapError(t)
        lastError = err
        if (attempt < maxAttempts) { delay(500); continue } else break
    }
    val ms = mark.elapsedNow().inWholeMilliseconds
    logDebug("NET_GET_MS: $ms")
    ... status mapping ...
}
```

## ApiResult.kt (`composeApp/src/commonMain/kotlin/net/bench/network/ApiResult.kt`)
- Wrapper for success & network error classification (enum Kind).
```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T, val statusCode: Int, val durationMs: Long) : ApiResult<T>()
    data class NetworkError(...){ enum class Kind { Timeout, NoInternet, Http4xx, Http5xx, Cancel, Unknown } }
}
```

## ApiConfig.*
- Common expect + BuildConfig forwarding, platform actuals return BuildConfig.BASE_URL.
```kotlin
expect fun platformBaseUrl(): String
object ApiConfig { val baseUrl: String = platformBaseUrl(); ... }
// Android & iOS actual: actual fun platformBaseUrl(): String = BuildConfig.BASE_URL
```

## BenchViewModel.kt
- Orchestrates runs, computes aggregates (median, p95 = floor(0.95 * (n-1))).
```kotlin
val p95Index = if (times.isEmpty()) 0 else floor(0.95 * (times.size - 1)).toInt().coerceIn(0, times.lastIndex)
val p95 = if (times.isEmpty()) 0 else times[p95Index]
```

## DataScreen.kt
- Base URL & path inputs; loads list; handles loading/error states; closes client in finally.
```kotlin
val client = createHttpClient(baseUrl = baseUrl)
val repo = DataRepository(ApiService(client))
try { when (val res = repo.fetchList(path)) { ... } } finally { client.close() }
```

# Bench UI & Metrics

- Inputs (BenchState / UI controls): `baseUrl`, `path`, `runs`, `warmup`, `connectMs`, `sendMs`, `receiveMs`, `enableRetry`.
- Outputs: list of `BenchEntry` (ms, status, kind), aggregates (count, median, p95, min, max, error counts), `lastPayloadPreview`.
- Timing source: Only from ApiService (logged `NET_GET_MS`). UI does not measure durations itself.
- p95 formula: `floor(0.95 * (n - 1))` index into sorted non-zero durations (BenchViewModel.computeAggregates).

# Tests

- `commonTest` dependencies include: `ktor-client-mock`, `kotlinx-coroutines-test` (enabled for potential network logic tests) – actual test files not listed here => UNKNOWN (provide path if added later).
- `androidUnitTest` dependencies: `mockwebserver`, `kotlin-test-junit`, `junit` – no concrete test source files enumerated in current listing => UNKNOWN (androidUnitTest file paths?).
- Statement: No explicit networking tests found for headers, error mapping, retry, or list decoding (current repository snapshot).

# Build Types & Release Notes

- Android buildTypes: `release` (minifyDisabled = true), `debug` (no extra logging toggled; Logging plugin level remains NONE unless explicitly enabled when creating client).
- iOS frameworks built as static (composeApp/build.gradle.kts iosTarget.binaries.framework block).
- Example build command with overrides:
```bash
./gradlew :composeApp:assembleDebug \
  -PBASE_URL=https://jsonplaceholder.typicode.com \
  -PCONNECT_TIMEOUT_MS=5000 -PSEND_TIMEOUT_MS=5000 -PRECEIVE_TIMEOUT_MS=5000 \
  -PENABLE_RETRY=true
```
- R8/Proguard: Not enabled (minifyDisabled=true) -> potential optimization left out for simplicity.

# Known Limitations & TODO

| Area | Observation |
|------|-------------|
| Retry logic | Fixed maxAttempts=2; no exponential backoff configuration. |
| Error specificity | IOException lumped as NoInternet (cannot differentiate TLS vs DNS). |
| JSON decoding | Manual simple parsing for arrays (no streaming). |
| Resource lifecycle | Client created per run / per load; no pooling or reuse (intentional for isolation). |
| Logging detail | Only duration logged; no correlation IDs or attempt index in log. |
| Tests | Missing explicit tests for retry, error mapping, p95 calculation, header assertions. |
| BuildConfig duplication | Both common and Android defaultConfig define BuildConfig fields (duplication risk). |
| Performance | Busy loop of runs sequential, no concurrency or cancellation optimizations. |

# Appendix: Dependency Versions

| Library / Plugin | Version | Declared In |
|------------------|---------|-------------|
| Kotlin | 2.1.21 | gradle/libs.versions.toml |
| Compose Multiplatform | 1.8.2 | gradle/libs.versions.toml |
| Ktor | 3.0.3 | gradle/libs.versions.toml |
| kotlinx-serialization-json | 1.7.3 | gradle/libs.versions.toml |
| kotlinx-coroutines-core | 1.9.0 | gradle/libs.versions.toml |
| Android Gradle Plugin | 8.7.3 | gradle/libs.versions.toml (agp) |
| mockwebserver | 4.12.0 | gradle/libs.versions.toml |
| ktor-client-logging | 3.0.3 | gradle/libs.versions.toml |
| ktor-client-okhttp | 3.0.3 | gradle/libs.versions.toml |
| ktor-client-darwin | 3.0.3 | gradle/libs.versions.toml |
| ktor-client-mock | 3.0.3 | gradle/libs.versions.toml |
| buildconfig plugin | 5.4.0 | composeApp/build.gradle.kts (applied) |
| junit | 4.13.2 | gradle/libs.versions.toml |

<!-- End of report -->

## Addendum (Clarifications & Updates)

### BASE_URL Source & Override
Default declared twice in `composeApp/build.gradle.kts`:
```kotlin
val baseUrlProp = (project.findProperty("BASE_URL") as String?) ?: "https://jsonplaceholder.typicode.com"
val aBaseUrl = (project.findProperty("BASE_URL") as String?) ?: "https://jsonplaceholder.typicode.com"
```
Override example (Android install):
```bash
./gradlew :composeApp:installDebug \
  -PBASE_URL=https://api.example.com \
  -PCONNECT_TIMEOUT_MS=5000 -PSEND_TIMEOUT_MS=5000 -PRECEIVE_TIMEOUT_MS=7000 \
  -PENABLE_RETRY=false
```

### Tests Status
No networking tests present. Only found: `composeApp/src/commonTest/kotlin/org/example/project/ComposeAppCommonTest.kt` (does not touch networking). No usages of `MockWebServer` / `MockEngine` in test sources.

### Minify & Size Inspection
Current: `release { isMinifyEnabled = false }`. Enable by setting true and adding ProGuard rules. Size check:
```bash
./gradlew :composeApp:assembleRelease
ls -lh composeApp/build/outputs/apk/release/
du -h composeApp/build/outputs/apk/release/composeApp-release.apk
```

### iOS Run Tips
Generate framework:
```bash
./gradlew :composeApp:syncFramework
```
Then integrate into an Xcode host app (no `iosApp` module here). If memory issues occur: add to `gradle.properties`:
`org.gradle.jvmargs=-Xmx6g` and `kotlin.daemon.jvmargs=-Xmx4096m`.

### Sample Logs & Aggregates (Illustrative)
```text
NET_GET_MS: 182
NET_GET_MS: 176
NET_GET_MS: 191
```
Sample aggregate line format:
```
count=20 median=178 p95=191 min=170 max=205 timeout=0 noInternet=0 http4xx=0 http5xx=0 cancel=0 unknown=0
```

### Single Source Timeouts Confirmation
Authoritative: Ktor `HttpTimeout` (HttpClientFactory). OkHttp engine shows no explicit timeouts:
```kotlin
OkHttp.create {
  config {
    retryOnConnectionFailure(false)
    cache(null)
  }
}
```

## Benchmark Scenarios (S1–S6)

S1 Small — https://dummyjson.com/posts/1 (timeouts 8000/8000/8000 ms, retry=OFF)

S2 List — https://dummyjson.com/posts?limit=100 (timeouts 8000/8000/8000 ms, retry=OFF)

S3 Error — https://httpbingo.org/status/500 (timeouts 8000/8000/8000 ms, retry=OFF)

S4 Timeout — https://httpbingo.org/delay/10 (timeouts 1000/1000/1000 ms, retry=OFF)

S5 Offline — https://dummyjson.com/posts/1 (airplane mode to induce NoInternet; timeouts 8000/8000/8000 ms, retry=OFF)

S6 Headers — https://httpbingo.org/headers (timeouts 8000/8000/8000 ms, retry=OFF)

## Measurement Configuration (parity)

Headers (defaultRequest): User-Agent=NetBench/1.0, Cache-Control=no-cache, Accept=application/json

One HttpClient per benchmark run series; exactly one timing measurement per request in ApiService (TimeSource.Monotonic) and exactly one `NET_GET_MS` log line per request.

Warm-up: ON (drop first), N=30 → reported samples=29

Build types for measurement: Profile/Release (avoid debug overhead); HTTP logging OFF; retry OFF.

Timeout parity: S1/S2/S6/S3/S5 = 8000/8000/8000 ms; S4 = 1000/1000/1000 ms.

Error taxonomy: Timeout, NoInternet, Http4xx, Http5xx, Cancel, Unknown

P95 computation: floor(0.95*(n-1)) on sorted durations

## Recent Changes (2025-09-05)

- Android INTERNET permission ensured in app manifest
- Fixed IOException import to io.ktor.utils.io.errors.IOException (proper mapping for NoInternet/IO errors)
- Propagated durationMs on error path: ApiResult.NetworkError now carries measured time; BenchViewModel uses it
- Bench “Run All” race fixed (wait for start → wait for finish)
- Presets aligned to httpbingo for /status/500 and /delay/10; S6 uses /headers
- Deterministic headers and timeouts consolidated in HttpClientFactory/ApiConfig

## Latest Results (Android device; iOS simulator optional)

```
scenario,N,median_ms,p95_ms,min_ms,max_ms,errors
S1 Small,29,404,442,283,501,0
S2 List,29,621,711,391,732,0
S3 Error,29,177,312,173,371,29
S4 Timeout,29,1020,1021,1016,1022,29
S5 Offline,29,30,31,9,32,29
S6 Headers,29,305,386,293,396,0
```

## Interpretation Highlights

S1 small JSON: Compose median ≈404 ms; S2 list 100: ≈621 ms (expected overhead from payload + decode)

S3 (500): non-zero failure-path latency (median ≈177 ms); S4 (timeout): ≈1020 ms (close to 1 s cap)

S5 (offline): fast-fail (~30 ms); S6 headers: median ≈305 ms (echo path)

Lower is better; report both median and P95; error scenarios measure failure-path time

## Replication Notes

Android run: `./gradlew :composeApp:installDebug -PBASE_URL=https://dummyjson.com -PCONNECT_TIMEOUT_MS=8000 -PSEND_TIMEOUT_MS=8000 -PRECEIVE_TIMEOUT_MS=8000 -PENABLE_RETRY=false`

iOS (framework integration):
`./gradlew :composeApp:syncFramework`
Framework output under `composeApp/build/xcode-frameworks`. Link the generated framework in your Xcode host app and run on iPhone 15 Simulator (or device) with identical endpoints, headers, timeouts (retry=OFF).

Bench UI: select preset, N=30, Warm-up ON, then “Copy CSV/MD” for logging; NET_GET_MS visible in logs

## Threats to Validity

- Public endpoints’ geography → tail variance (P95); keep same Wi-Fi and repeat 2–3 series
- iOS on simulator vs Android physical device; document environment versions
- Do not mix build types; keep headers and timeouts identical across tools

## Tests (current state & how to run)

State: No implemented network unit tests yet; only placeholder/common sample test. Recommended cases (commonTest with Ktor MockEngine):
- S1 (200 small JSON object) – verify duration recorded & success classification.
- S2 (200 list ~100 items) – verify list size and decode path.
- S3 (HTTP 500) – verify Http5xx classification & duration non-zero.
- S4 (delay > timeout) – simulate suspend delay causing HttpRequestTimeoutException → Timeout classification.
- S5 (No internet) – raise IOException mapping to NoInternet.
Android-specific (androidUnitTest with MockWebServer):
- S6 headers echo: assert outbound request includes User-Agent=NetBench/1.0, Cache-Control=no-cache, Accept=application/json (case-insensitive).

Bench (manual): Use Bench screen presets S1–S6, set N=30, Warm-up ON (drop first sample), export CSV/MD; ensure exactly one NET_GET_MS per request in logs.

Run commands:
```
./gradlew :composeApp:check
./gradlew :composeApp:testDebugUnitTest
```
(If an aggregate task like :composeApp:allTests is later added, document it; currently not present.)

No full HTTP integration tests (real network) are included—intentional; manual bench provides reproducibility focus.

## Environment for Reproducibility

Toolchain (from repo):
- Kotlin: 2.1.21
- Compose Multiplatform: 1.8.2
- Ktor: 3.0.3
- Coroutines: 1.9.0
- Serialization: 1.7.3
- Android Gradle Plugin: 8.7.3
- Gradle Wrapper: 8.9
- JVM target (Android): 11

Runtime environments (specify when reporting):
- Android physical device: <MODEL>/<API_LEVEL>, build type Profile or Release.
- iOS simulator (e.g., iPhone 15): iOS <VERSION>, Xcode <VERSION>, Release/Run.

Network conditions: Same Wi-Fi, minimal background traffic, similar time-of-day. N=30 runs, warm-up drop=1. HTTP logging OFF, retry OFF.

## Parity Checklist (Compose vs Flutter)

Identical knobs:
- Headers: User-Agent=NetBench/1.0, Cache-Control=no-cache, Accept=application/json
- Timeouts: S1/S2/S6 8000/8000/8000 ms; S4 1000/1000/1000 ms; S3/S5 same as S1
- Retry: OFF (measurement)
- One HttpClient per series; single timing in data layer; exactly one NET_GET_MS log per request
- P95 formula: floor(0.95*(n-1))
- Endpoints: dummyjson.com (/posts/1, /posts?limit=100), httpbingo.org (/status/500, /delay/10, /headers)
- Warm-up: discard first attempt

## Exact Commands (Build / Run / Bench)

Android build (debug / release for size):
```
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:assembleRelease
```

Android install & run (debug measurement config):
```
./gradlew :composeApp:installDebug \
  -PBASE_URL=https://dummyjson.com \
  -PCONNECT_TIMEOUT_MS=8000 -PSEND_TIMEOUT_MS=8000 -PRECEIVE_TIMEOUT_MS=8000 \
  -PENABLE_RETRY=false
```

iOS (framework integration):
Build KMP framework for Simulator:
```
./gradlew :composeApp:syncFramework
```
Output: `composeApp/build/xcode-frameworks`. Open your own Xcode host app, add the framework, run on iPhone 15 Simulator (or device). Use same base URL, headers, timeouts, retry=OFF for parity.

Bench procedure: In Bench screen pick preset (S1–S6) → set N=30, Warm-up ON → Run → after completion use Copy CSV / Copy MD; inspect logs for NET_GET_MS entries.

## Payload Notes

- S1: Small JSON object (single post) – latency baseline.
- S2: List (~100 posts) – larger payload; added network transfer + JSON decode overhead.
- S6: Headers echo – minimal body; verifies header correctness. Header extraction treats keys case-insensitive (lower-case map) for user-agent / cache-control / accept.

## CSV Schema (export)

Schema (columns order):
```
tool,platform,scenario,N,warmup,connect_timeout_ms,send_timeout_ms,receive_timeout_ms,median_ms,p95_ms,min_ms,max_ms,errors,notes
```
Lower is better; error scenarios measure failure-path latency.

## Sample Logs (NET_GET_MS)

Example timing + attempt logs (one NET_GET_MS per request):
```
NET_GET_MS: 404
BENCH_ATTEMPT i=5 status=500 kind=Http5xx durationMs=177
```

## Latest Results (Compose, 2025-09-05)

Already present above (see Latest Results section). Values:
```
scenario,N,median_ms,p95_ms,min_ms,max_ms,errors
S1 Small,29,404,442,283,501,0
S2 List,29,621,711,391,732,0
S3 Error,29,177,312,173,371,29
S4 Timeout,29,1020,1021,1016,1022,29
S5 Offline,29,30,31,9,32,29
S6 Headers,29,305,386,293,396,0
```

## Interpretation Highlights

- S1 vs S2: Expected increase from larger list payload (transfer + decode).
- S3: Non-zero failure-path latency confirms timing captured before classification.
- S4: ~1 s near timeout cap (1000 ms configuration) with minimal variance.
- S5: Fast-fail path (~30 ms) typical of immediate network unavailability detection.
- S6: Header echo low latency consistent with small response.
- Lower is better; report median + P95; error scenarios measure failure-path time not excluded from stats.

## Threats to Validity

Public endpoints’ geography and routing cause tail variance; repeat 2–3 series and compare medians. iOS simulator timing may differ from physical hardware; record device/simulator versions. Avoid mixing build types; enforce identical headers, timeouts, and retry config.

---
Checklist:
Added/updated: Tests, Environment for Reproducibility, Parity Checklist, Exact Commands, Payload Notes, Latest Results (presence verified), Interpretation Highlights, Threats to Validity.
Toolchain versions sourced from repo. Commands are copy-paste ready. Benchmark Scenarios section unchanged and accurate. Document now comprehensive for LLM thesis chapter on networking implementation & testing.

