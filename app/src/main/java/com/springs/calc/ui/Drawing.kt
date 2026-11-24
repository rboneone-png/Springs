package com.springs.calc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import com.springs.calc.EndType

fun EndType.key() = when (this) {
    EndType.CLOSED_GROUND -> "cg"; EndType.CLOSED -> "c"; EndType.OPEN -> "o"; EndType.OPEN_GROUND -> "og"
}

/** 產生工程圖按鈕：build() 回傳 JSON；輸入不完整時以 Toast 提示 */
@Composable
fun DrawingButton(onDrawing: (String) -> Unit, build: () -> String) {
    val ctx = LocalContext.current
    Column {
        OutlinedButton(onClick = {
            val json = runCatching(build).getOrNull()
            if (json != null) onDrawing(json) else Toast.makeText(ctx, "請先輸入完整規格（線徑、外徑、圈數、長度等）", Toast.LENGTH_SHORT).show()
        }) { Text("📐 產生工程圖（左：規格　右：示意圖）") }
        OutlinedButton(onClick = {
            val json = runCatching(build).getOrNull()
            if (json == null) Toast.makeText(ctx, "請先輸入完整規格（線徑、外徑、圈數、長度等）", Toast.LENGTH_SHORT).show()
            else runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL + "#s3d=" + Uri.encode(json))))
            }.onFailure { Toast.makeText(ctx, "無法開啟瀏覽器", Toast.LENGTH_SHORT).show() }
        }) { Text("🧊 3D 模型／下載 STEP（開啟網頁版）") }
    }
}

private const val WEB_URL = "https://rboneone-png.github.io/Springs/"

private fun page(js: String, json: String) =
    """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;background:#e9eef0}svg{width:100%;height:auto;display:block;background:#fff}@page{size:A4 landscape;margin:0}</style></head><body><script>$js</script><script>document.body.insertAdjacentHTML('beforeend', DWG.render($json));</script></body></html>"""

private fun printDrawing(ctx: Context, web: WebView) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val name = "彈簧工程圖"
    pm.print(name, web.createPrintDocumentAdapter(name),
        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).build())
}

/** 工程圖畫面：與網頁版共用同一份繪圖程式（assets/drawing.js），可縮放檢視，並用系統列印功能存成 PDF */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(json: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val js = remember { ctx.assets.open("drawing.js").bufferedReader().use { it.readText() } }
    var web by remember { mutableStateOf<WebView?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("工程圖（A4 橫向）") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = { TextButton(onClick = { web?.let { printDrawing(ctx, it) } }) { Text("列印／存成 PDF") } })
        },
    ) { pad ->
        AndroidView(modifier = Modifier.padding(pad).fillMaxSize(), factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true; settings.loadWithOverviewMode = true
                settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
                loadDataWithBaseURL(null, page(js, json), "text/html", "utf-8", null)
                web = this
            }
        })
    }
}
