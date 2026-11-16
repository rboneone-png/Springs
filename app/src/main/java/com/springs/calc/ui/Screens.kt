package com.springs.calc.ui

import androidx.compose.runtime.*
import com.springs.calc.*

@Composable
fun CompressionScreen() {
    val d = remember { mutableStateOf("2") }; val od = remember { mutableStateOf("16") }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf("60") }
    val lw = remember { mutableStateOf("40") }
    var mat by remember { mutableStateOf(Material.all[0]) }
    var end by remember { mutableStateOf(EndType.CLOSED_GROUND) }
    Hint()
    MaterialPicker(mat) { mat = it }
    Dropdown("端部型式", EndType.entries, end, { it.label }) { end = it }
    Row2({ NumField("線徑 d", "mm", d) }, { NumField("外徑 OD", "mm", od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長 L0", "mm", l0) })
    NumField("工作長度 L", "mm", lw)
    val r = runCatching {
        SpringCalc.compression(num(d)!!, num(od)!!, num(na)!!, num(l0)!!, num(lw)!!, mat, end)
    }.getOrNull()
    if (r != null) ResultCard(listOf(
        "中徑 D" to "${f(r.dm)} mm", "旋繞比 C" to f(r.c), "彈簧常數 k" to "${f(r.k, 3)} N/mm",
        "總圈數 Nt" to f(r.totalCoils, 1), "密合高度 Ls" to "${f(r.solid)} mm",
        "工作負荷 F" to "${f(r.load)} N", "工作剪應力 τ" to "${f(r.stress, 1)} MPa",
        "密合負荷 Fs" to "${f(r.solidLoad)} N", "密合剪應力 τs" to "${f(r.solidStress, 1)} MPa",
    ), r.warnings)
}

@Composable
fun TorsionScreen() {
    val d = remember { mutableStateOf("2") }; val od = remember { mutableStateOf("16") }
    val nb = remember { mutableStateOf("6") }; val l1 = remember { mutableStateOf("30") }
    val l2 = remember { mutableStateOf("30") }; val ang = remember { mutableStateOf("90") }
    val arm = remember { mutableStateOf("30") }
    var mat by remember { mutableStateOf(Material.all[0]) }
    Hint()
    MaterialPicker(mat) { mat = it }
    Row2({ NumField("線徑 d", "mm", d) }, { NumField("外徑 OD", "mm", od) })
    Row2({ NumField("本體圈數 N", "圈", nb) }, { NumField("扭轉角 θ", "度", ang) })
    Row2({ NumField("臂長 L1", "mm", l1) }, { NumField("臂長 L2", "mm", l2) })
    NumField("受力點半徑", "mm", arm)
    val r = runCatching {
        SpringCalc.torsion(num(d)!!, num(od)!!, num(nb)!!, num(l1)!!, num(l2)!!, num(ang)!!, num(arm)!!, mat)
    }.getOrNull()
    if (r != null) ResultCard(listOf(
        "中徑 D" to "${f(r.dm)} mm", "旋繞比 C" to f(r.c), "等效圈數 Na" to f(r.na, 2),
        "扭力常數" to "${f(r.ratePerDeg, 3)} N·mm/°", "扭矩 M" to "${f(r.torque, 1)} N·mm",
        "受力點負荷 F" to "${f(r.force)} N", "彎曲應力 σ" to "${f(r.stress, 1)} MPa",
        "扭緊後外徑" to "${f(r.closedDia)} mm",
    ), r.warnings)
}

@Composable
fun ExtensionScreen() {
    val d = remember { mutableStateOf("2") }; val od = remember { mutableStateOf("16") }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf("60") }
    val lw = remember { mutableStateOf("80") }; val fi = remember { mutableStateOf("10") }
    val fm = remember { mutableStateOf("200") }
    var mat by remember { mutableStateOf(Material.all[0]) }
    Hint()
    MaterialPicker(mat) { mat = it }
    Row2({ NumField("線徑 d", "mm", d) }, { NumField("外徑 OD", "mm", od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長(鉤內) L0", "mm", l0) })
    Row2({ NumField("初張力 Fi", "N", fi) }, { NumField("最大負荷", "N", fm) })
    NumField("工作長度 L", "mm", lw)
    val r = runCatching {
        SpringCalc.extension(num(d)!!, num(od)!!, num(na)!!, num(l0)!!, num(lw)!!, num(fi)!!, num(fm)!!, mat)
    }.getOrNull()
    if (r != null) ResultCard(listOf(
        "中徑 D" to "${f(r.dm)} mm", "旋繞比 C" to f(r.c), "彈簧常數 k" to "${f(r.k, 3)} N/mm",
        "本體長度" to "${f(r.bodyLength)} mm", "工作負荷 F" to "${f(r.load)} N",
        "工作剪應力 τ" to "${f(r.stress, 1)} MPa", "初張力應力" to "${f(r.initialStress, 1)} MPa",
        "最大負荷應力" to "${f(r.stressMax, 1)} MPa",
    ), r.warnings)
}
