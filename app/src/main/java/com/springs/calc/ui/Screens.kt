package com.springs.calc.ui

import androidx.compose.runtime.*
import com.springs.calc.*

private fun MutableState<String>.si(u: Units, k: Kind) = value.toDoubleOrNull()?.let { u.toSi(it, k) }!!

@Composable
fun CompressionScreen(u: Units) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf(u.def("60", Kind.LEN)) }
    val lw = remember { mutableStateOf(u.def("40", Kind.LEN)) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    var end by remember { mutableStateOf(EndType.CLOSED_GROUND) }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l0 to Kind.LEN, lw to Kind.LEN))
    Hint()
    MaterialPicker(u, mat) { mat = it }
    Dropdown("端部型式", EndType.entries, end, { it.label }) { end = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長 L0", u.len, l0) })
    NumField("工作長度 L", u.len, lw)
    val r = runCatching {
        SpringCalc.compression(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(na)!!,
            l0.si(u, Kind.LEN), lw.si(u, Kind.LEN), mat, end)
    }.getOrNull()
    if (r != null) ResultCard(listOf(
        "中徑 D" to "${f(u.lenOut(r.dm), 3)} ${u.len}", "旋繞比 C" to f(r.c),
        "彈簧常數 k" to "${f(u.rateOut(r.k), 3)} ${u.rate}",
        "總圈數 Nt" to f(r.totalCoils, 1), "密合高度 Ls" to "${f(u.lenOut(r.solid), 3)} ${u.len}",
        "工作負荷 F" to "${f(u.forceOut(r.load))} ${u.force}", "工作剪應力 τ" to "${f(u.stressOut(r.stress), 1)} ${u.stress}",
        "密合負荷 Fs" to "${f(u.forceOut(r.solidLoad))} ${u.force}", "密合剪應力 τs" to "${f(u.stressOut(r.solidStress), 1)} ${u.stress}",
    ), r.warnings)
}

@Composable
fun TorsionScreen(u: Units) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val nb = remember { mutableStateOf("6") }; val l1 = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    val l2 = remember { mutableStateOf(u.def("30", Kind.LEN)) }; val ang = remember { mutableStateOf("90") }
    val arm = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l1 to Kind.LEN, l2 to Kind.LEN, arm to Kind.LEN))
    Hint()
    MaterialPicker(u, mat) { mat = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("本體圈數 N", "圈", nb) }, { NumField("扭轉角 θ", "度", ang) })
    Row2({ NumField("臂長 L1", u.len, l1) }, { NumField("臂長 L2", u.len, l2) })
    NumField("受力點半徑", u.len, arm)
    val r = runCatching {
        SpringCalc.torsion(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(nb)!!, l1.si(u, Kind.LEN),
            l2.si(u, Kind.LEN), num(ang)!!, arm.si(u, Kind.LEN), mat)
    }.getOrNull()
    if (r != null) ResultCard(listOf(
        "中徑 D" to "${f(u.lenOut(r.dm), 3)} ${u.len}", "旋繞比 C" to f(r.c), "等效圈數 Na" to f(r.na, 2),
        "扭力常數" to "${f(u.torqueOut(r.ratePerDeg), 3)} ${u.torqueRate}",
        "扭矩 M" to "${f(u.torqueOut(r.torque), 2)} ${u.torque}",
        "受力點負荷 F" to "${f(u.forceOut(r.force))} ${u.force}", "彎曲應力 σ" to "${f(u.stressOut(r.stress), 1)} ${u.stress}",
        "扭緊後外徑" to "${f(u.lenOut(r.closedDia), 3)} ${u.len}",
    ), r.warnings)
}

@Composable
fun ExtensionScreen(u: Units) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf(u.def("60", Kind.LEN)) }
    val lw = remember { mutableStateOf(u.def("80", Kind.LEN)) }; val fi = remember { mutableStateOf(u.def("10", Kind.FORCE)) }
    val fm = remember { mutableStateOf(u.def("200", Kind.FORCE)) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l0 to Kind.LEN, lw to Kind.LEN,
        fi to Kind.FORCE, fm to Kind.FORCE))
    Hint()
    MaterialPicker(u, mat) { mat = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長(鉤內) L0", u.len, l0) })
    Row2({ NumField("初張力 Fi", u.force, fi) }, { NumField("最大負荷", u.force, fm) })
    NumField("工作長度 L", u.len, lw)
    val r = runCatching {
        SpringCalc.extension(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(na)!!, l0.si(u, Kind.LEN),
            lw.si(u, Kind.LEN), fi.si(u, Kind.FORCE), fm.si(u, Kind.FORCE), mat)
    }.getOrNull()
    if (r != null) ResultCard(listOf(
        "中徑 D" to "${f(u.lenOut(r.dm), 3)} ${u.len}", "旋繞比 C" to f(r.c),
        "彈簧常數 k" to "${f(u.rateOut(r.k), 3)} ${u.rate}",
        "本體長度" to "${f(u.lenOut(r.bodyLength), 3)} ${u.len}", "工作負荷 F" to "${f(u.forceOut(r.load))} ${u.force}",
        "工作剪應力 τ" to "${f(u.stressOut(r.stress), 1)} ${u.stress}", "初張力應力" to "${f(u.stressOut(r.initialStress), 1)} ${u.stress}",
        "最大負荷應力" to "${f(u.stressOut(r.stressMax), 1)} ${u.stress}",
    ), r.warnings)
}
