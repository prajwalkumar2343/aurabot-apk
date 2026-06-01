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

        repository.deleteRecord("builtin.habit_tracker", records.first().id)
        assertTrue(repository.records("builtin.habit_tracker").isEmpty())
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
    override suspend fun deleteRecord(miniAppId: String, recordId: String) {
        if (records[recordId]?.miniAppId == miniAppId) records.remove(recordId)
    }
    override suspend fun insertEvent(entity: MiniAppEventEntity) = Unit
}
