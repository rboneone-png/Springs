package com.springs.calc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.springs.calc.ui.*

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.COMP) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("彈簧計算器 · ${tab.title}彈簧") }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(selected = tab == it, onClick = { tab = it },
                        icon = { Icon(it.icon, null) }, label = { Text(it.title) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                Tab.COMP -> CompressionScreen()
                Tab.TORS -> TorsionScreen()
                Tab.EXT -> ExtensionScreen()
            }
        }
    }
}
