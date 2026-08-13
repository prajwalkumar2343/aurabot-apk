package com.aura.app.widgets

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraWidgetDao {
    @Query(
        """
        SELECT * FROM aura_widgets
        WHERE status IN ('visible', 'awaiting_confirmation', 'executing', 'succeeded', 'failed')
        ORDER BY priority DESC, createdAt DESC
        """
    )
    fun observeVisibleWidgets(): Flow<List<AuraWidgetEntity>>

    @Query("SELECT * FROM aura_widgets WHERE id = :id LIMIT 1")
    suspend fun widget(id: String): AuraWidgetEntity?

    @Query(
        """
        SELECT * FROM aura_widgets
        WHERE status IN ('visible', 'awaiting_confirmation', 'executing', 'succeeded', 'failed')
        """
    )
    suspend fun activeWidgets(): List<AuraWidgetEntity>

    @Query(
        """
        SELECT * FROM aura_widgets
        WHERE dedupeKey = :dedupeKey
          AND status IN ('visible', 'awaiting_confirmation', 'executing', 'succeeded', 'failed')
        LIMIT 1
        """
    )
    suspend fun activeWidgetByDedupeKey(dedupeKey: String): AuraWidgetEntity?

    @Query(
        """
        SELECT * FROM aura_widgets
        WHERE assistantRunId = :assistantRunId
          AND dedupeKey = :dedupeKey
        LIMIT 1
        """
    )
    suspend fun assistantRunWidget(assistantRunId: String, dedupeKey: String): AuraWidgetEntity?

    @Query("SELECT * FROM aura_widgets WHERE assistantRunId = :assistantRunId")
    suspend fun widgetsForAssistantRun(assistantRunId: String): List<AuraWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWidget(entity: AuraWidgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: AuraWidgetEventEntity)

    @Transaction
    suspend fun persistWidget(entity: AuraWidgetEntity, event: AuraWidgetEventEntity) {
        upsertWidget(entity)
        insertEvent(event)
    }

    @Query("SELECT * FROM aura_widgets WHERE status = :status")
    suspend fun widgetsWithStatus(status: String): List<AuraWidgetEntity>

    @Query(
        """
        SELECT * FROM aura_widgets
        WHERE status = 'executing'
          AND updatedAt <= :cutoff
        """
    )
    suspend fun executionsStartedBefore(cutoff: Long): List<AuraWidgetEntity>

    @Query(
        """
        SELECT * FROM aura_widgets
        WHERE expiresAt <= :now
          AND status IN ('visible', 'awaiting_confirmation', 'succeeded', 'failed')
        """
    )
    suspend fun expirableWidgets(now: Long): List<AuraWidgetEntity>

    @Query(
        """
        SELECT id FROM aura_widgets
        WHERE status IN ('dismissed', 'expired')
          AND updatedAt < :cutoff
        """
    )
    suspend fun terminalWidgetIdsBefore(cutoff: Long): List<String>

    @Query("DELETE FROM aura_widget_events WHERE widgetId IN (:widgetIds)")
    suspend fun deleteEvents(widgetIds: List<String>): Int

    @Query("DELETE FROM aura_widgets WHERE id IN (:widgetIds)")
    suspend fun deleteWidgets(widgetIds: List<String>): Int

    @Transaction
    suspend fun deleteWidgetsAndEvents(widgetIds: List<String>) {
        if (widgetIds.isEmpty()) return
        deleteEvents(widgetIds)
        deleteWidgets(widgetIds)
    }

    @Query("SELECT * FROM hosted_android_widgets ORDER BY page, cellY, cellX")
    fun observeHostedWidgets(): Flow<List<HostedAndroidWidgetEntity>>

    @Query("SELECT appWidgetId FROM hosted_android_widgets")
    suspend fun hostedWidgetIds(): List<Int>

    @Query("SELECT * FROM hosted_android_widgets WHERE appWidgetId = :appWidgetId LIMIT 1")
    suspend fun hostedWidget(appWidgetId: Int): HostedAndroidWidgetEntity?

    @Query("SELECT COALESCE(MAX(cellY), -1) FROM hosted_android_widgets WHERE page = :page")
    suspend fun maxHostedCellY(page: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHostedWidget(entity: HostedAndroidWidgetEntity)

    @Query(
        """
        UPDATE hosted_android_widgets
        SET spanX = :spanX, spanY = :spanY, updatedAt = :updatedAt
        WHERE appWidgetId = :appWidgetId
        """
    )
    suspend fun resizeHostedWidget(appWidgetId: Int, spanX: Int, spanY: Int, updatedAt: Long): Int

    @Query("DELETE FROM hosted_android_widgets WHERE appWidgetId = :appWidgetId")
    suspend fun deleteHostedWidget(appWidgetId: Int): Int
}
