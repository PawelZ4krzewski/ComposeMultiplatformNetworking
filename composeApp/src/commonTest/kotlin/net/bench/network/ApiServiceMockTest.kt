package net.bench.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ApiServiceMockTest {
    @Test
    fun deserializes_and_headers_present() = runTest {
        val engine = MockEngine { request ->
            assertEquals("NetBench/1.0", request.headers["User-Agent"])
            assertEquals("no-cache", request.headers["Cache-Control"])
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            respond(
                content = "{" + "\"id\":1,\"title\":\"t\",\"body\":\"b\"" + "}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(engine) {
            defaultRequest {
                url("http://test")
    headers["User-Agent"] = "NetBench/1.0"
                headers["Cache-Control"] = "no-cache"
    headers[HttpHeaders.Accept] = ContentType.Application.Json.toString()
                accept(ContentType.Application.Json)
            }
            install(ContentNegotiation) { json() }
    }
    val service = ApiService(client)
    val res = service.fetch("/todos/1")
        require(res is ApiResult.Success)
        assertEquals(200, res.statusCode)
        assertEquals(1, res.data.id)
    }

    @Test
    fun maps_4xx_and_5xx() = runTest {
        val engine4 = MockEngine { respond("bad", HttpStatusCode.BadRequest) }
    val client4 = HttpClient(engine4) { install(ContentNegotiation) { json() } }
    val res4 = ApiService(client4).fetch("/todos/1")
        require(res4 is ApiResult.NetworkError)

        val engine5 = MockEngine { respond("err", HttpStatusCode.InternalServerError) }
    val client5 = HttpClient(engine5) { install(ContentNegotiation) { json() } }
    val res5 = ApiService(client5).fetch("/todos/1")
        require(res5 is ApiResult.NetworkError)
    }
}
