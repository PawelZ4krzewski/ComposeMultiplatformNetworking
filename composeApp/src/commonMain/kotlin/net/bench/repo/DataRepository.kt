package net.bench.repo

import net.bench.network.ApiResult
import net.bench.network.ApiService
import net.bench.network.SampleDto

class DataRepository(private val api: ApiService) {
    suspend fun fetchList(path: String): ApiResult<List<SampleDto>> {
        // Use the new fetchList method from ApiService that properly handles arrays
        return api.fetchList(path)
    }
}
