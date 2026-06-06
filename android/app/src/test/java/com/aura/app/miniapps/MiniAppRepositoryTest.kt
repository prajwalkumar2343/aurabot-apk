package com.aura.app.miniapps

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

private class FakeMiniAppDao : MiniAppDao {
    private val bundles = linkedMapOf<String, MiniAppBundleEntity>()
    private val records = linkedMapOf<String, MiniAppRecordEntity>()

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
        records.values.filter { it.miniAppId == miniAppId }.sortedByDescending { it.createdAt }
    override suspend fun recordsByType(miniAppId: String, recordType: String): List<MiniAppRecordEntity> =
        records(miniAppId).filter { it.recordType == recordType }
    override suspend fun record(miniAppId: String, recordId: String): MiniAppRecordEntity? =
        records[recordId]?.takeIf { it.miniAppId == miniAppId }
    override suspend fun deleteRecord(miniAppId: String, recordId: String) {
        if (records[recordId]?.miniAppId == miniAppId) records.remove(recordId)
    }
    override suspend fun insertEvent(entity: MiniAppEventEntity) = Unit
}
