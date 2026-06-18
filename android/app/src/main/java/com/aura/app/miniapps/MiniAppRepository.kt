package com.aura.app.miniapps

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

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
        dao.installBundle(
            MiniAppBundleEntity(
                id = valid.id,
                name = valid.metadata.name,
                description = valid.metadata.description,
                category = valid.metadata.category,
                iconValue = valid.icon.value,
                iconBackground = valid.icon.background,
                builtIn = valid.metadata.builtIn,
                bundleJson = gson.toJson(valid),
                installedAt = existing?.installedAt ?: now,
                updatedAt = now
            )
        )
        storeVersion(valid, "Installed ${valid.metadata.name}.", listOf("Initial install."), now)
        dao.insertEvent(MiniAppEventEntity(UUID.randomUUID().toString(), valid.id, "installed", "{}", now))
        return valid.install(now)
    }

    suspend fun applyRevision(preview: MiniAppRevisionPreview): MiniAppInstall {
        val valid = MiniAppValidator.validate(preview.bundle)
        val now = clock()
        val existing = dao.bundle(valid.id)
        existing?.bundleJson?.let { json ->
            val current = gson.fromJson(json, MiniAppBundle::class.java)
            storeVersion(
                current,
                "Snapshot before v${valid.version}.",
                listOf("Rollback point before: ${preview.summary}"),
                existing.updatedAt
            )
            migrateRecords(current, valid)
        }
        dao.installBundle(
            MiniAppBundleEntity(
                id = valid.id,
                name = valid.metadata.name,
                description = valid.metadata.description,
                category = valid.metadata.category,
                iconValue = valid.icon.value,
                iconBackground = valid.icon.background,
                builtIn = false,
                bundleJson = gson.toJson(valid),
                installedAt = existing?.installedAt ?: now,
                updatedAt = now
            )
        )
        storeVersion(valid, preview.summary, preview.migrationPlan, now)
        dao.insertEvent(
            MiniAppEventEntity(
                UUID.randomUUID().toString(),
                valid.id,
                "revision_applied",
                gson.toJson(mapOf("version" to valid.version.toString(), "summary" to preview.summary)),
                now
            )
        )
        return valid.install(existing?.installedAt ?: now)
    }

    suspend fun listInstalled(): List<MiniAppInstall> =
        dao.listBundles().map { it.install() }

    suspend fun bundle(id: String): MiniAppBundle? =
        dao.bundle(id)?.bundleJson?.let { gson.fromJson(it, MiniAppBundle::class.java) }

    suspend fun versions(miniAppId: String): List<MiniAppVersion> {
        val activeVersion = bundle(miniAppId)?.version
        return dao.versions(miniAppId).map { it.version(activeVersion) }
    }

    suspend fun rollback(miniAppId: String, version: Int): MiniAppInstall? {
        val target = dao.version(miniAppId, version) ?: return null
        val bundle = MiniAppValidator.validate(gson.fromJson(target.bundleJson, MiniAppBundle::class.java))
        val existing = dao.bundle(miniAppId)
        val now = clock()
        dao.installBundle(
            MiniAppBundleEntity(
                id = bundle.id,
                name = bundle.metadata.name,
                description = bundle.metadata.description,
                category = bundle.metadata.category,
                iconValue = bundle.icon.value,
                iconBackground = bundle.icon.background,
                builtIn = bundle.metadata.builtIn,
                bundleJson = gson.toJson(bundle),
                installedAt = existing?.installedAt ?: now,
                updatedAt = now
            )
        )
        dao.insertEvent(
            MiniAppEventEntity(
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
        dao.upsertRecord(
            MiniAppRecordEntity(
                id = record.id,
                miniAppId = miniAppId,
                recordType = normalizedRecordType,
                valuesJson = gson.toJson(normalizedValues),
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertEvent(
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
        dao.upsertRecord(existing.copy(recordType = bundle.dataSchema.recordType, valuesJson = gson.toJson(normalizedValues), updatedAt = now))
        dao.insertEvent(
            MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_updated", gson.toJson(normalizedValues), now)
        )
        return dao.record(miniAppId, recordId)?.record()
    }

    suspend fun deleteRecord(miniAppId: String, recordId: String) {
        dao.deleteRecord(miniAppId, recordId)
        dao.insertEvent(MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_deleted", """{"id":"$recordId"}""", clock()))
    }

    private fun MiniAppBundleEntity.install() = MiniAppInstall(
        id = id,
        name = name,
        description = description,
        category = category,
        icon = MiniAppIcon(value = iconValue, background = iconBackground),
        builtIn = builtIn,
        installedAt = installedAt,
        version = gson.fromJson(bundleJson, MiniAppBundle::class.java)?.version ?: 1
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

    private suspend fun storeVersion(bundle: MiniAppBundle, summary: String, migrationPlan: List<String>, createdAt: Long) {
        dao.upsertVersion(
            MiniAppVersionEntity(
                miniAppId = bundle.id,
                version = bundle.version,
                name = bundle.metadata.name,
                summary = summary,
                migrationPlanJson = gson.toJson(migrationPlan),
                bundleJson = gson.toJson(bundle),
                createdAt = createdAt
            )
        )
    }

    private suspend fun migrateRecords(previous: MiniAppBundle, next: MiniAppBundle) {
        val previousFieldNames = previous.dataSchema.fields.map { it.name }.toSet()
        val newDefaults = next.dataSchema.fields
            .filter { it.name !in previousFieldNames && it.defaultValue != null }
            .associate { it.name to it.defaultValue.orEmpty() }
        val oldPrimaryType = previous.dataSchema.recordType
        val newPrimaryType = next.dataSchema.recordType
        if (newDefaults.isEmpty() && oldPrimaryType == newPrimaryType) return
        records(next.id).forEach { record ->
            val migratedValues = newDefaults.entries.fold(record.values) { values, (key, defaultValue) ->
                if (key in values) values else values + (key to defaultValue)
            }
            val migratedType = if (record.recordType == oldPrimaryType) newPrimaryType else record.recordType
            if (migratedValues != record.values || migratedType != record.recordType) {
                val now = clock()
                dao.upsertRecord(
                    MiniAppRecordEntity(
                        id = record.id,
                        miniAppId = record.miniAppId,
                        recordType = migratedType,
                        valuesJson = gson.toJson(migratedValues),
                        createdAt = record.createdAt,
                        updatedAt = now
                    )
                )
            }
        }
    }

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
}
