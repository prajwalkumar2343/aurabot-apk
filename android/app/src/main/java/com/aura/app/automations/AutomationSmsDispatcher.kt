package com.aura.app.automations

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

fun interface AutomationSmsDispatcher {
    fun send(recipient: String, body: String)
}

internal class AndroidAutomationSmsDispatcher(
    private val gateway: AutomationSmsGateway
) : AutomationSmsDispatcher {
    constructor(context: Context) : this(SmsManagerAutomationSmsGateway(context))

    override fun send(recipient: String, body: String) {
        val parts = gateway.divideMessage(body)
        require(parts.isNotEmpty()) { "SMS body could not be encoded" }
        if (parts.size == 1) {
            gateway.sendTextMessage(recipient, parts.single())
        } else {
            gateway.sendMultipartTextMessage(recipient, parts)
        }
    }
}

internal interface AutomationSmsGateway {
    fun divideMessage(body: String): List<String>
    fun sendTextMessage(recipient: String, body: String)
    fun sendMultipartTextMessage(recipient: String, parts: List<String>)
}

private class SmsManagerAutomationSmsGateway(context: Context) : AutomationSmsGateway {
    private val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }

    override fun divideMessage(body: String): List<String> = smsManager.divideMessage(body)

    override fun sendTextMessage(recipient: String, body: String) {
        smsManager.sendTextMessage(recipient, null, body, null, null)
    }

    override fun sendMultipartTextMessage(recipient: String, parts: List<String>) {
        smsManager.sendMultipartTextMessage(recipient, null, ArrayList(parts), null, null)
    }
}
