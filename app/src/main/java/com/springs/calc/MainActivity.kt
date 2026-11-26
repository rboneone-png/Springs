package com.springs.calc

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.springs.calc.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    COMP("壓縮", Icons.Default.UnfoldLess),
    TORS("扭力", Icons.Default.RotateRight),
    EXT("拉伸", Icons.Default.UnfoldMore),
    DESIGN("設計", Icons.Default.Tune),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val pagerState = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val tab = Tab.entries[pagerState.currentPage]
    var units by rememberSaveable { mutableStateOf(Units.METRIC) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var drawingJson by remember { mutableStateOf<String?>(null) }
    val io = remember { FileIo() }
    val ctx = LocalContext.current
    var fileMenu by remember { mutableStateOf(false) }
    fun fileName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    }.getOrNull()?.replace(Regex("\\.json$", RegexOption.IGNORE_CASE), "")
    fun write(uri: Uri) {
        runCatching {
            val o = JSONObject().put("format", "springcalc").put("version", 1).put("tab", tab.ordinal).put("tabName", tab.title)
                .put("data", io.snaps[tab.ordinal]?.invoke() ?: JSONObject())
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(o.toString(1).toByteArray()) }
        }.onSuccess { io.name = fileName(uri) ?: io.name; Toast.makeText(ctx, "已儲存", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, "儲存失敗：${it.message}", Toast.LENGTH_LONG).show() }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { write(it) } }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            require(o.optString("format") == "springcalc") { "不是本程式存的規格檔" }
            val t = o.getInt("tab"); require(t in 0..3) { "檔案內容無效" }
            scope.launch { pagerState.animateScrollToPage(t) }
            io.loads[t]?.invoke(o.getJSONObject("data"))
            io.name = fileName(uri)
        }.onFailure { Toast.makeText(ctx, "無法讀取：${it.message}", Toast.LENGTH_LONG).show() }
    }
    fun save(asNew: Boolean) { saveLauncher.launch((io.name ?: "${tab.title}規格") + ".json") }
    // 說明書以覆蓋層顯示，關閉後回到原畫面，已輸入的數值不會消失
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { TopAppBar(
            title = { Text((if (tab == Tab.DESIGN) "規格設計" else "${tab.title}彈簧") + (io.name?.let { "｜$it" } ?: "")) },
            actions = {
                TextButton(onClick = { showManual = true }) { Text("說明") }
                Box {
                    TextButton(onClick = { fileMenu = true }) { Text("檔案") }
                    DropdownMenu(fileMenu, { fileMenu = false }) {
                        DropdownMenuItem(text = { Text("開啟檔案…") }, onClick = { fileMenu = false; openLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) })
                        DropdownMenuItem(text = { Text("儲存這一頁…") }, onClick = { fileMenu = false; save(false) })
                    }
                }
                Units.entries.forEach {
                    FilterChip(selected = units == it, onClick = { units = it },
                        label = { Text(it.label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(selected = tab == it, onClick = { scope.launch { pagerState.animateScrollToPage(it.ordinal) } },
                        icon = { Icon(it.icon, null) }, label = { Text(it.title) })
                }
            }
        },
    ) { pad ->
        // 左右滑動切換；beyondViewportPageCount 讓所有分頁都保留，輸入內容不會因切換而消失
        HorizontalPager(pagerState, Modifier.padding(pad).fillMaxSize(), beyondViewportPageCount = Tab.entries.size - 1) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (Tab.entries[page]) {
                    Tab.COMP -> CompressionScreen(units, io) { drawingJson = it }
                    Tab.TORS -> TorsionScreen(units, io) { drawingJson = it }
                    Tab.EXT -> ExtensionScreen(units, io) { drawingJson = it }
                    Tab.DESIGN -> DesignScreen(units, io) { drawingJson = it }
                }
            }
        }
    }
    if (showManual) ManualScreen { showManual = false }
    drawingJson?.let { DrawingScreen(it, io.name, { n -> io.name = n }) { drawingJson = null } }
    }
}
