package com.aura.app.widgets

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "aura_widgets",
    indices = [
        Index(value = ["status", "priority", "createdAt"]),
        Index(value = ["dedupeKey", "status"])
    ]
)
data class AuraWidgetEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val message: String,
    val detailsJson: String,
    val actionsJson: String,
    @ColumnInfo(defaultValue = "'compact'") val presentation: String = "compact",
    @ColumnInfo(defaultValue = "'plain_text'") val contentFormat: String = "plain_text",
    val content: String? = null,
    val status: String,
    val risk: String,
    val priority: Int,
    val source: String,
    val dedupeKey: String,
    val pendingActionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
    val lastError: String?,
    val assistantRunId: String? = null
)

@Entity(
    tableName = "aura_widget_events",
    indices = [Index(value = ["widgetId", "createdAt"])]
)
data class AuraWidgetEventEntity(
    @PrimaryKey val id: String,
    val widgetId: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Long
)

@Entity(tableName = "hosted_android_widgets")
data class HostedAndroidWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val createdAt: Long,
    val updatedAt: Long
)
