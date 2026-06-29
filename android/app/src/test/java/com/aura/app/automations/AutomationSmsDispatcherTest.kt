package com.aura.app.automations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AutomationSmsDispatcherTest {
    @Test
    fun singlePartMessageUsesSingleTextApi() {
        val gateway = RecordingSmsGateway(parts = listOf("hello"))

        AndroidAutomationSmsDispatcher(gateway).send("+15555550123", "hello")

        assertEquals(listOf("+15555550123" to "hello"), gateway.singleMessages)
        assertEquals(emptyList<Pair<String, List<String>>>(), gateway.multipartMessages)
    }

    @Test
    fun encodedMultipartMessageUsesMultipartApi() {
        val parts = listOf("first part", "second part", "third part")
        val gateway = RecordingSmsGateway(parts)

        AndroidAutomationSmsDispatcher(gateway).send("+15555550123", "long message")

        assertEquals(emptyList<Pair<String, String>>(), gateway.singleMessages)
        assertEquals(listOf("+15555550123" to parts), gateway.multipartMessages)
    }

    @Test
    fun dispatchUsesPlatformEncodingAwareMessageSplit() {
        val gateway = RecordingSmsGateway(parts = listOf("encoded"))

        AndroidAutomationSmsDispatcher(gateway).send("+15555550123", "message body")

        assertEquals(listOf("message body"), gateway.dividedBodies)
        assertEquals(listOf("+15555550123" to "encoded"), gateway.singleMessages)
    }

    @Test
    fun emptyEncodingResultFailsWithoutDispatching() {
        val gateway = RecordingSmsGateway(parts = emptyList())

        val failure = assertThrows(IllegalArgumentException::class.java) {
            AndroidAutomationSmsDispatcher(gateway).send("+15555550123", "message")
        }

        assertEquals("SMS body could not be encoded", failure.message)
        assertEquals(emptyList<Pair<String, String>>(), gateway.singleMessages)
        assertEquals(emptyList<Pair<String, List<String>>>(), gateway.multipartMessages)
    }
}

private class RecordingSmsGateway(
    private val parts: List<String>
) : AutomationSmsGateway {
    val dividedBodies = mutableListOf<String>()
    val singleMessages = mutableListOf<Pair<String, String>>()
    val multipartMessages = mutableListOf<Pair<String, List<String>>>()

    override fun divideMessage(body: String): List<String> {
        dividedBodies += body
        return parts
    }

    override fun sendTextMessage(recipient: String, body: String) {
        singleMessages += recipient to body
    }

    override fun sendMultipartTextMessage(recipient: String, parts: List<String>) {
        multipartMessages += recipient to parts
    }
}
