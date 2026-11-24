package com.brahma.mobile.brain

interface LlmEngine {
    val name: String
    fun isReady(): Boolean
    suspend fun generate(prompt: String, maxTokens: Int = 256): String
}
