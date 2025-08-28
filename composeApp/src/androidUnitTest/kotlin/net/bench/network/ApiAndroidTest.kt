package net.bench.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class ApiAndroidTest {
    @Test
    fun hits_baseurl_and_path_with_headers() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"title\":\"a\",\"body\":\"b\"}")
        )
        server.start()
        val baseUrl = server.url("/").toString().removeSuffix("/")

        val client = createHttpClient(baseUrl = baseUrl)
        val service = ApiService(client, enableRetry = false)

        runBlocking {
            val res = service.fetch("/todos/1")
            require(res is ApiResult.Success)
        }

        val recorded = server.takeRequest()
    assertEquals("/todos/1", recorded.path)
    assertEquals("NetBench/1.0", recorded.getHeader("User-Agent"))
        assertEquals("no-cache", recorded.getHeader("Cache-Control"))
        assertEquals("application/json", recorded.getHeader("Accept"))

        server.shutdown()
    }
}
