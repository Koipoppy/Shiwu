package com.shiwu.scan3d.ui.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.shiwu.scan3d.BuildConfig
import java.io.File
import java.net.URLEncoder

/**
 * 用打包在 assets 里的 <model-viewer> 渲染 GLB。
 * 通过 WebViewAssetLoader 把 files/models 映射成 https 资源，避免 file:// 跨域限制。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ModelViewer(
    modelFile: File,
    modifier: Modifier = Modifier,
    autoRotate: Boolean = true,
    onLoaded: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    val currentOnLoaded by rememberUpdatedState(onLoaded)

    val webView = remember(modelFile.absolutePath, autoRotate) {
        createViewer(
            context = context,
            modelName = modelFile.name,
            autoRotate = autoRotate,
            onLoaded = { currentOnLoaded?.invoke() },
            onError = { message -> currentOnError?.invoke(message) }
        )
    }

    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun createViewer(
    context: Context,
    modelName: String,
    autoRotate: Boolean,
    onLoaded: () -> Unit,
    onError: (String) -> Unit
): WebView {
    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

    val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler(
            "/files/",
            WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "models"))
        )
        .build()

    return WebView(context).apply {
        setBackgroundColor(Color.rgb(0xFB, 0xF8, 0xF1))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onModelError(message: String) {
                post { onError(message) }
            }

            @JavascriptInterface
            fun onModelLoaded() {
                post { onLoaded() }
            }
        }, "ShiwuBridge")

        if (BuildConfig.DEBUG) {
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d("ShiwuViewer", "${message.message()} @ ${message.sourceId()}:${message.lineNumber()}")
                    return true
                }
            }
        }

        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = request.url.host != "appassets.androidplatform.net"
        }

        val encodedName = URLEncoder.encode(modelName, "UTF-8")
        val rotate = if (autoRotate) "1" else "0"
        loadUrl("https://appassets.androidplatform.net/assets/viewer.html?model=/files/$encodedName&rotate=$rotate")
    }
}
