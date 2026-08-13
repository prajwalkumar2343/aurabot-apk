package com.aura.app.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.aura.app.widgets.AuraWidget
import com.aura.app.widgets.AuraWidgetContentFormat
import java.io.ByteArrayInputStream

@Composable
fun AuraSurfaceScreen(
    widget: AuraWidget,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("aura-surface-${widget.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(widget.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    widget.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss ${widget.title}")
            }
        }
        when (widget.contentFormat) {
            AuraWidgetContentFormat.Html -> AuraReportHtml(widget.content.orEmpty())
            AuraWidgetContentFormat.PlainText -> Text(
                text = widget.content.orEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuraReportHtml(content: String) {
    val document = remember(content) { secureReportDocument(content) }
    val reportView = remember { arrayOfNulls<WebView>(1) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse = WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
                reportView[0] = this
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("aura-report-html")
    )
    DisposableEffect(Unit) {
        onDispose {
            reportView[0]?.stopLoading()
            reportView[0]?.destroy()
            reportView[0] = null
        }
    }
}

private fun secureReportDocument(content: String): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:">
        <style>
          :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
          body { margin: 0; padding: 20px; line-height: 1.5; overflow-wrap: anywhere; }
          table { width: 100%; border-collapse: collapse; }
          th, td { padding: 10px 8px; border-bottom: 1px solid rgba(128,128,128,.28); text-align: left; }
          img { max-width: 100%; height: auto; }
          a { color: inherit; text-decoration: underline; }
        </style>
      </head>
      <body>$content</body>
    </html>
""".trimIndent()
