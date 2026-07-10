package com.aura.app.automations

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationTemplateRendererTest {
    private val renderer = AutomationTemplateRenderer()

    @Test
    fun rendersWhitespaceAndDottedTokensWithoutChangingUnmatchedText() {
        val rendered = renderer.render(
            "ETA {{ eta.minutes }} minutes from {{place-name}}; missing {{unknown}}.",
            mapOf("eta.minutes" to "12", "place-name" to "Work")
        )

        assertEquals("ETA 12 minutes from Work; missing .", rendered)
    }
}
