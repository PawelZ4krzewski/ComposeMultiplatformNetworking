package net.bench.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual object EngineProvider { actual val engine: HttpClientEngine = OkHttp.create {
    config {
        retryOnConnectionFailure(false)
        cache(null)
    }
} }
