package com.samuel.ielts.net

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MetaApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(key: String = "test-key"): MetaApi {
        val base = server.url("/v1").toString().trimEnd('/')
        return MetaApi(MetaConfig(baseUrl = base, apiKey = key))
    }

    @Test
    fun success_returnsAssistantContent() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"x","choices":[{"message":{"role":"assistant","content":"{\"overall\":6.5}"}}]}"""
            )
        )
        val result = api().mark("sys", "user")
        assertTrue(result is MetaResult.Success)
        assertEquals("{\"overall\":6.5}", (result as MetaResult.Success).content)
    }

    @Test
    fun requestShape_matchesPlan() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"content":"ok"}}]}"""
            )
        )
        api().mark("sys-prompt", "user-prompt")
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"model\":\"muse-spark-1.3-contributor\""))
        assertTrue(body.contains("\"reasoning_effort\":\"medium\""))
        assertTrue(body.contains("\"role\":\"developer\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"temperature\":0.2"))
        assertTrue(body.contains("\"strict\":true"))
        assertTrue(body.contains("sys-prompt"))
    }

    @Test
    fun unauthorized_returnsHttpError() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"bad key"}}""")
        )
        val result = api().mark("sys", "user")
        assertTrue(result is MetaResult.HttpError)
        assertEquals(401, (result as MetaResult.HttpError).code)
    }

    @Test
    fun blankKey_neverHitsNetwork() {
        val result = api(key = "").mark("sys", "user")
        assertTrue(result is MetaResult.Failure)
        assertEquals(0, server.requestCount)
    }
}
