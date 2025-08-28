package net.bench.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual object EngineProvider { actual val engine: HttpClientEngine = Darwin.create { } }
