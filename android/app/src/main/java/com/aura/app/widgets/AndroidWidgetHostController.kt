package com.aura.app.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidWidgetHostController(
    context: Context,
    private val repository: AuraWidgetRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val resizeMutex = Mutex()
    private val host = object : AppWidgetHost(appContext, HOST_ID) {
        override fun onAppWidgetRemoved(appWidgetId: Int) {
            super.onAppWidgetRemoved(appWidgetId)
            scope.launch {
                try {
                    repository.removeHostedWidget(appWidgetId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Startup reconciliation retries host/storage cleanup.
                }
            }
        }
    }

    fun startListening() {
        host.startListening()
    }

    fun stopListening() {
        host.stopListening()
    }

    suspend fun reconcile(ignoredAppWidgetIds: Set<Int> = emptySet()) {
        val hostIds = host.appWidgetIds.toSet().minus(ignoredAppWidgetIds)
        val storedIds = repository.hostedWidgetIds()
        val unavailableIds = hostIds.filterTo(mutableSetOf()) {
            manager.getAppWidgetInfo(it) == null
        }
        unavailableIds.forEach { appWidgetId ->
            repository.removeHostedWidget(appWidgetId)
            abandonAppWidgetId(appWidgetId)
        }
        val availableHostIds = hostIds.minus(unavailableIds)
        storedIds.minus(availableHostIds).minus(ignoredAppWidgetIds).forEach {
            repository.removeHostedWidget(it)
        }
        availableHostIds.minus(storedIds).forEach { appWidgetId ->
            if (persistBoundWidget(appWidgetId) == null) {
                abandonAppWidgetId(appWidgetId)
            }
        }
    }

    fun allocateAppWidgetId(): Int = host.allocateAppWidgetId()

    fun abandonAppWidgetId(appWidgetId: Int) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { host.deleteAppWidgetId(appWidgetId) }
        }
    }

    suspend fun persistBoundWidget(appWidgetId: Int): HostedAndroidWidget? {
        val provider = manager.getAppWidgetInfo(appWidgetId) ?: return null
        val spanX = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            provider.targetCellWidth > 0
        ) {
            provider.targetCellWidth
        } else {
            cellsFor(provider.minWidth)
        }.coerceIn(1, MAX_COLUMNS)
        val spanY = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            provider.targetCellHeight > 0
        ) {
            provider.targetCellHeight
        } else {
            cellsFor(provider.minHeight)
        }.coerceIn(1, MAX_ROWS)
        updateWidgetSize(appWidgetId, spanX, spanY)
        return repository.addHostedWidget(
            appWidgetId = appWidgetId,
            providerPackage = provider.provider.packageName,
            providerClass = provider.provider.className,
            spanX = spanX,
            spanY = spanY
        )
    }

    fun createView(context: Context, appWidgetId: Int): View? {
        val provider = manager.getAppWidgetInfo(appWidgetId) ?: return null
        return host.createView(context, appWidgetId, provider)
    }

    suspend fun resize(appWidgetId: Int, spanX: Int, spanY: Int): HostedAndroidWidget? =
        resizeMutex.withLock {
            val current = repository.hostedWidget(appWidgetId) ?: return@withLock null
            val provider = manager.getAppWidgetInfo(appWidgetId) ?: return@withLock null
            val (normalizedX, normalizedY) = constrainedSpans(provider, current, spanX, spanY)
            updateWidgetSize(appWidgetId, normalizedX, normalizedY)
            try {
                repository.resizeHostedWidget(appWidgetId, normalizedX, normalizedY)
            } catch (error: Exception) {
                runCatching { updateWidgetSize(appWidgetId, current.spanX, current.spanY) }
                throw error
            }
        }

    suspend fun remove(appWidgetId: Int) = resizeMutex.withLock {
        host.deleteAppWidgetId(appWidgetId)
        repository.removeHostedWidget(appWidgetId)
    }

    fun appWidgetInfo(appWidgetId: Int) = manager.getAppWidgetInfo(appWidgetId)

    private fun updateWidgetSize(appWidgetId: Int, spanX: Int, spanY: Int) {
        val minWidth = sizeDpForSpan(spanX)
        val minHeight = sizeDpForSpan(spanY)
        manager.updateAppWidgetOptions(
            appWidgetId,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                putInt(
                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        arrayListOf(SizeF(minWidth.toFloat(), minHeight.toFloat()))
                    )
                }
            }
        )
    }

    private fun constrainedSpans(
        provider: AppWidgetProviderInfo,
        current: HostedAndroidWidget,
        requestedX: Int,
        requestedY: Int
    ): Pair<Int, Int> {
        val canResizeHorizontally =
            provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val canResizeVertically =
            provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val minX = cellsFor(maxOf(provider.minWidth, provider.minResizeWidth)).coerceIn(1, MAX_COLUMNS)
        val minY = cellsFor(maxOf(provider.minHeight, provider.minResizeHeight)).coerceIn(1, MAX_ROWS)
        val maxX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            provider.maxResizeWidth
                .takeIf { it > 0 }
                ?.let(::cellsFor)
                ?.coerceIn(minX, MAX_COLUMNS)
                ?: MAX_COLUMNS
        } else {
            MAX_COLUMNS
        }
        val maxY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            provider.maxResizeHeight
                .takeIf { it > 0 }
                ?.let(::cellsFor)
                ?.coerceIn(minY, MAX_ROWS)
                ?: MAX_ROWS
        } else {
            MAX_ROWS
        }
        val spanX = if (canResizeHorizontally) requestedX.coerceIn(minX, maxX) else current.spanX
        val spanY = if (canResizeVertically) requestedY.coerceIn(minY, maxY) else current.spanY
        return spanX to spanY
    }

    private fun cellsFor(sizeDp: Int): Int =
        ceil(sizeDp.coerceAtLeast(CELL_DP).toDouble() / CELL_DP.toDouble()).toInt()

    private fun sizeDpForSpan(span: Int): Int = span * CELL_DP - CELL_GAP_DP

    companion object {
        private const val HOST_ID = 0xA012
        private const val CELL_DP = 72
        private const val CELL_GAP_DP = 16
        private const val MAX_COLUMNS = 4
        private const val MAX_ROWS = 6
    }
}
