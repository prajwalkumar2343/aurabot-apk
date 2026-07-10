package com.aura.app.miniapps

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MiniAppRepository(
    private val dao: MiniAppDao,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun ensureBuiltInsInstalled() {
        BuiltInMiniApps.all.forEach { install(it) }
    }

    suspend fun install(bundle: MiniAppBundle): MiniAppInstall {
        val valid = MiniAppValidator.validate(bundle)
        val now = clock()
        val existing = dao.bundle(valid.id)
        val existingBundle = existing?.let { entity ->
            try {
                MiniAppValidator.validate(gson.fromJson(entity.bundleJson, MiniAppBundle::class.java))
            } catch (_: Exception) {
                null
            }
        }
        if (existing != null && existingBundle == valid) {
            val canonical = valid.entity(existing.installedAt, existing.updatedAt)
            if (existing != canonical) {
                dao.installBundle(canonical)
            }
            return valid.install(existing.installedAt)
        }
        val installedAt = existing?.installedAt ?: now
        dao.persistInstall(
            bundle = valid.entity(installedAt, now),
            version = valid.versionEntity("Installed ${valid.metadata.name}.", listOf("Initial install."), now),
            event = MiniAppEventEntity(
                UUID.randomUUID().toString(),
                valid.id,
                if (existing == null) "installed" else "reinstalled",
                "{}",
                now
            )
        )
        return valid.install(installedAt)
    }

    suspend fun applyRevision(preview: MiniAppRevisionPreview): MiniAppInstall {
        val valid = MiniAppValidator.validate(preview.bundle)
        val now = clock()
        val existing = dao.bundle(valid.id)
        val current = existing?.bundleJson?.let { json ->
            MiniAppValidator.validate(gson.fromJson(json, MiniAppBundle::class.java))
        } ?: throw MiniAppValidationException("Unknown mini app: ${valid.id}")
        validateRevisionCompatibility(current, valid)
        val versions = buildList {
            add(
                current.versionEntity(
                    "Snapshot before v${valid.version}.",
                    listOf("Rollback point before: ${preview.summary}"),
                    existing.updatedAt
                )
            )
            add(valid.versionEntity(preview.summary, preview.migrationPlan, now))
        }.distinctBy { it.version }
        dao.persistRevision(
            migratedRecords = migratedRecords(current, valid, now),
            bundle = valid.entity(existing.installedAt, now, builtIn = false),
            versions = versions,
            event = MiniAppEventEntity(
                UUID.randomUUID().toString(),
                valid.id,
                "revision_applied",
                gson.toJson(mapOf("version" to valid.version.toString(), "summary" to preview.summary)),
                now
            )
        )
        return valid.install(existing.installedAt)
    }

    suspend fun listInstalled(): List<MiniAppInstall> =
        dao.listBundles().map { it.install() }

    suspend fun bundle(id: String): MiniAppBundle? =
        dao.bundle(id)?.bundleJson?.let { MiniAppValidator.validate(gson.fromJson(it, MiniAppBundle::class.java)) }

    suspend fun backfillLegacyWidgets(): List<String> {
        val invalidIds = mutableListOf<String>()
        dao.listBundles().forEach { entity ->
            try {
                val decoded = gson.fromJson(entity.bundleJson, MiniAppBundle::class.java)
                val normalized = try {
                    MiniAppValidator.validate(decoded)
                } catch (_: MiniAppValidationException) {
                    MiniAppValidator.validate(decoded.copy(widget = null))
                }
                if (decoded.widget != normalized.widget) {
                    dao.installBundle(entity.copy(bundleJson = gson.toJson(normalized)))
                }
            } catch (_: Exception) {
                invalidIds += entity.id
            }
        }
        return invalidIds
    }

    suspend fun widgetCatalog(): MiniAppWidgetCatalog {
        val validBundles = mutableListOf<MiniAppBundle>()
        val invalidIds = mutableListOf<String>()
        dao.listBundles().forEach { entity ->
            try {
                validBundles += MiniAppValidator.validate(gson.fromJson(entity.bundleJson, MiniAppBundle::class.java))
            } catch (_: Exception) {
                invalidIds += entity.id
            }
        }
        return MiniAppWidgetCatalog(
            widgets = widgetSnapshots(validBundles),
            invalidMiniAppIds = invalidIds
        )
    }

    suspend fun widgetSnapshot(miniAppId: String): MiniAppWidgetSnapshot? {
        val bundle = bundle(miniAppId) ?: return null
        return widgetSnapshots(listOf(bundle)).first()
    }

    suspend fun versions(miniAppId: String): List<MiniAppVersion> {
        val activeVersion = bundle(miniAppId)?.version
        return dao.versions(miniAppId).map { it.version(activeVersion) }
    }

    suspend fun rollback(miniAppId: String, version: Int): MiniAppInstall? {
        val target = dao.version(miniAppId, version) ?: return null
        val bundle = MiniAppValidator.validate(gson.fromJson(target.bundleJson, MiniAppBundle::class.java))
        val existing = dao.bundle(miniAppId)
        val now = clock()
        dao.persistRollback(
            bundle = bundle.entity(existing?.installedAt ?: now, now),
            event = MiniAppEventEntity(
                UUID.randomUUID().toString(),
                miniAppId,
                "revision_rolled_back",
                gson.toJson(mapOf("version" to version.toString())),
                now
            )
        )
        return bundle.install(existing?.installedAt ?: now)
    }

    suspend fun runAction(miniAppId: String, actionId: String): MiniAppRecord? {
        val bundle = bundle(miniAppId) ?: return null
        val action = bundle.actions.firstOrNull { it.id == actionId } ?: return null
        return when (action.type) {
            "create_record" -> createRecord(miniAppId, action.recordType, action.values)
            else -> null
        }
    }

    suspend fun createRecord(
        miniAppId: String,
        recordType: String,
        values: Map<String, String>
    ): MiniAppRecord {
        val bundle = bundle(miniAppId) ?: throw MiniAppValidationException("Unknown mini app: $miniAppId")
        val normalizedRecordType = normalizeRecordType(bundle, recordType)
        val normalizedValues = normalizeRecordValues(bundle, values)
        val now = clock()
        val record = MiniAppRecord(
            id = UUID.randomUUID().toString(),
            miniAppId = miniAppId,
            recordType = normalizedRecordType,
            values = normalizedValues,
            createdAt = now,
            updatedAt = now
        )
        val entity = MiniAppRecordEntity(
                id = record.id,
                miniAppId = miniAppId,
                recordType = normalizedRecordType,
                valuesJson = gson.toJson(normalizedValues),
                createdAt = now,
                updatedAt = now
        )
        dao.persistRecordMutation(
            entity,
            MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_created", gson.toJson(normalizedValues), now)
        )
        return record
    }

    suspend fun records(miniAppId: String): List<MiniAppRecord> =
        dao.records(miniAppId).map { it.record() }

    suspend fun records(miniAppId: String, recordType: String?): List<MiniAppRecord> =
        if (recordType.isNullOrBlank()) records(miniAppId) else dao.recordsByType(miniAppId, recordType).map { it.record() }

    suspend fun updateRecord(miniAppId: String, recordId: String, values: Map<String, String>): MiniAppRecord? {
        val existing = dao.record(miniAppId, recordId) ?: return null
        val bundle = bundle(miniAppId) ?: return null
        normalizeRecordType(bundle, existing.recordType)
        val normalizedValues = normalizeRecordValues(bundle, existing.record().values + values)
        val now = clock()
        dao.persistRecordMutation(
            existing.copy(recordType = bundle.dataSchema.recordType, valuesJson = gson.toJson(normalizedValues), updatedAt = now),
            MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_updated", gson.toJson(normalizedValues), now)
        )
        return dao.record(miniAppId, recordId)?.record()
    }

    suspend fun deleteRecord(miniAppId: String, recordId: String): Boolean =
        dao.deleteRecordWithEvent(
            miniAppId,
            recordId,
            MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_deleted", """{"id":"$recordId"}""", clock())
        )

    private fun MiniAppBundleEntity.install() = MiniAppInstall(
        id = id,
        name = name,
        description = description,
        category = category,
        icon = MiniAppIcon(value = iconValue, background = iconBackground),
        builtIn = builtIn,
        installedAt = installedAt,
        version = try {
            gson.fromJson(bundleJson, MiniAppBundle::class.java)?.version ?: 1
        } catch (_: Exception) {
            1
        }
    )

    private fun MiniAppBundle.install(now: Long) = MiniAppInstall(
        id = id,
        name = metadata.name,
        description = metadata.description,
        category = metadata.category,
        icon = icon,
        builtIn = metadata.builtIn,
        installedAt = now,
        version = version
    )

    private suspend fun migratedRecords(previous: MiniAppBundle, next: MiniAppBundle, now: Long): List<MiniAppRecordEntity> {
        val previousFieldNames = previous.dataSchema.fields.map { it.name }.toSet()
        val newDefaults = next.dataSchema.fields
            .filter { it.name !in previousFieldNames && it.defaultValue != null }
            .associate { it.name to it.defaultValue.orEmpty() }
        val oldPrimaryType = previous.dataSchema.recordType
        val newPrimaryType = next.dataSchema.recordType
        if (newDefaults.isEmpty() && oldPrimaryType == newPrimaryType) return emptyList()
        return records(next.id).mapNotNull { record ->
            val migratedValues = newDefaults.entries.fold(record.values) { values, (key, defaultValue) ->
                if (key in values) values else values + (key to defaultValue)
            }
            val migratedType = if (record.recordType == oldPrimaryType) newPrimaryType else record.recordType
            if (migratedValues != record.values || migratedType != record.recordType) {
                MiniAppRecordEntity(
                    id = record.id,
                    miniAppId = record.miniAppId,
                    recordType = migratedType,
                    valuesJson = gson.toJson(migratedValues),
                    createdAt = record.createdAt,
                    updatedAt = now
                )
            } else null
        }
    }

    private fun validateRevisionCompatibility(previous: MiniAppBundle, next: MiniAppBundle) {
        if (next.version != previous.version + 1) {
            throw MiniAppValidationException("Mini app revision must increment version by exactly 1")
        }
        val nextFields = next.dataSchema.fields.associateBy { it.name }
        previous.dataSchema.fields.forEach { previousField ->
            val nextField = nextFields[previousField.name]
                ?: throw MiniAppValidationException("Mini app revision cannot remove field: ${previousField.name}")
            if (nextField.type != previousField.type) {
                throw MiniAppValidationException("Mini app revision cannot change field type: ${previousField.name}")
            }
        }
    }

    private suspend fun widgetSnapshots(bundles: List<MiniAppBundle>): List<MiniAppWidgetSnapshot> {
        if (bundles.isEmpty()) return emptyList()
        val now = clock()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = now - TimeUnit.DAYS.toMillis(7)
        val stats = bundles
            .map { it.id }
            .chunked(MAX_WIDGET_STATS_BATCH_SIZE)
            .flatMap { ids -> dao.widgetRecordStats(ids, todayStart, weekStart, now) }
            .associateBy { it.miniAppId }
        val streakIds = bundles.filter { it.widget?.metric == "streak" }.map { it.id }
        val streakDays = streakIds
            .chunked(MAX_WIDGET_STATS_BATCH_SIZE)
            .flatMap { ids ->
                dao.recentRecordDays(ids, now - TimeUnit.DAYS.toMillis(STREAK_LOOKBACK_DAYS), now)
            }
            .filter { it.dayKey.isNotBlank() }
            .groupBy({ it.miniAppId }, { it.dayKey })
        return bundles.map { bundle ->
            val recordStats = stats[bundle.id]
            val streak = if (bundle.widget?.metric == "streak") {
                calculateWidgetStreak(streakDays[bundle.id].orEmpty(), now)
            } else 0
            MiniAppWidgetSnapshot(
                bundle = bundle,
                totalCount = recordStats?.totalCount ?: 0,
                todayCount = recordStats?.todayCount ?: 0,
                weeklyCount = recordStats?.weeklyCount ?: 0,
                streak = streak
            )
        }
    }

    private fun calculateWidgetStreak(dayKeys: List<String>, now: Long): Int {
        if (dayKeys.isEmpty()) return 0
        val availableDays = dayKeys.toSet()
        val format = SimpleDateFormat("yyyyMMdd", Locale.US)
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        var streak = 0
        while (streak < MAX_STREAK_DAY_KEYS && format.format(calendar.time) in availableDays) {
            streak += 1
            calendar.add(Calendar.DATE, -1)
        }
        return streak
    }

    private fun MiniAppBundle.entity(
        installedAt: Long,
        updatedAt: Long,
        builtIn: Boolean = metadata.builtIn
    ) = MiniAppBundleEntity(
        id = id,
        name = metadata.name,
        description = metadata.description,
        category = metadata.category,
        iconValue = icon.value,
        iconBackground = icon.background,
        builtIn = builtIn,
        bundleJson = gson.toJson(this),
        installedAt = installedAt,
        updatedAt = updatedAt
    )

    private fun MiniAppBundle.versionEntity(
        summary: String,
        migrationPlan: List<String>,
        createdAt: Long
    ) = MiniAppVersionEntity(
        miniAppId = id,
        version = version,
        name = metadata.name,
        summary = summary,
        migrationPlanJson = gson.toJson(migrationPlan),
        bundleJson = gson.toJson(this),
        createdAt = createdAt
    )

    private fun normalizeRecordType(bundle: MiniAppBundle, recordType: String): String {
        val schemaRecordType = bundle.dataSchema.recordType
        val requested = recordType.trim()
        return when {
            requested.isEmpty() || requested == "record" -> schemaRecordType
            requested == schemaRecordType -> schemaRecordType
            else -> throw MiniAppValidationException("Unsupported record type: $recordType")
        }
    }

    private fun normalizeRecordValues(bundle: MiniAppBundle, values: Map<String, String>): Map<String, String> {
        val fields = bundle.dataSchema.fields
        val fieldNames = fields.map { it.name }.toSet()
        val unknownFields = values.keys.filter { it !in fieldNames }
        if (unknownFields.isNotEmpty()) {
            throw MiniAppValidationException("Unknown record field: ${unknownFields.first()}")
        }
        return fields.mapNotNull { field ->
            val value = values[field.name]
                ?: field.defaultValue
                ?: if (field.required) throw MiniAppValidationException("${field.name} is required") else return@mapNotNull null
            if (field.required && value.isBlank()) throw MiniAppValidationException("${field.name} is required")
            field.name to value
        }.toMap()
    }

    private fun MiniAppVersionEntity.version(activeVersion: Int?): MiniAppVersion {
        val listType = object : TypeToken<List<String>>() {}.type
        return MiniAppVersion(
            miniAppId = miniAppId,
            version = this.version,
            name = name,
            summary = summary,
            migrationPlan = gson.fromJson(migrationPlanJson, listType) ?: emptyList(),
            createdAt = createdAt,
            active = activeVersion == version
        )
    }

    private fun MiniAppRecordEntity.record(): MiniAppRecord {
        val type = object : TypeToken<Map<String, String>>() {}.type
        return MiniAppRecord(
            id = id,
            miniAppId = miniAppId,
            recordType = recordType,
            values = gson.fromJson(valuesJson, type) ?: emptyMap(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private companion object {
        const val MAX_STREAK_DAY_KEYS = 366
        const val STREAK_LOOKBACK_DAYS = 367L
        const val MAX_WIDGET_STATS_BATCH_SIZE = 400
    }
}
