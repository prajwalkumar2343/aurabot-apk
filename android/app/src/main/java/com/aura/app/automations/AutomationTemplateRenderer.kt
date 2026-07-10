package com.aura.app.automations

class AutomationTemplateRenderer {
    private val tokenPattern = """\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}""".toRegex()

    fun render(template: String, values: Map<String, String>): String =
        tokenPattern.replace(template) { match ->
            values[match.groupValues[1]].orEmpty()
        }.trim()
}
