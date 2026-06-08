package com.aura.app.miniapps

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mini_app_bundles")
data class MiniAppBundleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val iconValue: String,
    val iconBackground: String,
    val builtIn: Boolean,
    val bundleJson: String,
    val installedAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "mini_app_records",
    indices = [Index(value = ["miniAppId", "recordType", "createdAt"])]
)
data class MiniAppRecordEntity(
    @PrimaryKey val id: String,
    val miniAppId: String,
    val recordType: String,
    val valuesJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "mini_app_events",
    indices = [Index(value = ["miniAppId", "createdAt"])]
)
data class MiniAppEventEntity(
    @PrimaryKey val id: String,
    val miniAppId: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Long
)

@Entity(tableName = "mini_app_settings")
data class MiniAppSettingEntity(
    @PrimaryKey val key: String,
    val miniAppId: String,
    val value: String,
    val updatedAt: Long
)

@Entity(
    tableName = "mini_app_versions",
    primaryKeys = ["miniAppId", "version"],
    indices = [Index(value = ["miniAppId", "createdAt"])]
)
data class MiniAppVersionEntity(
    val miniAppId: String,
    val version: Int,
    val name: String,
    val summary: String,
    val migrationPlanJson: String,
    val bundleJson: String,
    val createdAt: Long
)
