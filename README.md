Compose Multiplatform Networking (KMP + Ktor)

Minimal, reproducible networking bench app matching the Flutter reference. One screen with states Loading / Success / Error and a single button to send/retry a GET.

What it does
- GET {BASE_URL}{PATH} (default PATH: /todos/1)
- Headers: User-Agent=NetBench/1.0, Cache-Control=no-cache, Accept=application/json
- Timeouts: set via BuildConfig (defaults: 8000ms) and can be overridden at build time
- No retries, no caching, logging OFF (including release)
- JSON mapping to { id:Int?, title:String?, body:String? }

Config enforcement
- Android: BuildConfig.BASE_URL provided at build time or "WSTAW_URL" (fail-fast at startup)
- iOS: Info.plist BASE_URL default "WSTAW_URL" (fail-fast at startup)

## Configuration

Override config via Gradle properties:
```bash
./gradlew :composeApp:assembleDebug \
  -PBASE_URL=https://jsonplaceholder.typicode.com/posts \
  -PCONNECT_TIMEOUT_MS=8000 \
  -PSEND_TIMEOUT_MS=8000 \
  -PRECEIVE_TIMEOUT_MS=8000 \
  -PENABLE_RETRY=false
```

**Note**: BuildConfig is shared across platforms. On iOS, if BuildConfig fields are not visible, BASE_URL falls back to Info.plist with default "WSTAW_URL".

Run
- Android: build/install from Android Studio or Gradle tasks
- iOS: open iosApp/iosApp.xcodeproj in Xcode and Run

Measuring tips
- N≥20–50 requests per scenario
- Report median and P95
- Cold start between runs
- Keep HTTP logging OFF
- Use release/minified on Android (R8 ON)
- Stable network connection