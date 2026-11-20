package com.springs.calc

import android.os.Bundle
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
    // 說明書以覆蓋層顯示，關閉後回到原畫面，已輸入的數值不會消失
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(if (tab == Tab.DESIGN) "規格設計" else "${tab.title}彈簧") },
            actions = {
                TextButton(onClick = { showManual = true }) { Text("說明") }
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
                    Tab.COMP -> CompressionScreen(units) { drawingJson = it }
                    Tab.TORS -> TorsionScreen(units) { drawingJson = it }
                    Tab.EXT -> ExtensionScreen(units) { drawingJson = it }
                    Tab.DESIGN -> DesignScreen(units) { drawingJson = it }
                }
            }
        }
    }
    if (showManual) ManualScreen { showManual = false }
    drawingJson?.let { DrawingScreen(it) { drawingJson = null } }
    }
}
