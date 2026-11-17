package com.springs.calc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.springs.calc.*
import com.springs.calc.SpringCalc.Strength
import com.springs.calc.SpringCalc.Verdict

private fun MutableState<String>.si(u: Units, k: Kind) = value.toDoubleOrNull()?.let { u.toSi(it, k) }!!

private fun sRow(label: String, s: Strength) = label to "${f(s.ratio, 2)}  ${s.verdict.label}"
private fun sWarn(name: String, s: Strength) =
    if (s.verdict == Verdict.FAIL) listOf("$name 超過材質強度安全範圍") else emptyList()
private fun sf(x: Double) = if (x.isInfinite()) "∞" else f(x, 2)
private fun lifeText(c: Double, below: Boolean, inf: String) = when {
    c.isInfinite() -> inf
    below -> "< 10³ 次"
    else -> "約 ${fmtCycles(c)} 次"
}

@Composable
private fun PeenedSwitch(v: Boolean, on: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = v, onCheckedChange = on); Spacer(Modifier.width(8.dp)); Text("珠擊處理 (Shot peened)")
    }

/** 可新增/刪除的多筆輸入（工作長度、扭轉角） */
@Composable
private fun MultiInput(label: String, unit: String, items: SnapshotStateList<MutableState<String>>, addText: String) {
    items.forEachIndexed { i, st ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { NumField("$label${i + 1}", unit, st) }
            if (items.size > 1) IconButton(onClick = { items.removeAt(i) }) {
                Icon(Icons.Default.Delete, contentDescription = "刪除")
            }
        }
    }
    OutlinedButton(onClick = { items.add(mutableStateOf("")) }) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(addText)
    }
}

// ================= 壓縮 =================
@Composable
fun CompressionScreen(u: Units) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf(u.def("60", Kind.LEN)) }
    val lws = remember { mutableStateListOf(mutableStateOf(u.def("40", Kind.LEN))) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    var end by remember { mutableStateOf(EndType.CLOSED_GROUND) }
    val lmaxS = remember { mutableStateOf("") }
    var peened by remember { mutableStateOf(false) }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l0 to Kind.LEN, lmaxS to Kind.LEN) + lws.map { it to Kind.LEN })
    Hint()
    CompressionDiagram()
    MaterialPicker(u, mat) { mat = it }
    Dropdown("端部型式", EndType.entries, end, { it.label }) { end = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長 L0", u.len, l0) })
    MultiInput("工作長度 L", u.len, lws, "新增工作長度")
    NumField("按壓回彈長度（循環最大長度，空白＝自由長）", u.len, lmaxS)
    PeenedSwitch(peened) { peened = it }
    val base = runCatching {
        SpringCalc.compression(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(na)!!, l0.si(u, Kind.LEN),
            l0.si(u, Kind.LEN), mat, end)
    }.getOrNull()
    if (base != null) {
        val sut = mat.sut(d.si(u, Kind.LEN))
        val solidS = SpringCalc.strengthShear(base.solidStress, sut)
        ResultCard(listOf(
            "中徑 D" to "${f(u.lenOut(base.dm), 3)} ${u.len}", "旋繞比 C" to f(base.c),
            "彈簧常數 k" to "${f(u.rateOut(base.k), 3)} ${u.rate}",
            "總圈數 Nt" to f(base.totalCoils, 1), "密合高度 Ls" to "${f(u.lenOut(base.solid), 3)} ${u.len}",
            "抗拉強度 Sut" to "${f(u.stressOut(sut), 0)} ${u.stress}",
            "密合負荷 Fs" to "${f(u.forceOut(base.solidLoad))} ${u.force}",
            "密合剪應力 τs" to "${f(u.stressOut(base.solidStress), 1)} ${u.stress}",
            sRow("密合強度判定 τs/Sut", solidS),
        ), base.warnings.filter { !it.startsWith("工作長度") } + sWarn("密合時", solidS), title = "彈簧基本參數")
        lws.forEachIndexed { i, st ->
            val lw = st.value.toDoubleOrNull() ?: return@forEachIndexed
            val dSi = d.si(u, Kind.LEN); val l0Si = l0.si(u, Kind.LEN); val lwSi = u.toSi(lw, Kind.LEN)
            val r = SpringCalc.compression(dSi, od.si(u, Kind.LEN), num(na)!!, l0Si, lwSi, mat, end)
            val lMaxSi = lmaxS.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) } ?: l0Si
            val tauMin = if (lMaxSi < l0Si) SpringCalc.shear(r.k * (l0Si - lMaxSi), r.dm, dSi) else 0.0
            val fat = SpringCalc.fatigue(r.stress, tauMin, dSi, mat, peened)
            val str = SpringCalc.strengthShear(r.stress, fat.sut)
            val extraW = fat.warnings + sWarn("工作時", str) +
                if (lwSi > lMaxSi) listOf("工作長度大於回彈長度，循環定義不合理") else emptyList()
            ResultCard(listOf(
                "壓縮量 (L0−L)" to "${f(u.lenOut(l0Si - lwSi), 3)} ${u.len}",
                "工作負荷 F" to "${f(u.forceOut(r.load))} ${u.force}",
                "工作剪應力 τmax" to "${f(u.stressOut(r.stress), 1)} ${u.stress}",
                "回彈剪應力 τmin" to "${f(u.stressOut(tauMin), 1)} ${u.stress}",
                sRow("強度判定 τ/Sut", str),
                "靜態安全係數" to sf(fat.staticSf),
                "疲勞安全係數 (10⁷)" to sf(fat.fatigueSf),
                "預估壽命" to lifeText(fat.cycles, fat.belowThousand, "無限壽命 (>10⁷ 次)"),
            ), r.warnings.filter { it.startsWith("工作長度") } + extraW, title = "L${i + 1} = ${f(lw, 2)} ${u.len}")
        }
    }
}

// ================= 扭力 =================
@Composable
fun TorsionScreen(u: Units) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val nb = remember { mutableStateOf("6") }; val l1 = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    val l2 = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    val angs = remember { mutableStateListOf(mutableStateOf("90")) }
    val arm = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    val angMin = remember { mutableStateOf("0") }
    var peened by remember { mutableStateOf(false) }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l1 to Kind.LEN, l2 to Kind.LEN, arm to Kind.LEN))
    Hint()
    TorsionDiagram()
    MaterialPicker(u, mat) { mat = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("本體圈數 N", "圈", nb) }, { NumField("受力點半徑 R", u.len, arm) })
    Row2({ NumField("臂長 L1", u.len, l1) }, { NumField("臂長 L2", u.len, l2) })
    MultiInput("扭轉角 θ", "度", angs, "新增扭轉角")
    NumField("循環最小角度（預載，0＝完全放鬆）", "度", angMin)
    PeenedSwitch(peened) { peened = it }
    val dSi = runCatching { d.si(u, Kind.LEN) }.getOrNull()
    fun calc(angle: Double) = SpringCalc.torsion(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(nb)!!,
        l1.si(u, Kind.LEN), l2.si(u, Kind.LEN), angle, arm.si(u, Kind.LEN), mat)
    val base = runCatching { calc(0.0) }.getOrNull()
    if (base != null && dSi != null) {
        val sut = mat.sut(dSi)
        ResultCard(listOf(
            "中徑 D" to "${f(u.lenOut(base.dm), 3)} ${u.len}", "旋繞比 C" to f(base.c), "等效圈數 Na" to f(base.na, 2),
            "扭力常數" to "${f(u.torqueOut(base.ratePerDeg), 3)} ${u.torqueRate}",
            "抗拉強度 Sut" to "${f(u.stressOut(sut), 0)} ${u.stress}",
        ), base.warnings, title = "彈簧基本參數")
        val aMin = angMin.value.toDoubleOrNull() ?: 0.0
        angs.forEachIndexed { i, st ->
            val angle = st.value.toDoubleOrNull() ?: return@forEachIndexed
            val r = runCatching { calc(angle) }.getOrNull() ?: return@forEachIndexed
            val sigMin = if (angle > 0) r.stress * aMin / angle else 0.0
            val fat = SpringCalc.fatigueBending(r.stress, sigMin, dSi, mat, peened)
            val str = SpringCalc.strengthBending(r.stress, sut)
            val extraW = fat.warnings + sWarn("工作時", str) +
                if (aMin >= angle) listOf("最小角度需小於工作扭轉角") else emptyList()
            ResultCard(listOf(
                "扭矩 M" to "${f(u.torqueOut(r.torque), 2)} ${u.torque}",
                "受力點負荷 F" to "${f(u.forceOut(r.force))} ${u.force}",
                "彎曲應力 σmax" to "${f(u.stressOut(r.stress), 1)} ${u.stress}",
                "最小彎曲應力 σmin" to "${f(u.stressOut(sigMin), 1)} ${u.stress}",
                sRow("強度判定 σ/Sut", str),
                "扭緊後外徑" to "${f(u.lenOut(r.closedDia), 3)} ${u.len}",
                "靜態安全係數" to sf(fat.staticSf),
                "疲勞安全係數 (10⁶)" to sf(fat.fatigueSf),
                "預估壽命" to lifeText(fat.cycles, fat.belowThousand, "無限壽命 (>10⁶ 次)"),
            ), r.warnings + extraW, title = "θ${i + 1} = ${f(angle, 1)}°")
        }
    }
}

// ================= 拉伸 =================
@Composable
fun ExtensionScreen(u: Units) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf(u.def("60", Kind.LEN)) }
    val lws = remember { mutableStateListOf(mutableStateOf(u.def("80", Kind.LEN))) }
    val fi = remember { mutableStateOf(u.def("10", Kind.FORCE)) }
    val fm = remember { mutableStateOf(u.def("200", Kind.FORCE)) }
    val r1 = remember { mutableStateOf(u.def("7", Kind.LEN)) }; val r2 = remember { mutableStateOf(u.def("4", Kind.LEN)) }
    val lminS = remember { mutableStateOf("") }
    var peened by remember { mutableStateOf(false) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l0 to Kind.LEN, lminS to Kind.LEN,
        fi to Kind.FORCE, fm to Kind.FORCE, r1 to Kind.LEN, r2 to Kind.LEN) + lws.map { it to Kind.LEN })
    Hint()
    ExtensionDiagram()
    MaterialPicker(u, mat) { mat = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長(鉤內) L0", u.len, l0) })
    Row2({ NumField("初張力 Fi", u.force, fi) }, { NumField("最大負荷", u.force, fm) })
    Row2({ NumField("鉤環中心半徑 r1", u.len, r1) }, { NumField("鉤側彎半徑 r2", u.len, r2) })
    MultiInput("工作長度 L", u.len, lws, "新增工作長度")
    NumField("循環最小長度（空白＝自由長，此時負荷＝初張力）", u.len, lminS)
    PeenedSwitch(peened) { peened = it }
    fun calc(lw: Double) = SpringCalc.extension(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(na)!!,
        l0.si(u, Kind.LEN), lw, fi.si(u, Kind.FORCE), fm.si(u, Kind.FORCE), mat,
        r1.si(u, Kind.LEN), r2.si(u, Kind.LEN))
    val base = runCatching { calc(l0.si(u, Kind.LEN)) }.getOrNull()
    if (base != null) {
        val dSi = d.si(u, Kind.LEN); val sut = mat.sut(dSi)
        val initS = SpringCalc.strengthShear(base.initialStress, sut)
        val maxS = SpringCalc.strengthShear(base.stressMax, sut)
        val hbS = SpringCalc.strengthBending(base.hookBendingMax, sut)
        val htS = SpringCalc.strengthShear(base.hookTorsionMax, sut)
        ResultCard(listOf(
            "中徑 D" to "${f(u.lenOut(base.dm), 3)} ${u.len}", "旋繞比 C" to f(base.c),
            "彈簧常數 k" to "${f(u.rateOut(base.k), 3)} ${u.rate}",
            "本體長度" to "${f(u.lenOut(base.bodyLength), 3)} ${u.len}",
            "抗拉強度 Sut" to "${f(u.stressOut(sut), 0)} ${u.stress}",
            "初張力應力" to "${f(u.stressOut(base.initialStress), 1)} ${u.stress}", sRow("　判定 τ/Sut", initS),
            "最大負荷應力" to "${f(u.stressOut(base.stressMax), 1)} ${u.stress}", sRow("　判定 τ/Sut", maxS),
            "最大負荷鉤環彎曲" to "${f(u.stressOut(base.hookBendingMax), 1)} ${u.stress}", sRow("　判定 σ/Sut", hbS),
            "最大負荷鉤側彎扭轉" to "${f(u.stressOut(base.hookTorsionMax), 1)} ${u.stress}", sRow("　判定 τ/Sut", htS),
        ), base.warnings.filter { !it.startsWith("工作長度") && !it.startsWith("負荷小於") } +
            sWarn("最大負荷本體", maxS) + sWarn("最大負荷鉤環彎曲", hbS) + sWarn("最大負荷鉤側彎", htS),
            title = "彈簧基本參數")
        val lMinSi = lminS.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) } ?: l0.si(u, Kind.LEN)
        lws.forEachIndexed { i, st ->
            val lw = st.value.toDoubleOrNull() ?: return@forEachIndexed
            val lwSi = u.toSi(lw, Kind.LEN)
            val r = calc(lwSi)
            val ef = SpringCalc.extensionFatigue(dSi, r.dm, r.k, l0.si(u, Kind.LEN), lwSi, lMinSi,
                fi.si(u, Kind.FORCE), r1.si(u, Kind.LEN), r2.si(u, Kind.LEN), mat, peened)
            val bS = SpringCalc.strengthShear(r.stress, sut)
            val hS = SpringCalc.strengthBending(r.hookBending, sut)
            val tS = SpringCalc.strengthShear(r.hookTorsion, sut)
            ResultCard(listOf(
                "工作負荷 F" to "${f(u.forceOut(r.load))} ${u.force}",
                "本體剪應力 τ" to "${f(u.stressOut(r.stress), 1)} ${u.stress}", sRow("　判定 τ/Sut", bS),
                "鉤環彎曲應力" to "${f(u.stressOut(r.hookBending), 1)} ${u.stress}", sRow("　判定 σ/Sut", hS),
                "鉤側彎扭轉應力" to "${f(u.stressOut(r.hookTorsion), 1)} ${u.stress}", sRow("　判定 τ/Sut", tS),
                "壽命：本體" to lifeText(ef.body.cycles, ef.body.belowThousand, "無限 (>10⁷ 次)"),
                "壽命：鉤環彎曲" to lifeText(ef.hookBend.cycles, ef.hookBend.belowThousand, "無限 (>10⁶ 次)"),
                "壽命：鉤側彎扭轉" to lifeText(ef.hookTors.cycles, ef.hookTors.belowThousand, "無限 (>10⁷ 次)"),
                "預估壽命（取最短：${ef.governing}）" to lifeText(ef.cycles, ef.belowThousand, "無限壽命"),
            ), r.warnings.filter { it.startsWith("工作長度") || it.startsWith("負荷小於") } + ef.warnings +
                sWarn("本體", bS) + sWarn("鉤環彎曲", hS) + sWarn("鉤側彎", tS),
                title = "L${i + 1} = ${f(lw, 2)} ${u.len}")
        }
    }
}

/** 兩位有效數字的循環次數，如 5.3×10⁵ */
private fun fmtCycles(n: Double): String {
    val e = Math.floor(Math.log10(n)).toInt()
    val m = n / Math.pow(10.0, e.toDouble())
    val sup = e.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
    return "%.1f×10%s".format(m, sup)
}
