package com.aura.app.miniapps

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MiniAppDao {
    @Query("SELECT * FROM mini_app_bundles ORDER BY builtIn DESC, name COLLATE NOCASE ASC")
    suspend fun listBundles(): List<MiniAppBundleEntity>

    @Query("SELECT * FROM mini_app_bundles WHERE id = :id LIMIT 1")
    suspend fun bundle(id: String): MiniAppBundleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun installBundle(entity: MiniAppBundleEntity)

    @Query("DELETE FROM mini_app_bundles WHERE id = :id AND builtIn = 0")
    suspend fun uninstallCustomBundle(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(entity: MiniAppRecordEntity)

    @Query("SELECT * FROM mini_app_records WHERE miniAppId = :miniAppId ORDER BY createdAt DESC")
    suspend fun records(miniAppId: String): List<MiniAppRecordEntity>

    @Query("SELECT * FROM mini_app_records WHERE miniAppId = :miniAppId AND recordType = :recordType ORDER BY createdAt DESC")
    suspend fun recordsByType(miniAppId: String, recordType: String): List<MiniAppRecordEntity>

    @Query("SELECT * FROM mini_app_records WHERE id = :recordId AND miniAppId = :miniAppId LIMIT 1")
    suspend fun record(miniAppId: String, recordId: String): MiniAppRecordEntity?

    @Query(
        """
        SELECT miniAppId,
               SUM(CASE WHEN createdAt <= :now THEN 1 ELSE 0 END) AS totalCount,
               SUM(CASE WHEN createdAt BETWEEN :todayStart AND :now THEN 1 ELSE 0 END) AS todayCount,
               SUM(CASE WHEN createdAt BETWEEN :weekStart AND :now THEN 1 ELSE 0 END) AS weeklyCount
        FROM mini_app_records
        WHERE miniAppId IN (:miniAppIds)
        GROUP BY miniAppId
        """
    )
    suspend fun widgetRecordStats(
        miniAppIds: List<String>,
        todayStart: Long,
        weekStart: Long,
        now: Long
    ): List<MiniAppWidgetRecordStats>

    @Query(
        """
        SELECT miniAppId,
               COALESCE(strftime('%Y%m%d', createdAt / 1000, 'unixepoch', 'localtime'), '') AS dayKey
        FROM mini_app_records
        WHERE miniAppId IN (:miniAppIds) AND createdAt BETWEEN :cutoff AND :now
        GROUP BY miniAppId, dayKey
        """
    )
    suspend fun recentRecordDays(
        miniAppIds: List<String>,
        cutoff: Long,
        now: Long
    ): List<MiniAppWidgetRecordDay>

    @Query("DELETE FROM mini_app_records WHERE id = :recordId AND miniAppId = :miniAppId")
    suspend fun deleteRecord(miniAppId: String, recordId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: MiniAppEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(entity: MiniAppVersionEntity)

    @Query("SELECT * FROM mini_app_versions WHERE miniAppId = :miniAppId ORDER BY version DESC")
    suspend fun versions(miniAppId: String): List<MiniAppVersionEntity>

    @Query("SELECT * FROM mini_app_versions WHERE miniAppId = :miniAppId AND version = :version LIMIT 1")
    suspend fun version(miniAppId: String, version: Int): MiniAppVersionEntity?

    @Transaction
    suspend fun persistInstall(
        bundle: MiniAppBundleEntity,
        version: MiniAppVersionEntity,
        event: MiniAppEventEntity
    ) {
        installBundle(bundle)
        upsertVersion(version)
        insertEvent(event)
    }

    @Transaction
    suspend fun persistRevision(
        migratedRecords: List<MiniAppRecordEntity>,
        bundle: MiniAppBundleEntity,
        versions: List<MiniAppVersionEntity>,
        event: MiniAppEventEntity
    ) {
        migratedRecords.forEach { upsertRecord(it) }
        installBundle(bundle)
        versions.forEach { upsertVersion(it) }
        insertEvent(event)
    }

    @Transaction
    suspend fun persistRollback(bundle: MiniAppBundleEntity, event: MiniAppEventEntity) {
        installBundle(bundle)
        insertEvent(event)
    }

    @Transaction
    suspend fun persistRecordMutation(record: MiniAppRecordEntity, event: MiniAppEventEntity) {
        upsertRecord(record)
        insertEvent(event)
    }

    @Transaction
    suspend fun deleteRecordWithEvent(
        miniAppId: String,
        recordId: String,
        event: MiniAppEventEntity
    ): Boolean {
        val deleted = deleteRecord(miniAppId, recordId)
        if (deleted > 0) insertEvent(event)
        return deleted > 0
    }
}
