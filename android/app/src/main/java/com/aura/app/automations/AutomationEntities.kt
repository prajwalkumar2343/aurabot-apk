package com.aura.app.automations

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "automations",
    indices = [Index(value = ["enabled", "updatedAt"])]
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val specJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastTriggeredAt: Long? = null
)

@Entity(
    tableName = "automation_run_logs",
    indices = [Index(value = ["automationId", "createdAt"])]
)
data class AutomationRunLogEntity(
    @PrimaryKey val id: String,
    val automationId: String,
    val eventType: String,
    val status: String,
    val message: String,
    val createdAt: Long
)
