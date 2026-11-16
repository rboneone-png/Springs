package com.springs.calc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.springs.calc.Material as Mat

/** 數字輸入欄；回傳目前 Double?（空白或非法為 null） */
@Composable
fun NumField(label: String, unit: String, state: MutableState<String>) {
    OutlinedTextField(
        value = state.value, onValueChange = { state.value = it },
        label = { Text("$label ($unit)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = state.value.isNotBlank() && state.value.toDoubleOrNull() == null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun num(s: MutableState<String>): Double? = s.value.toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, items: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(text(selected), {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }) {
            items.forEach { DropdownMenuItem(text = { Text(text(it)) }, onClick = { onSelect(it); open = false }) }
        }
    }
}

@Composable
fun MaterialPicker(units: Units, selected: Mat, onSelect: (Mat) -> Unit) =
    Dropdown("材質", Mat.all, selected,
        { "${it.name}  G=${units.modulus(it.g)} E=${units.modulus(it.e)}" }, onSelect)

@Composable
fun Row2(a: @Composable () -> Unit, b: @Composable () -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { a() }; Box(Modifier.weight(1f)) { b() }
    }

@Composable
fun ResultCard(rows: List<Pair<String, String>>, warnings: List<String>, title: String = "計算結果") {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k); Text(v, style = MaterialTheme.typography.titleSmall)
                }
            }
            warnings.forEach {
                Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun f(v: Double, digits: Int = 2) = "%.${digits}f".format(v)

@Composable
fun Hint() = Text("輸入完整後即時計算。可於右上角切換公制/英制。", style = MaterialTheme.typography.bodySmall)
