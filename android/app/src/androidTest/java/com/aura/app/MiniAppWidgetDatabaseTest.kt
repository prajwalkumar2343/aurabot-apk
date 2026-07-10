package com.aura.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aura.app.miniapps.MiniAppDatabase
import com.aura.app.miniapps.MiniAppEventEntity
import com.aura.app.miniapps.MiniAppRecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MiniAppWidgetDatabaseTest {
    private lateinit var database: MiniAppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MiniAppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun widgetStatsAreBoundedByTimeAndDoNotReadRecordPayloads() = runBlocking {
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dao = database.miniAppDao()
        listOf(
            "today" to now,
            "recent" to now - TimeUnit.DAYS.toMillis(2),
            "old" to now - TimeUnit.DAYS.toMillis(8),
            "future" to now + TimeUnit.DAYS.toMillis(1)
        ).forEach { (id, timestamp) ->
            dao.upsertRecord(
                MiniAppRecordEntity(id, "generated.stats", "entry", "{\"large\":\"payload\"}", timestamp, timestamp)
            )
        }

        val stats = dao.widgetRecordStats(
            listOf("generated.stats"),
            todayStart,
            now - TimeUnit.DAYS.toMillis(7),
            now
        ).single()
        val dayKeys = dao.recentRecordDays(
            listOf("generated.stats"),
            now - TimeUnit.DAYS.toMillis(367),
            now
        )

        assertEquals(3L, stats.totalCount)
        assertEquals(1L, stats.todayCount)
        assertEquals(2L, stats.weeklyCount)
        assertEquals(3, dayKeys.size)
    }

    @Test
    fun recordAndAuditEventRollbackTogetherWhenEventPersistenceFails() = runBlocking {
        val dao = database.miniAppDao()
        val now = System.currentTimeMillis()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_widget_test_events
            BEFORE INSERT ON mini_app_events
            BEGIN
                SELECT RAISE(ABORT, 'forced event failure');
            END
            """.trimIndent()
        )

        try {
            dao.persistRecordMutation(
                MiniAppRecordEntity("atomic", "generated.atomic", "entry", "{}", now, now),
                MiniAppEventEntity("event", "generated.atomic", "record_created", "{}", now)
            )
            fail("Expected the forced event failure")
        } catch (_: Exception) {
            // The transaction must roll the record back when the audit event cannot be stored.
        }

        assertNull(dao.record("generated.atomic", "atomic"))
    }
}
