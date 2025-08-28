package net.bench.core

@Suppress("NOTHING_TO_INLINE")
inline fun logDebug(message: String) {
    // Keep minimal; println is fine for benchmarking; tooling can capture logs
    println(message)
}
