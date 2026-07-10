package com.aura.app.miniapps

object MiniAppWidgetPolicy {
    val supportedTypes = setOf("summary", "counter", "progress", "quick_actions")
    val supportedMetrics = setOf("today_count", "weekly_count", "total_count", "streak")
    const val maxActions = 3

    fun withDefault(bundle: MiniAppBundle): MiniAppBundle {
        val fallback = defaultFor(bundle)
        val widget = bundle.widget ?: return bundle.copy(widget = fallback)
        return bundle.copy(
            widget = widget.copy(
                title = widget.title.trim().ifBlank { fallback.title },
                description = widget.description.trim().ifBlank { fallback.description }
            )
        )
    }

    fun defaultFor(bundle: MiniAppBundle): MiniAppWidget {
        val actionIds = bundle.actions
            .asSequence()
            .filter { it.type == "create_record" }
            .map { it.id }
            .take(maxActions)
            .toList()
        val metric = bundle.screens
            .asSequence()
            .flatMap { it.components.asSequence() }
            .mapNotNull { it.metric }
            .firstOrNull { it in supportedMetrics }
            ?: "total_count"
        return MiniAppWidget(
            type = if (actionIds.isEmpty()) "summary" else "quick_actions",
            title = bundle.metadata.name.take(60),
            description = bundle.metadata.description.trim().ifBlank { "Open ${bundle.metadata.name}" }.take(160),
            metric = metric,
            goal = null,
            actionIds = actionIds
        )
    }
}
