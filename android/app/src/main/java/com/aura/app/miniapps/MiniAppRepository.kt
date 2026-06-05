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
                installedAt = now,
                updatedAt = now
            )
        )
        dao.insertEvent(MiniAppEventEntity(UUID.randomUUID().toString(), valid.id, "installed", "{}", now))
        return valid.install(now)
    }

    suspend fun listInstalled(): List<MiniAppInstall> =
        dao.listBundles().map { it.install() }

    suspend fun bundle(id: String): MiniAppBundle? =
        dao.bundle(id)?.bundleJson?.let { gson.fromJson(it, MiniAppBundle::class.java) }

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
        val now = clock()
        val record = MiniAppRecord(
            id = UUID.randomUUID().toString(),
            miniAppId = miniAppId,
            recordType = recordType,
            values = values,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertRecord(
            MiniAppRecordEntity(
                id = record.id,
                miniAppId = miniAppId,
                recordType = recordType,
                valuesJson = gson.toJson(values),
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertEvent(MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_created", gson.toJson(values), now))
        return record
    }

    suspend fun records(miniAppId: String): List<MiniAppRecord> =
        dao.records(miniAppId).map { it.record() }

    suspend fun records(miniAppId: String, recordType: String?): List<MiniAppRecord> =
        if (recordType.isNullOrBlank()) records(miniAppId) else dao.recordsByType(miniAppId, recordType).map { it.record() }

    suspend fun updateRecord(miniAppId: String, recordId: String, values: Map<String, String>): MiniAppRecord? {
        val existing = dao.record(miniAppId, recordId) ?: return null
        val now = clock()
        dao.upsertRecord(existing.copy(valuesJson = gson.toJson(values), updatedAt = now))
        dao.insertEvent(MiniAppEventEntity(UUID.randomUUID().toString(), miniAppId, "record_updated", gson.toJson(values), now))
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
        installedAt = installedAt
    )

    private fun MiniAppBundle.install(now: Long) = MiniAppInstall(
        id = id,
        name = metadata.name,
        description = metadata.description,
        category = metadata.category,
        icon = icon,
        builtIn = metadata.builtIn,
        installedAt = now
    )

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
