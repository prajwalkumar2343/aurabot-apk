package com.aura.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.app.AppContainer
import com.aura.app.automations.AutomationActionTypeSets
import com.aura.app.automations.AutomationActionTypes
import com.aura.app.automations.AutomationEvents
import com.aura.app.automations.AutomationPermissionStatus
import com.aura.app.automations.AutomationRunLog
import com.aura.app.automations.AutomationSpec
import com.aura.app.automations.AutomationTriggerTypes
import com.aura.app.apps.AppInfo
import com.aura.app.assistant.DEFAULT_GEMINI_MODEL
import com.aura.app.assistant.LlmProvider
import com.aura.app.assistant.MemoryAppProposal
import com.aura.app.assistant.MessageRole
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppComponent
import com.aura.app.miniapps.MiniAppComponentItem
import com.aura.app.miniapps.MiniAppEvolutionSuggestion
import com.aura.app.miniapps.MiniAppField
import com.aura.app.miniapps.MiniAppInstall
import com.aura.app.miniapps.MiniAppRecord
import com.aura.app.miniapps.MiniAppRevisionPreview
import com.aura.app.miniapps.MiniAppVersion
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun MiniAppReactRuntimeScreen(
    bundle: MiniAppBundle,
    versions: List<MiniAppVersion>,
    evolutionSuggestion: MiniAppEvolutionSuggestion?,
    revisionPreview: MiniAppRevisionPreview?,
    revising: Boolean,
    onBack: () -> Unit,
    onRevise: (String) -> Unit,
    onDraftEvolution: () -> Unit,
    onDismissEvolution: () -> Unit,
    onAcceptRevision: () -> Unit,
    onDismissRevision: () -> Unit,
    onRollback: (Int) -> Unit,
    onListRecords: suspend (String, String?) -> List<MiniAppRecord>,
    onCreateRecord: suspend (String, String, Map<String, String>) -> MiniAppRecord,
    onUpdateRecord: suspend (String, String, Map<String, String>) -> MiniAppRecord?,
    onDeleteRecord: suspend (String, String) -> Boolean
) {
    val primary = parseMiniAppColor(bundle.theme.primary, MaterialTheme.colorScheme.primary)
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    var webView by remember(bundle.id) { mutableStateOf<WebView?>(null) }
    val html = remember(bundle.id, bundle.codeBundle?.compiledJs, bundle.codeBundle?.css) {
        buildMiniAppReactHtml(bundle)
    }
    ScreenShell(wallpaperUri = null) {
        MiniAppTopBar(bundle, "React App", primary, onBack)
        Spacer(Modifier.height(14.dp))
        MiniAppForgePanel(
            bundle = bundle,
            versions = versions,
            evolutionSuggestion = evolutionSuggestion,
            revisionPreview = revisionPreview,
            revising = revising,
            primary = primary,
            onRevise = onRevise,
            onDraftEvolution = onDraftEvolution,
            onDismissEvolution = onDismissEvolution,
            onAcceptRevision = onAcceptRevision,
            onDismissRevision = onDismissRevision,
            onRollback = onRollback
        )
        Spacer(Modifier.height(14.dp))
        key(bundle.id, html) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("field-notes-react-webview")
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        webViewClient = AuraMiniAppWebViewClient()
                        addJavascriptInterface(
                            AuraMiniAppWebBridge(
                                scope = scope,
                                gson = gson,
                                webViewProvider = { webView },
                                miniAppId = bundle.id,
                                onListRecords = onListRecords,
                                onCreateRecord = onCreateRecord,
                                onUpdateRecord = onUpdateRecord,
                                onDeleteRecord = onDeleteRecord
                            ),
                            "AuraNativeBridge"
                        )
                        loadDataWithBaseURL("https://appassets.androidplatform.net/mini-apps/${bundle.id}/", html, "text/html", "UTF-8", null)
                    }
                },
                update = { view ->
                    webView = view
                }
            )
        }
    }
}

private class AuraMiniAppWebBridge(
    private val scope: CoroutineScope,
    private val gson: Gson,
    private val webViewProvider: () -> WebView?,
    private val miniAppId: String,
    private val onListRecords: suspend (String, String?) -> List<MiniAppRecord>,
    private val onCreateRecord: suspend (String, String, Map<String, String>) -> MiniAppRecord,
    private val onUpdateRecord: suspend (String, String, Map<String, String>) -> MiniAppRecord?,
    private val onDeleteRecord: suspend (String, String) -> Boolean
) {
    @JavascriptInterface
    fun postMessage(raw: String) {
        if (raw.length > MaxBridgeMessageChars) return
        scope.launch {
            val response = runCatching {
                val request = gson.fromJson(raw, MiniAppBridgeRequest::class.java)
                val result = when (request.method) {
                    "records.list" -> onListRecords(miniAppId, request.recordType).map { it.bridgeMap() }
                    "records.create" -> onCreateRecord(
                        miniAppId,
                        request.recordType?.takeIf { it.isNotBlank() } ?: "record",
                        request.values.coerceBridgeValues()
                    ).bridgeMap()
                    "records.update" -> {
                        val recordId = request.recordId?.takeIf { it.isNotBlank() } ?: error("recordId is required")
                        onUpdateRecord(miniAppId, recordId, request.values.coerceBridgeValues())?.bridgeMap()
                            ?: error("Record not found")
                    }
                    "records.delete" -> {
                        val recordId = request.recordId?.takeIf { it.isNotBlank() } ?: error("recordId is required")
                        mapOf("deleted" to onDeleteRecord(miniAppId, recordId))
                    }
                    else -> error("Unsupported method: ${request.method}")
                }
                mapOf("id" to request.id, "ok" to true, "result" to result)
            }.getOrElse { error ->
                mapOf("id" to extractBridgeRequestId(raw, gson), "ok" to false, "error" to (error.message ?: "Runtime request failed"))
            }
            val payload = gson.toJson(response)
            webViewProvider()?.post {
                webViewProvider()?.evaluateJavascript("window.__AuraRuntimeResolve(${JSONObject.quote(payload)})", null)
            }
        }
    }
}

private class AuraMiniAppWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
        return if (uri.scheme == "http" || uri.scheme == "https") {
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

}

private data class MiniAppBridgeRequest(
    val id: String = "",
    val method: String = "",
    val recordType: String? = null,
    val recordId: String? = null,
    val values: Map<String, Any?>? = null
)

private fun extractBridgeRequestId(raw: String, gson: Gson): String =
    runCatching { gson.fromJson(raw, MiniAppBridgeRequest::class.java).id }.getOrDefault("")

private fun Map<String, Any?>?.coerceBridgeValues(): Map<String, String> =
    this.orEmpty().entries.take(60).associate { (key, value) ->
        key.take(80) to when (value) {
            null -> ""
            is String -> value.take(4000)
            is Number, is Boolean -> value.toString()
            else -> gsonSafeString(value).take(4000)
        }
    }.filterKeys { it.isNotBlank() }

private fun gsonSafeString(value: Any): String = runCatching { Gson().toJson(value) }.getOrElse { value.toString() }

private fun MiniAppRecord.bridgeMap(): Map<String, Any> =
    mapOf(
        "id" to id,
        "miniAppId" to miniAppId,
        "recordType" to recordType,
        "values" to values,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

private fun buildMiniAppReactHtml(bundle: MiniAppBundle): String {
    val code = bundle.codeBundle
    val css = code?.css.orEmpty().escapeScriptEnd()
    val compiled = code?.compiledJs.orEmpty().escapeScriptEnd()
    return """
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; connect-src 'none'; img-src data:; media-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
  <style>
    html, body, #root { min-height: 100%; margin: 0; }
    body { background: #f7f8fb; overflow-x: hidden; }
    button, input, textarea, select { font: inherit; }
    $css
  </style>
</head>
<body>
  <div id="root"></div>
  <script>
    (function() {
      var seq = 0;
      var pending = {};
      function request(method, payload) {
        var id = String(++seq);
        var message = Object.assign({ id: id, method: method }, payload || {});
        return new Promise(function(resolve, reject) {
          pending[id] = { resolve: resolve, reject: reject };
          window.AuraNativeBridge.postMessage(JSON.stringify(message));
          window.setTimeout(function() {
            if (pending[id]) {
              delete pending[id];
              reject(new Error("Aura request timed out"));
            }
          }, 12000);
        });
      }
      window.__AuraRuntimeResolve = function(raw) {
        var message = JSON.parse(raw);
        var slot = pending[message.id];
        if (!slot) return;
        delete pending[message.id];
        if (message.ok) slot.resolve(message.result);
        else slot.reject(new Error(message.error || "Aura request failed"));
      };
      window.aura = {
        theme: ${JSONObject.quote(bundle.theme.primary)},
        records: {
          list: function(recordType) { return request("records.list", { recordType: recordType || null }); },
          create: function(recordType, values) { return request("records.create", { recordType: recordType || "record", values: values || {} }); },
          update: function(recordId, values) { return request("records.update", { recordId: recordId, values: values || {} }); },
          delete: function(recordId) { return request("records.delete", { recordId: recordId }); }
        }
      };
    })();
  </script>
  <script>$compiled</script>
  <script>
    if (window.__AuraMiniAppMount) {
      window.__AuraMiniAppMount(document.getElementById("root"), window.aura);
    } else {
      document.getElementById("root").innerHTML = "<main style='padding:18px;font-family:system-ui'>React mini app did not expose a mount function.</main>";
    }
  </script>
</body>
</html>
""".trimIndent()
}

private fun String.escapeScriptEnd(): String = replace("</script", "<\\/script", ignoreCase = true)

private const val MaxBridgeMessageChars = 32_000
