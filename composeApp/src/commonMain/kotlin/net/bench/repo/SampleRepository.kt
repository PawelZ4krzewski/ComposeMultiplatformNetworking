package net.bench.repo

import net.bench.network.ApiService

class SampleRepository(private val api: ApiService) {
    suspend fun fetch(path: String) = api.fetch(path)
}
