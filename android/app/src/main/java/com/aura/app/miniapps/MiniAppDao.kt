package com.aura.app.miniapps

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Query("DELETE FROM mini_app_records WHERE id = :recordId AND miniAppId = :miniAppId")
    suspend fun deleteRecord(miniAppId: String, recordId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: MiniAppEventEntity)
}
