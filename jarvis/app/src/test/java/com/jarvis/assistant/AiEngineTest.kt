package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiEngineTest {
    @Test
    fun classifies_routes() {
        assertEquals("coding", classifyTask("fix the Kotlin compile error"))
        assertEquals("reasoning", classifyTask("compare these two designs and reason about tradeoffs"))
        assertEquals("vision", classifyTask("what is on my screen?"))
        assertEquals("speech", classifyTask("transcribe this recording"))
        assertEquals("fast", classifyTask("hello jarvis"))
    }

    @Test
    fun parses_openai_sse_tokens() {
        assertEquals("Hi", extractSseText("data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}"))
        assertNull(extractSseText("data: [DONE]"))
        assertNull(extractSseText("event: ping"))
    }

    @Test
    fun builds_chat_endpoint() {
        assertEquals("https://api.example.com/v1/chat/completions", chatEndpoint("https://api.example.com/v1"))
        assertEquals("https://api.example.com/v1/chat/completions", chatEndpoint("https://api.example.com/v1/chat/completions"))
    }
}
