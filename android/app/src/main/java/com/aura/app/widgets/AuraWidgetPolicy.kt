package com.aura.app.widgets

import java.security.MessageDigest

class AuraWidgetValidationException(message: String) : IllegalArgumentException(message)

object AuraWidgetPolicy {
    const val maxTitleChars = 80
    const val maxMessageChars = 280
    const val maxDetails = 6
    const val maxDetailChars = 120
    const val maxActions = 2
    const val maxPayloadEntries = 8
    const val maxPayloadValueChars = 500
    const val maxContentChars = 60_000
    const val maxExpiryMinutes = 7 * 24 * 60

    fun validate(proposal: AuraWidgetProposal): AuraWidgetProposal {
        val title = proposal.title.trim()
        val message = proposal.message.trim()
        if (title.isEmpty()) throw AuraWidgetValidationException("Widget title is required")
        if (title.length > maxTitleChars) throw AuraWidgetValidationException("Widget title is too long")
        if (message.isEmpty()) throw AuraWidgetValidationException("Widget message is required")
        if (message.length > maxMessageChars) throw AuraWidgetValidationException("Widget message is too long")
        if (proposal.details.size > maxDetails) throw AuraWidgetValidationException("Widget has too many detail rows")
        if (proposal.actions.size > maxActions) throw AuraWidgetValidationException("Widget has too many actions")
        if (proposal.details.any { it.trim().isEmpty() || it.trim().length > maxDetailChars }) {
            throw AuraWidgetValidationException("Widget detail is invalid")
        }
        if (proposal.priority !in 0..100) throw AuraWidgetValidationException("Widget priority is invalid")
        if (proposal.expiresInMinutes !in 1..maxExpiryMinutes) {
            throw AuraWidgetValidationException("Widget expiry is invalid")
        }
        if (proposal.dedupeKey.trim().length > 120) {
            throw AuraWidgetValidationException("Widget dedupe key is too long")
        }
        val content = proposal.content?.trim()?.takeIf { it.isNotEmpty() }
        if (content != null && content.length > maxContentChars) {
            throw AuraWidgetValidationException("Widget content is too large")
        }
        if (proposal.presentation == AuraWidgetPresentation.Fullscreen && content == null) {
            throw AuraWidgetValidationException("Full-screen widgets require content")
        }
        if (
            proposal.contentFormat == AuraWidgetContentFormat.Html &&
            (proposal.kind != AuraWidgetKind.Report || proposal.presentation != AuraWidgetPresentation.Fullscreen)
        ) {
            throw AuraWidgetValidationException("HTML is only supported by full-screen report widgets")
        }
        if (proposal.kind == AuraWidgetKind.MeetingNotes && proposal.presentation == AuraWidgetPresentation.Fullscreen) {
            throw AuraWidgetValidationException("Meeting notes widgets must use a compact or expanded presentation")
        }
        val normalizedActions = proposal.actions.map { action ->
            val id = action.id.trim()
            val label = action.label.trim()
            if (!id.matches(Regex("[a-z0-9_-]{1,64}"))) {
                throw AuraWidgetValidationException("Widget action id is invalid")
            }
            if (label.isEmpty() || label.length > 40) {
                throw AuraWidgetValidationException("Widget action label is invalid")
            }
            if (action.payload.size > maxPayloadEntries) {
                throw AuraWidgetValidationException("Widget action payload is too large")
            }
            val normalizedPayload = action.payload
                .mapKeys { it.key.trim() }
                .mapValues { it.value.trim() }
            if (normalizedPayload.size != action.payload.size) {
                throw AuraWidgetValidationException("Widget action payload keys must be unique")
            }
            normalizedPayload.forEach { (key, value) ->
                if (!key.matches(Regex("[a-zA-Z0-9_.-]{1,64}")) || value.length > maxPayloadValueChars) {
                    throw AuraWidgetValidationException("Widget action payload is invalid")
                }
            }
            when (action.type) {
                AuraWidgetActionType.AssistantMessage -> {
                    if (normalizedPayload["message"].isNullOrBlank()) {
                        throw AuraWidgetValidationException("Assistant widget action requires a message")
                    }
                }
                AuraWidgetActionType.OpenApp -> {
                    if (
                        normalizedPayload["package_name"].isNullOrBlank() &&
                        normalizedPayload["app_query"].isNullOrBlank()
                    ) {
                        throw AuraWidgetValidationException("Open-app widget action requires an app")
                    }
                }
                AuraWidgetActionType.Dismiss -> {
                    if (normalizedPayload.isNotEmpty()) {
                        throw AuraWidgetValidationException("Dismiss widget action cannot include a payload")
                    }
                }
            }
            val requiresConfirmation =
                action.requiresConfirmation ||
                    action.type == AuraWidgetActionType.AssistantMessage ||
                    proposal.risk != AuraWidgetRisk.Low
            action.copy(
                id = id,
                label = label,
                payload = normalizedPayload,
                requiresConfirmation = requiresConfirmation
            )
        }
        if (normalizedActions.map { it.id }.distinct().size != normalizedActions.size) {
            throw AuraWidgetValidationException("Widget action ids must be unique")
        }
        if (proposal.kind == AuraWidgetKind.Confirmation && normalizedActions.isEmpty()) {
            throw AuraWidgetValidationException("Confirmation widget requires an action")
        }
        return proposal.copy(
            title = title,
            message = message,
            details = proposal.details.map { it.trim() },
            actions = normalizedActions,
            content = content,
            dedupeKey = proposal.dedupeKey.trim().ifBlank {
                val identity = "${proposal.kind.wireValue}\u0000$title\u0000$message"
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte ->
                        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                    }
                "auto:${digest.take(24)}"
            },
            source = proposal.source.trim().ifBlank { "assistant" }.take(80)
        )
    }
}
