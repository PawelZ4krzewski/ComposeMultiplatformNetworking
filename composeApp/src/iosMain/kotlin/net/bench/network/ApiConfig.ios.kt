package net.bench.network

import net.bench.config.BuildConfig

actual fun platformBaseUrl(): String = BuildConfig.BASE_URL
