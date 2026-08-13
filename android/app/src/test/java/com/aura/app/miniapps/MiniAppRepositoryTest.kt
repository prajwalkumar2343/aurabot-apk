package com.aura.app.miniapps

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiniAppRepositoryTest {
    @Test
    fun installListOpenRecordQueryAndDeleteFlow() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 1000L })

        repository.install(BuiltInMiniApps.habitTracker)
        val installed = repository.listInstalled()
        assertEquals(listOf("Habit Tracker"), installed.map { it.name })

        val bundle = repository.bundle("builtin.habit_tracker")
        assertEquals("Habit Tracker", bundle?.metadata?.name)
        assertEquals("Habit Tracker", bundle?.widget?.title)

        repository.runAction("builtin.habit_tracker", "check_workout")
        val records = repository.records("builtin.habit_tracker")
        assertEquals(1, records.size)
        assertEquals("Workout", records.first().values["habit"])

        val updated = repository.updateRecord("builtin.habit_tracker", records.first().id, mapOf("habit" to "Workout", "note" to "Done"))
        assertEquals("Done", updated?.values?.get("note"))
        assertEquals(1, repository.records("builtin.habit_tracker", updated?.recordType).size)

        repository.deleteRecord("builtin.habit_tracker", records.first().id)
        assertTrue(repository.records("builtin.habit_tracker").isEmpty())
    }

    @Test
    fun builtInReactSmokeAppInstallsAndSupportsLocalCrud() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 2000L })

        repository.ensureBuiltInsInstalled()
        val installed = repository.listInstalled()
        assertEquals(true, installed.any { it.id == "builtin.react_field_notes" && it.name == "Field Notes" })

        val bundle = repository.bundle("builtin.react_field_notes")
        assertEquals("react", bundle?.runtime)

        val created = repository.createRecord(
            miniAppId = "builtin.react_field_notes",
            recordType = "field_note",
            values = mapOf("title" to "Smoke record", "status" to "Open", "note" to "Created locally")
        )
        assertEquals("Smoke record", repository.records("builtin.react_field_notes", "field_note").first().values["title"])

        val updated = repository.updateRecord(
            miniAppId = "builtin.react_field_notes",
            recordId = created.id,
            values = mapOf("title" to "Smoke record", "status" to "Done", "note" to "Updated locally")
        )
        assertEquals("Done", updated?.values?.get("status"))

        repository.deleteRecord("builtin.react_field_notes", created.id)
        assertTrue(repository.records("builtin.react_field_notes").isEmpty())
    }

    @Test
    fun revisionPreviewAppliesNewVersionPreservesRecordsAndRollsBackBundle() = runTest {
        var now = 3000L
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { now })
        val original = BuiltInMiniApps.habitTracker.copy(
            metadata = BuiltInMiniApps.habitTracker.metadata.copy(builtIn = false)
        )

        repository.install(original)
        val record = repository.createRecord(
            miniAppId = original.id,
            recordType = "habit_checkin",
            values = mapOf("habit" to "Workout")
        )

        now = 4000L
        val revised = original.copy(
            version = original.version + 1,
            dataSchema = original.dataSchema.copy(
                fields = original.dataSchema.fields + MiniAppField("soreness", "number", defaultValue = "0")
            ),
            assistantIntents = original.assistantIntents + MiniAppAssistantIntent(
                name = "log_soreness",
                utterances = listOf("log soreness"),
                actionId = "check_workout"
            )
        )
        repository.applyRevision(
            MiniAppRevisionPreview(
                bundle = revised,
                summary = "Added soreness tracking.",
                migrationPlan = listOf("Existing records remain valid.")
            )
        )

        assertEquals(2, repository.bundle(original.id)?.version)
        assertEquals(record.id, repository.records(original.id).first().id)
        assertEquals("0", repository.records(original.id).first().values["soreness"])
        assertEquals(listOf(2, 1), repository.versions(original.id).map { it.version })
        assertEquals(true, repository.versions(original.id).first().active)

        now = 5000L
        repository.rollback(original.id, 1)

        assertEquals(1, repository.bundle(original.id)?.version)
        assertEquals(record.id, repository.records(original.id).first().id)
        assertEquals(true, repository.versions(original.id).first { it.version == 1 }.active)
    }

    @Test
    fun createRecordNormalizesDefaultRecordTypeAndSchemaDefaults() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 6000L })

        repository.install(BuiltInMiniApps.habitTracker)

        val record = repository.createRecord(
            miniAppId = "builtin.habit_tracker",
            recordType = "record",
            values = mapOf("habit" to "Water")
        )

        assertEquals("habit_checkin", record.recordType)
        assertEquals("true", record.values["done"])
    }

    @Test
    fun createRecordRejectsUnknownRecordTypesAndFields() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 7000L })

        repository.install(BuiltInMiniApps.habitTracker)

        assertValidationFails {
            repository.createRecord(
                miniAppId = "builtin.habit_tracker",
                recordType = "expense",
                values = mapOf("habit" to "Water")
            )
        }
        assertValidationFails {
            repository.createRecord(
                miniAppId = "builtin.habit_tracker",
                recordType = "habit_checkin",
                values = mapOf("habit" to "Water", "surprise" to "nope")
            )
        }
    }

    @Test
    fun updateRecordMergesWithExistingValuesBeforeValidation() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 8000L })

        repository.install(BuiltInMiniApps.habitTracker)
        val record = repository.createRecord(
            miniAppId = "builtin.habit_tracker",
            recordType = "habit_checkin",
            values = mapOf("habit" to "Reading", "done" to "true")
        )

        val updated = repository.updateRecord(
            miniAppId = "builtin.habit_tracker",
            recordId = record.id,
            values = mapOf("note" to "Chapter two")
        )

        assertEquals("Reading", updated?.values?.get("habit"))
        assertEquals("Chapter two", updated?.values?.get("note"))
    }

    @Test
    fun repeatedBuiltInInstallIsIdempotent() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 9_000L })

        repository.ensureBuiltInsInstalled()
        val firstEventCount = dao.eventCount
        val firstVersionCount = dao.versionCount
        repository.ensureBuiltInsInstalled()

        assertEquals(firstEventCount, dao.eventCount)
        assertEquals(firstVersionCount, dao.versionCount)
    }

    @Test
    fun widgetCatalogUsesAggregateStatsWithoutLoadingRecordPayloads() = runTest {
        val now = 1_752_192_000_000L
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { now })
        repository.install(BuiltInMiniApps.habitTracker)
        repository.createRecord("builtin.habit_tracker", "habit_checkin", mapOf("habit" to "Water"))
        dao.recordsQueryCount = 0

        val catalog = repository.widgetCatalog()
        val widget = catalog.widgets.single()

        assertEquals(1L, widget.totalCount)
        assertEquals(1L, widget.todayCount)
        assertEquals(1L, widget.weeklyCount)
        assertEquals(0, dao.recordsQueryCount)
    }

    @Test
    fun widgetCatalogIsolatesCorruptBundlesAndBackfillsLegacyWidgets() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 10_000L })
        val gson = com.google.gson.Gson()
        val legacy = BuiltInMiniApps.habitTracker.copy(widget = null)
        dao.installBundle(
            MiniAppBundleEntity(
                id = legacy.id,
                name = legacy.metadata.name,
                description = legacy.metadata.description,
                category = legacy.metadata.category,
                iconValue = legacy.icon.value,
                iconBackground = legacy.icon.background,
                builtIn = true,
                bundleJson = gson.toJson(legacy),
                installedAt = 1L,
                updatedAt = 1L
            )
        )
        dao.installBundle(
            MiniAppBundleEntity("corrupt", "Corrupt", "", "Test", "C", "#000000", false, "{bad json", 1L, 1L)
        )
        val invalidWidgetBundle = legacy.copy(
            id = "generated.invalid_widget",
            widget = MiniAppWidget(type = "webview", title = "Unsafe", description = "Unsafe widget")
        )
        dao.installBundle(
            MiniAppBundleEntity(
                invalidWidgetBundle.id,
                invalidWidgetBundle.metadata.name,
                invalidWidgetBundle.metadata.description,
                invalidWidgetBundle.metadata.category,
                invalidWidgetBundle.icon.value,
                invalidWidgetBundle.icon.background,
                false,
                gson.toJson(invalidWidgetBundle),
                1L,
                1L
            )
        )

        assertEquals(listOf("corrupt"), repository.backfillLegacyWidgets())
        val catalog = repository.widgetCatalog()

        assertEquals(
            listOf("builtin.habit_tracker", "generated.invalid_widget"),
            catalog.widgets.map { it.bundle.id }.sorted()
        )
        assertEquals(listOf("corrupt"), catalog.invalidMiniAppIds)
        assertTrue(dao.rawBundle(legacy.id)?.contains("\"widget\"") == true)
        assertTrue(dao.rawBundle(invalidWidgetBundle.id)?.contains("\"type\":\"quick_actions\"") == true)
    }

    @Test
    fun revisionRejectsVersionJumpsAndStoredFieldRemoval() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 11_000L })
        val original = BuiltInMiniApps.habitTracker.copy(
            metadata = BuiltInMiniApps.habitTracker.metadata.copy(builtIn = false)
        )
        repository.install(original)

        assertValidationFails {
            repository.applyRevision(
                MiniAppRevisionPreview(original.copy(version = 3), "Jumped version", emptyList())
            )
        }
        assertValidationFails {
            repository.applyRevision(
                MiniAppRevisionPreview(
                    original.copy(
                        version = 2,
                        dataSchema = original.dataSchema.copy(fields = original.dataSchema.fields.drop(1))
                    ),
                    "Removed fields",
                    emptyList()
                )
            )
        }
    }

    @Test
    fun widgetCatalogBatchesLargeInstalledCatalogsAndKeepsCorruptInstallsListable() = runTest {
        val dao = FakeMiniAppDao()
        val repository = MiniAppRepository(dao, clock = { 12_000L })
        val gson = com.google.gson.Gson()
        repeat(401) { index ->
            val bundle = MiniAppValidator.validate(BuiltInMiniApps.habitTracker.copy(id = "generated.scale_$index"))
            dao.installBundle(
                MiniAppBundleEntity(
                    bundle.id,
                    bundle.metadata.name,
                    bundle.metadata.description,
                    bundle.metadata.category,
                    bundle.icon.value,
                    bundle.icon.background,
                    false,
                    gson.toJson(bundle),
                    index.toLong(),
                    index.toLong()
                )
            )
        }
        dao.installBundle(MiniAppBundleEntity("corrupt", "Corrupt", "", "Test", "C", "#000000", false, "{bad", 1L, 1L))

        val catalog = repository.widgetCatalog()
        val installed = repository.listInstalled()

        assertEquals(401, catalog.widgets.size)
        assertTrue(dao.widgetStatsBatchSizes.all { it <= 400 })
        assertEquals(listOf(400, 1), dao.widgetStatsBatchSizes)
        assertTrue(installed.any { it.id == "corrupt" && it.version == 1 })
    }
}

private suspend fun assertValidationFails(block: suspend () -> Unit) {
    try {
        block()
        fail("Expected MiniAppValidationException")
    } catch (_: MiniAppValidationException) {
        // Expected.
    }
}

private class FakeMiniAppDao : MiniAppDao {
    private val bundles = linkedMapOf<String, MiniAppBundleEntity>()
    private val records = linkedMapOf<String, MiniAppRecordEntity>()
    private val versions = linkedMapOf<String, MiniAppVersionEntity>()
    private val events = mutableListOf<MiniAppEventEntity>()
    var recordsQueryCount: Int = 0
    val eventCount: Int get() = events.size
    val versionCount: Int get() = versions.size
    val widgetStatsBatchSizes = mutableListOf<Int>()

    fun rawBundle(id: String): String? = bundles[id]?.bundleJson

    override suspend fun listBundles(): List<MiniAppBundleEntity> = bundles.values.sortedBy { it.name }
    override suspend fun bundle(id: String): MiniAppBundleEntity? = bundles[id]
    override suspend fun installBundle(entity: MiniAppBundleEntity) {
        bundles[entity.id] = entity
    }
    override suspend fun uninstallCustomBundle(id: String) {
        if (bundles[id]?.builtIn == false) bundles.remove(id)
    }
    override suspend fun upsertRecord(entity: MiniAppRecordEntity) {
        records[entity.id] = entity
    }
    override suspend fun records(miniAppId: String): List<MiniAppRecordEntity> =
        records.values.filter { it.miniAppId == miniAppId }.sortedByDescending { it.createdAt }.also { recordsQueryCount += 1 }
    override suspend fun recordsByType(miniAppId: String, recordType: String): List<MiniAppRecordEntity> =
        records(miniAppId).filter { it.recordType == recordType }
    override suspend fun record(miniAppId: String, recordId: String): MiniAppRecordEntity? =
        records[recordId]?.takeIf { it.miniAppId == miniAppId }
    override suspend fun widgetRecordStats(
        miniAppIds: List<String>,
        todayStart: Long,
        weekStart: Long,
        now: Long
    ): List<MiniAppWidgetRecordStats> = miniAppIds.mapNotNull { miniAppId ->
        if (miniAppId == miniAppIds.firstOrNull()) widgetStatsBatchSizes += miniAppIds.size
        val matching = records.values.filter { it.miniAppId == miniAppId && it.createdAt <= now }
        if (matching.isEmpty()) null else MiniAppWidgetRecordStats(
            miniAppId = miniAppId,
            totalCount = matching.size.toLong(),
            todayCount = matching.count { it.createdAt >= todayStart }.toLong(),
            weeklyCount = matching.count { it.createdAt >= weekStart }.toLong()
        )
    }
    override suspend fun recentRecordDays(
        miniAppIds: List<String>,
        cutoff: Long,
        now: Long
    ): List<MiniAppWidgetRecordDay> =
        records.values
            .asSequence()
            .filter { it.miniAppId in miniAppIds && it.createdAt in cutoff..now }
            .map {
                MiniAppWidgetRecordDay(
                    it.miniAppId,
                    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.createdAt))
                )
            }
            .distinctBy { it.miniAppId to it.dayKey }
            .toList()
    override suspend fun deleteRecord(miniAppId: String, recordId: String): Int {
        if (records[recordId]?.miniAppId != miniAppId) return 0
        records.remove(recordId)
        return 1
    }
    override suspend fun insertEvent(entity: MiniAppEventEntity) {
        events += entity
    }
    override suspend fun upsertVersion(entity: MiniAppVersionEntity) {
        versions["${entity.miniAppId}:${entity.version}"] = entity
    }
    override suspend fun versions(miniAppId: String): List<MiniAppVersionEntity> =
        versions.values.filter { it.miniAppId == miniAppId }.sortedByDescending { it.version }
    override suspend fun version(miniAppId: String, version: Int): MiniAppVersionEntity? =
        versions["$miniAppId:$version"]
}
