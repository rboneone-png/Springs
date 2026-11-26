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
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.runtime.saveable.rememberSaveable

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
private fun PeenedSwitch(v: Boolean, on: (Boolean) -> Unit) {
    var ask by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = v, onCheckedChange = { if (it) ask = true else on(false) })
        Spacer(Modifier.width(8.dp)); Text("珠擊處理 (Shot peened)：提升零件的抗疲勞壽命與耐用度(特殊用途選用)")
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("確定啟用珠擊處理？") },
        text = { Text("此選項會提高疲勞壽命的估算值，僅限零件確實經過珠擊處理 (Shot peened) 的特殊用途才選用。") },
        confirmButton = { TextButton(onClick = { ask = false; on(true) }) { Text("確定啟用") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("取消") } },
    )
}

/** 目標次數後的衰減：kind 0 壓縮、1 扭力、2 拉伸；未填目標次數則回傳空 */
private fun relaxInfo(u: Units, mi: Int, kind: Int, ratio: Double, estN: Double, estBelow: Boolean, load: Double, span: Double,
                      nTar: String, temp: String): Pair<List<Pair<String, String>>, List<String>> {
    val n = nTar.toDoubleOrNull()?.takeIf { it > 0 } ?: return emptyList<Pair<String, String>>() to emptyList()
    val t = temp.toDoubleOrNull() ?: 20.0
    val r = SpringCalc.relaxLoss(mi, ratio, n, t)
    val okLife = estN.isInfinite() || (!estBelow && estN >= n)
    val rows = mutableListOf("目標循環次數" to "${fmtCycles(n)} 次",
        "壽命是否達標" to if (okLife) "✔ 達標" else "✘ 不足（預估 ${lifeText(estN, estBelow, "")}）",
        "預估負荷損失（鬆弛）" to "${f(r.loss * 100, 1)} %", "　衰減後負荷" to "${f(u.forceOut(load * (1 - r.loss)))} ${u.force}")
    rows.add(when (kind) {
        0 -> "　自由長縮短（永久變形）" to "${f(u.lenOut(r.loss * span), 3)} ${u.len}"
        2 -> "　自由長增加（永久伸長）" to "${f(u.lenOut(r.loss * span), 3)} ${u.len}"
        else -> "　自由角度減少" to "${f(r.loss * span, 2)} 度"
    })
    val w = mutableListOf<String>()
    if (!okLife) w.add("預估疲勞壽命低於目標次數，在目標次數前可能斷裂")
    if (r.hot) w.add("使用溫度 ${f(t, 0)}°C 超過此材質建議最高 ${r.tmax}°C，鬆弛與剛度下降會更嚴重，此估算不可靠")
    if (r.loss >= .1) w.add("預估負荷損失 ≥ 10%：建議降低應力、加預壓（除應力）處理或換材質")
    return rows to w
}

@Composable
private fun RelaxFields(nTar: MutableState<String>, temp: MutableState<String>) =
    Row2({ NumField("目標循環次數（空白＝不評估衰減）", "次", nTar) }, { NumField("使用溫度（空白＝室溫 20°C）", "°C", temp) })

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
fun CompressionScreen(u: Units, io: FileIo, onDrawing: (String) -> Unit) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf(u.def("60", Kind.LEN)) }
    val lws = remember { mutableStateListOf(mutableStateOf(u.def("40", Kind.LEN))) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    var end by remember { mutableStateOf(EndType.CLOSED_GROUND) }
    val lmaxS = remember { mutableStateOf("") }
    var peened by remember { mutableStateOf(false) }
    val treat = remember { TreatState() }
    val ntar = remember { mutableStateOf("") }; val temp = remember { mutableStateOf("") }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l0 to Kind.LEN, lmaxS to Kind.LEN) + lws.map { it to Kind.LEN })
    SideEffect {
        io.reg(0, {
            treat.putTo(JSONObject()).put("mat", Material.all.indexOf(mat)).put("end", end.key()).put("peen", peened)
                .putN("d", d, u, Kind.LEN).putN("od", od, u, Kind.LEN).putN("na", na, u, Kind.NONE).putN("l0", l0, u, Kind.LEN)
                .putList("lw", lws, u, Kind.LEN).putN("lmax", lmaxS, u, Kind.LEN).putN("ntar", ntar, u, Kind.NONE).putN("temp", temp, u, Kind.NONE)
        }) { o ->
            mat = matOf(o.optInt("mat")); end = endFromKey(o.optString("end")); peened = o.optBoolean("peen")
            o.getN("d", d, u, Kind.LEN); o.getN("od", od, u, Kind.LEN); o.getN("na", na, u, Kind.NONE); o.getN("l0", l0, u, Kind.LEN)
            o.getList("lw", lws, u, Kind.LEN); o.getN("lmax", lmaxS, u, Kind.LEN); o.getN("ntar", ntar, u, Kind.NONE); o.getN("temp", temp, u, Kind.NONE)
            treat.loadFrom(o)
        }
    }
    Hint()
    CompressionDiagram()
    MaterialPicker(u, mat) { mat = it }
    Dropdown("端部型式", EndType.entries, end, { it.label }) { end = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長 L0", u.len, l0) })
    MultiInput("工作長度 L", u.len, lws, "新增工作長度")
    NumField("壽命測試按壓回覆位置（循環最大長度，空白＝自由長）", u.len, lmaxS)
    RelaxFields(ntar, temp)
    PeenedSwitch(peened) { peened = it }
    TreatFields(Material.all.indexOf(mat), treat)
    DrawingButton(onDrawing) {
        treat.putTo(JSONObject()).put("type", 0).put("imp", u == Units.IMPERIAL).put("mat", Material.all.indexOf(mat)).put("peen", peened).put("end", end.key())
            .put("d", d.si(u, Kind.LEN)).put("od", od.si(u, Kind.LEN)).put("na", num(na)!!).put("l0", l0.si(u, Kind.LEN))
            .put("lw", JSONArray(lws.mapNotNull { it.value.toDoubleOrNull()?.let { x -> u.toSi(x, Kind.LEN) } }))
            .put("lmax", lmaxS.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) } ?: 0.0).toString()
    }
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
            val rx = relaxInfo(u, Material.all.indexOf(mat), 0, r.stress / fat.sut, fat.cycles, fat.belowThousand, r.load, l0Si - lwSi, ntar.value, temp.value)
            val extraW = fat.warnings + sWarn("工作時", str) +
                (if (lwSi > lMaxSi) listOf("工作長度大於回彈長度，循環定義不合理") else emptyList()) + rx.second
            ResultCard(listOf(
                "壓縮量 (L0−L)" to "${f(u.lenOut(l0Si - lwSi), 3)} ${u.len}",
                "工作負荷 F" to "${f(u.forceOut(r.load))} ${u.force}",
                "工作剪應力 τmax" to "${f(u.stressOut(r.stress), 1)} ${u.stress}",
                "回彈剪應力 τmin" to "${f(u.stressOut(tauMin), 1)} ${u.stress}",
                sRow("強度判定 τ/Sut", str),
                "靜態安全係數" to sf(fat.staticSf),
                "疲勞安全係數 (10⁷)" to sf(fat.fatigueSf),
                "預估壽命" to lifeText(fat.cycles, fat.belowThousand, "無限壽命 (>10⁷ 次)"),
            ) + rx.first, r.warnings.filter { it.startsWith("工作長度") } + extraW, title = "L${i + 1} = ${f(lw, 2)} ${u.len}")
        }
    }
    TreatCard(Material.all.indexOf(mat), treat)
}

// ================= 扭力 =================
@Composable
fun TorsionScreen(u: Units, io: FileIo, onDrawing: (String) -> Unit) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val nb = remember { mutableStateOf("6") }; val l1 = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    val l2 = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    val angs = remember { mutableStateListOf(mutableStateOf("90")) }
    val arm = remember { mutableStateOf(u.def("30", Kind.LEN)) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    val angMin = remember { mutableStateOf("0") }
    val fa = remember { mutableStateOf("180") }
    var hand by remember { mutableStateOf(0) }
    val ntar = remember { mutableStateOf("") }; val temp = remember { mutableStateOf("") }
    var peened by remember { mutableStateOf(false) }
    val treat = remember { TreatState() }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l1 to Kind.LEN, l2 to Kind.LEN, arm to Kind.LEN))
    SideEffect {
        io.reg(1, {
            treat.putTo(JSONObject()).put("mat", Material.all.indexOf(mat)).put("hand", hand).put("peen", peened)
                .putN("d", d, u, Kind.LEN).putN("od", od, u, Kind.LEN).putN("nb", nb, u, Kind.NONE).putN("arm", arm, u, Kind.LEN)
                .putN("l1", l1, u, Kind.LEN).putN("l2", l2, u, Kind.LEN).putN("fa", fa, u, Kind.NONE).putList("ang", angs, u, Kind.NONE)
                .putN("amin", angMin, u, Kind.NONE).putN("ntar", ntar, u, Kind.NONE).putN("temp", temp, u, Kind.NONE)
        }) { o ->
            mat = matOf(o.optInt("mat")); hand = o.optInt("hand"); peened = o.optBoolean("peen")
            o.getN("d", d, u, Kind.LEN); o.getN("od", od, u, Kind.LEN); o.getN("nb", nb, u, Kind.NONE); o.getN("arm", arm, u, Kind.LEN)
            o.getN("l1", l1, u, Kind.LEN); o.getN("l2", l2, u, Kind.LEN); o.getN("fa", fa, u, Kind.NONE); o.getList("ang", angs, u, Kind.NONE)
            o.getN("amin", angMin, u, Kind.NONE); o.getN("ntar", ntar, u, Kind.NONE); o.getN("temp", temp, u, Kind.NONE)
            treat.loadFrom(o)
        }
    }
    Hint()
    TorsionDiagram()
    MaterialPicker(u, mat) { mat = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("本體圈數 N", "圈", nb) }, { NumField("受力點半徑 R", u.len, arm) })
    Row2({ NumField("臂長 L1", u.len, l1) }, { NumField("臂長 L2", u.len, l2) })
    NumField("自由角度 α0（自由狀態兩腳夾角）", "度", fa)
    Dropdown("旋向（沿軸線方向看的纏繞方向；扭緊時兩腳夾角變小）", listOf(0, 1), hand, { if (it == 0) "右旋（右手螺旋）" else "左旋（左手螺旋）" }) { hand = it }
    MultiInput("扭轉角 θ", "度", angs, "新增扭轉角")
    NumField("壽命測試按壓回覆的位置角度（預載，0＝完全放鬆）", "度", angMin)
    RelaxFields(ntar, temp)
    PeenedSwitch(peened) { peened = it }
    TreatFields(Material.all.indexOf(mat), treat)
    DrawingButton(onDrawing) {
        treat.putTo(JSONObject()).put("fa", fa.value.toDoubleOrNull() ?: 180.0).put("hand", hand).put("type", 1).put("imp", u == Units.IMPERIAL).put("mat", Material.all.indexOf(mat)).put("peen", peened)
            .put("d", d.si(u, Kind.LEN)).put("od", od.si(u, Kind.LEN)).put("nb", num(nb)!!).put("l1", l1.si(u, Kind.LEN)).put("l2", l2.si(u, Kind.LEN))
            .put("armR", arm.si(u, Kind.LEN)).put("ang", JSONArray(angs.mapNotNull { it.value.toDoubleOrNull() }))
            .put("amin", angMin.value.toDoubleOrNull() ?: 0.0).toString()
    }
    val dSi = runCatching { d.si(u, Kind.LEN) }.getOrNull()
    fun calc(angle: Double) = SpringCalc.torsion(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(nb)!!,
        l1.si(u, Kind.LEN), l2.si(u, Kind.LEN), angle, arm.si(u, Kind.LEN), mat)
    val base = runCatching { calc(0.0) }.getOrNull()
    if (base != null && dSi != null) {
        val sut = mat.sut(dSi)
        // 自由角度 α0：工作時夾角 = α0 − θ。切線腳時，N 的小數圈 f 對應 α0 = 180° − 360°×f（以 360° 為週期）
        val faV = fa.value.toDoubleOrNull() ?: 180.0
        val nbV = num(nb) ?: 0.0
        fun mod360(x: Double) = ((x % 360) + 360) % 360
        val natFa = mod360(180 - 360 * (nbV - Math.floor(nbV)))
        val dFa = Math.abs(mod360(faV - natFa + 180) - 180)
        val faW = mutableListOf<String>()
        if (faV < 0 || faV > 360) faW.add("自由角度 α0 應在 0～360 度之間")
        else if (dFa > 1) {
            val fT = (((180 - faV) / 360) % 1 + 1) % 1
            val nEff = Math.floor(nbV - fT + .5) + fT
            faW.add("兩腳要與線圈相切，N 的小數圈需對應自由角度 α0=${f(faV, 1)}°（小數 ${f(fT, 2)} 圈）。你輸入的 N=${f(nbV, 2)} 對應 ${f(natFa, 1)}°。" +
                "3D 模型與工程圖會改用 N'=${f(nEff, 2)}；本頁的剛度與應力仍依輸入的 N 計算，建議把 N 改成 ${f(nEff, 2)}。")
        }
        ResultCard(listOf(
            "中徑 D" to "${f(u.lenOut(base.dm), 3)} ${u.len}", "旋繞比 C" to f(base.c), "等效圈數 Na" to f(base.na, 2),
            "扭力常數" to "${f(u.torqueOut(base.ratePerDeg), 3)} ${u.torqueRate}",
            "抗拉強度 Sut" to "${f(u.stressOut(sut), 0)} ${u.stress}",
            "自由角度 α0（兩腳夾角）" to "${f(faV, 1)} 度",
        ), base.warnings + faW, title = "彈簧基本參數")
        val aMin = angMin.value.toDoubleOrNull() ?: 0.0
        angs.forEachIndexed { i, st ->
            val angle = st.value.toDoubleOrNull() ?: return@forEachIndexed
            val r = runCatching { calc(angle) }.getOrNull() ?: return@forEachIndexed
            val sigMin = if (angle > 0) r.stress * aMin / angle else 0.0
            val fat = SpringCalc.fatigueBending(r.stress, sigMin, dSi, mat, peened)
            val str = SpringCalc.strengthBending(r.stress, sut)
            val rx = relaxInfo(u, Material.all.indexOf(mat), 1, r.stress / sut * 0.45 / 0.65, fat.cycles, fat.belowThousand, r.force, angle, ntar.value, temp.value)
            val extraW = fat.warnings + sWarn("工作時", str) +
                (if (aMin >= angle) listOf("最小角度需小於工作扭轉角") else emptyList()) + rx.second
            ResultCard(listOf(
                "工作時兩腳夾角（α0－θ）" to "${f(faV - angle, 1)} 度",
                "扭矩 M" to "${f(u.torqueOut(r.torque), 2)} ${u.torque}",
                "受力點負荷 F" to "${f(u.forceOut(r.force))} ${u.force}",
                "彎曲應力 σmax" to "${f(u.stressOut(r.stress), 1)} ${u.stress}",
                "最小彎曲應力 σmin" to "${f(u.stressOut(sigMin), 1)} ${u.stress}",
                sRow("強度判定 σ/Sut", str),
                "扭緊後外徑" to "${f(u.lenOut(r.closedDia), 3)} ${u.len}",
                "靜態安全係數" to sf(fat.staticSf),
                "疲勞安全係數 (10⁶)" to sf(fat.fatigueSf),
                "預估壽命" to lifeText(fat.cycles, fat.belowThousand, "無限壽命 (>10⁶ 次)"),
            ) + rx.first, r.warnings + extraW, title = "θ${i + 1} = ${f(angle, 1)}°")
        }
    }
    TreatCard(Material.all.indexOf(mat), treat)
}

// ================= 拉伸 =================
@Composable
fun ExtensionScreen(u: Units, io: FileIo, onDrawing: (String) -> Unit) {
    val d = remember { mutableStateOf(u.def("2", Kind.LEN)) }; val od = remember { mutableStateOf(u.def("16", Kind.LEN)) }
    val na = remember { mutableStateOf("8") }; val l0 = remember { mutableStateOf(u.def("60", Kind.LEN)) }
    val lws = remember { mutableStateListOf(mutableStateOf(u.def("80", Kind.LEN))) }
    val fi = remember { mutableStateOf("") }
    var fiAuto by remember { mutableStateOf(true) }
    val fm = remember { mutableStateOf("") }
    var fmAuto by remember { mutableStateOf(true) }
    val r1 = remember { mutableStateOf(u.def("7", Kind.LEN)) }; val r2 = remember { mutableStateOf(u.def("4", Kind.LEN)) }
    val lminS = remember { mutableStateOf("") }
    var peened by remember { mutableStateOf(false) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    var hook by remember { mutableStateOf(0) }
    val ntar = remember { mutableStateOf("") }; val temp = remember { mutableStateOf("") }
    val treat = remember { TreatState() }
    UnitSync(u, listOf(d to Kind.LEN, od to Kind.LEN, l0 to Kind.LEN, lminS to Kind.LEN,
        fi to Kind.FORCE, fm to Kind.FORCE, r1 to Kind.LEN, r2 to Kind.LEN) + lws.map { it to Kind.LEN })
    SideEffect {
        io.reg(2, {
            treat.putTo(JSONObject()).put("mat", Material.all.indexOf(mat)).put("hook", hook).put("peen", peened)
                .put("fiAuto", fiAuto).put("fmAuto", fmAuto)
                .putN("d", d, u, Kind.LEN).putN("od", od, u, Kind.LEN).putN("na", na, u, Kind.NONE).putN("l0", l0, u, Kind.LEN)
                .putN("fi", fi, u, Kind.FORCE).putN("fm", fm, u, Kind.FORCE).putN("r1", r1, u, Kind.LEN).putN("r2", r2, u, Kind.LEN)
                .putList("lw", lws, u, Kind.LEN).putN("lmin", lminS, u, Kind.LEN).putN("ntar", ntar, u, Kind.NONE).putN("temp", temp, u, Kind.NONE)
        }) { o ->
            mat = matOf(o.optInt("mat")); hook = o.optInt("hook"); peened = o.optBoolean("peen")
            fiAuto = o.optBoolean("fiAuto"); fmAuto = o.optBoolean("fmAuto")
            o.getN("d", d, u, Kind.LEN); o.getN("od", od, u, Kind.LEN); o.getN("na", na, u, Kind.NONE); o.getN("l0", l0, u, Kind.LEN)
            o.getN("fi", fi, u, Kind.FORCE); o.getN("fm", fm, u, Kind.FORCE); o.getN("r1", r1, u, Kind.LEN); o.getN("r2", r2, u, Kind.LEN)
            o.getList("lw", lws, u, Kind.LEN); o.getN("lmin", lminS, u, Kind.LEN); o.getN("ntar", ntar, u, Kind.NONE); o.getN("temp", temp, u, Kind.NONE)
            treat.loadFrom(o)
        }
    }
    // 初張力：預設依線徑與外徑自動計算；使用者可手動修改，按「自動」隨時恢復
    val dSi = d.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) }
    val odSi = od.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) }
    val autoFi = if (dSi != null && odSi != null && dSi > 0 && odSi > dSi) SpringCalc.initialTension(dSi, odSi - dSi) else null
    LaunchedEffect(fiAuto, autoFi, u) {
        if (fiAuto && autoFi != null)
            fi.value = "%.3f".format(u.fromSi(autoFi.fi, Kind.FORCE)).trimEnd('0').trimEnd('.')
    }
    // 最大負荷：預設取「最大工作長度」對應的負荷（可手動修改，按「自動」恢復）
    val naV = na.value.toDoubleOrNull(); val l0V = l0.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) }
    val fiV = fi.value.toDoubleOrNull()?.let { u.toSi(it, Kind.FORCE) }
    val r1V = r1.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) }; val r2V = r2.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) }
    val lMaxV = lws.mapNotNull { it.value.toDoubleOrNull() }.maxOrNull()?.let { u.toSi(it, Kind.LEN) }
    val kV = if (dSi != null && odSi != null && naV != null && naV > 0 && odSi > dSi && dSi > 0) SpringCalc.rate(mat.g, dSi, odSi - dSi, naV) else null
    val autoFm = if (kV != null && l0V != null && fiV != null) fiV + kV * ((lMaxV ?: l0V) - l0V).coerceAtLeast(0.0) else null
    val allow = if (kV != null && l0V != null && fiV != null && r1V != null && r2V != null && dSi != null && odSi != null)
        SpringCalc.allowableLoad(dSi, odSi - dSi, kV, l0V, fiV, mat.sut(dSi), r1V, r2V) else null
    LaunchedEffect(fmAuto, autoFm, u) {
        if (fmAuto && autoFm != null) fm.value = "%.3f".format(u.fromSi(autoFm, Kind.FORCE)).trimEnd('0').trimEnd('.')
    }
    Hint()
    ExtensionDiagram()
    MaterialPicker(u, mat) { mat = it }
    Row2({ NumField("線徑 d", u.len, d) }, { NumField("外徑 OD", u.len, od) })
    Row2({ NumField("有效圈數 Na", "圈", na) }, { NumField("自由長(鉤內) L0", u.len, l0) })
    Row2({ NumField("初張力 Fi", u.force, fi) { fiAuto = false } }, { NumField("最大負荷", u.force, fm) { fmAuto = false } })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { fiAuto = true }, enabled = !fiAuto) { Text("自動") }
        Text(
            if (autoFi == null) "初張力：請先輸入線徑與外徑"
            else "初張力：${if (fiAuto) "自動計算" else "手動輸入"}｜建議範圍 ${f(u.forceOut(autoFi.fiLo), 2)}～${f(u.forceOut(autoFi.fiHi), 2)} ${u.force}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { fmAuto = true }, enabled = !fmAuto) { Text("自動") }
        Text(
            "最大負荷：${if (fmAuto) "自動（依最大工作長度）" else "手動輸入"}" +
                (allow?.let { "｜材質容許上限 ${f(u.forceOut(it.f), 2)} ${u.force}（${it.governing}控制，對應 L≈${f(u.lenOut(it.lMax), 2)} ${u.len}）" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row2({ NumField("鉤環中心半徑 r1", u.len, r1) }, { NumField("鉤側彎半徑 r2", u.len, r2) })
    Dropdown("鉤型（只影響 3D 與工程圖）", listOf(0, 1), hook, { if (it == 0) "長開口鉤" else "圓環鉤" }) { hook = it }
    Text("兩種鉤的上下兩鉤都共面（平行）；圓環鉤＝環立在線圈端、連接線橫越線圈中央。", style = MaterialTheme.typography.bodySmall)
    MultiInput("工作長度 L", u.len, lws, "新增工作長度")
    NumField("壽命測試按壓回覆位置（空白＝自由長，此時負荷＝初張力）", u.len, lminS)
    RelaxFields(ntar, temp)
    PeenedSwitch(peened) { peened = it }
    TreatFields(Material.all.indexOf(mat), treat)
    DrawingButton(onDrawing) {
        treat.putTo(JSONObject()).put("hook", hook).put("type", 2).put("imp", u == Units.IMPERIAL).put("mat", Material.all.indexOf(mat)).put("peen", peened)
            .put("d", d.si(u, Kind.LEN)).put("od", od.si(u, Kind.LEN)).put("na", num(na)!!).put("l0", l0.si(u, Kind.LEN))
            .put("fi", fi.si(u, Kind.FORCE)).put("fm", fm.si(u, Kind.FORCE)).put("r1", r1.si(u, Kind.LEN)).put("r2", r2.si(u, Kind.LEN))
            .put("lw", JSONArray(lws.mapNotNull { it.value.toDoubleOrNull()?.let { x -> u.toSi(x, Kind.LEN) } }))
            .put("lmin", lminS.value.toDoubleOrNull()?.let { u.toSi(it, Kind.LEN) } ?: 0.0).toString()
    }
    fun calc(lw: Double) = SpringCalc.extension(d.si(u, Kind.LEN), od.si(u, Kind.LEN), num(na)!!,
        l0.si(u, Kind.LEN), lw, fi.si(u, Kind.FORCE), fm.si(u, Kind.FORCE), mat,
        r1.si(u, Kind.LEN), r2.si(u, Kind.LEN))
    val base = runCatching { calc(l0.si(u, Kind.LEN)) }.getOrNull()
    if (base != null) {
        val dSi = d.si(u, Kind.LEN); val sut = mat.sut(dSi)
        val allowRows = allow?.let {
            listOf(
                "容許最大負荷（${it.governing}控制）" to "${f(u.forceOut(it.f), 2)} ${u.force}",
                "　對應最大工作長度" to "${f(u.lenOut(it.lMax), 2)} ${u.len}",
            )
        } ?: emptyList()
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
        ) + allowRows, base.warnings.filter { !it.startsWith("工作長度") && !it.startsWith("負荷小於") } +
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
            val rx = relaxInfo(u, Material.all.indexOf(mat), 2, r.stress / sut, ef.cycles, ef.belowThousand, r.load, r.load / r.k, ntar.value, temp.value)
            ResultCard(listOf(
                "工作負荷 F" to "${f(u.forceOut(r.load))} ${u.force}",
                "本體剪應力 τ" to "${f(u.stressOut(r.stress), 1)} ${u.stress}", sRow("　判定 τ/Sut", bS),
                "鉤環彎曲應力" to "${f(u.stressOut(r.hookBending), 1)} ${u.stress}", sRow("　判定 σ/Sut", hS),
                "鉤側彎扭轉應力" to "${f(u.stressOut(r.hookTorsion), 1)} ${u.stress}", sRow("　判定 τ/Sut", tS),
                "壽命：本體" to lifeText(ef.body.cycles, ef.body.belowThousand, "無限 (>10⁷ 次)"),
                "壽命：鉤環彎曲" to lifeText(ef.hookBend.cycles, ef.hookBend.belowThousand, "無限 (>10⁶ 次)"),
                "壽命：鉤側彎扭轉" to lifeText(ef.hookTors.cycles, ef.hookTors.belowThousand, "無限 (>10⁷ 次)"),
                "預估壽命（取最短：${ef.governing}）" to lifeText(ef.cycles, ef.belowThousand, "無限壽命"),
            ) + rx.first, r.warnings.filter { it.startsWith("工作長度") || it.startsWith("負荷小於") } + ef.warnings +
                sWarn("本體", bS) + sWarn("鉤環彎曲", hS) + sWarn("鉤側彎", tS) + rx.second,
                title = "L${i + 1} = ${f(lw, 2)} ${u.len}")
        }
    }
    TreatCard(Material.all.indexOf(mat), treat)
}

// ================= 規格設計 =================
private fun matIdxOf(m: Material) = Material.all.indexOf(m)

private fun strengthOf(x: Double, bending: Boolean) = Strength(x, if (bending)
    when { x <= .65 -> Verdict.OK; x <= .78 -> Verdict.CAUTION; else -> Verdict.FAIL }
else when { x <= .45 -> Verdict.OK; x <= .65 -> Verdict.CAUTION; else -> Verdict.FAIL })

@Composable
fun DesignScreen(u: Units, io: FileIo, onDrawing: (String) -> Unit) {
    var type by rememberSaveable { mutableStateOf(0) }
    var mat by remember { mutableStateOf(Material.all[0]) }
    var end by remember { mutableStateOf(EndType.CLOSED_GROUND) }
    var peened by remember { mutableStateOf(false) }
    fun len(mm: String) = u.def(mm, Kind.LEN)
    fun frc(n: String) = u.def(n, Kind.FORCE)
    val odMax = remember { mutableStateOf(len("30")) }
    val cId = remember { mutableStateOf(len("10")) }; val cL0 = remember { mutableStateOf("") }
    val cL1 = remember { mutableStateOf(len("50")) }; val cF1 = remember { mutableStateOf(frc("10")) }
    val cL2 = remember { mutableStateOf(len("35")) }; val cF2 = remember { mutableStateOf(frc("60")) }
    val eL1 = remember { mutableStateOf(len("60")) }; val eF1 = remember { mutableStateOf(frc("15")) }
    val eL2 = remember { mutableStateOf(len("80")) }; val eF2 = remember { mutableStateOf(frc("55")) }
    val eHook = remember { mutableStateOf("1") }
    val tShaft = remember { mutableStateOf(len("8")) }; val tLen = remember { mutableStateOf(len("30")) }
    val tR = remember { mutableStateOf(len("30")) }; val tA1 = remember { mutableStateOf(len("30")) }; val tA2 = remember { mutableStateOf(len("30")) }
    val tTh1 = remember { mutableStateOf("0") }; val tF1 = remember { mutableStateOf(frc("0")) }
    val tTh2 = remember { mutableStateOf("90") }; val tF2 = remember { mutableStateOf(frc("10")) }
    val tFa = remember { mutableStateOf("180") }
    val treat = remember { TreatState() }
    val nTargetS = remember { mutableStateOf("100000") }; val tolS = remember { mutableStateOf("5") }
    val tempS = remember { mutableStateOf("") }
    UnitSync(u, listOf(odMax to Kind.LEN, cId to Kind.LEN, cL0 to Kind.LEN, cL1 to Kind.LEN, cF1 to Kind.FORCE, cL2 to Kind.LEN, cF2 to Kind.FORCE,
        eL1 to Kind.LEN, eF1 to Kind.FORCE, eL2 to Kind.LEN, eF2 to Kind.FORCE, tShaft to Kind.LEN, tLen to Kind.LEN, tR to Kind.LEN,
        tA1 to Kind.LEN, tA2 to Kind.LEN, tF1 to Kind.FORCE, tF2 to Kind.FORCE))
    SideEffect {
        io.reg(3, {
            treat.putTo(JSONObject()).put("type", type).put("mat", Material.all.indexOf(mat)).put("c_end", end.key()).put("peen", peened)
                .putN("odMax", odMax, u, Kind.LEN).putN("c_idMin", cId, u, Kind.LEN).putN("c_l0Max", cL0, u, Kind.LEN)
                .putN("c_l1", cL1, u, Kind.LEN).putN("c_f1", cF1, u, Kind.FORCE).putN("c_l2", cL2, u, Kind.LEN).putN("c_f2", cF2, u, Kind.FORCE)
                .putN("e_l1", eL1, u, Kind.LEN).putN("e_f1", eF1, u, Kind.FORCE).putN("e_l2", eL2, u, Kind.LEN).putN("e_f2", eF2, u, Kind.FORCE)
                .putN("e_hook", eHook, u, Kind.NONE).putN("t_shaft", tShaft, u, Kind.LEN).putN("t_len", tLen, u, Kind.LEN)
                .putN("t_R", tR, u, Kind.LEN).putN("t_a1", tA1, u, Kind.LEN).putN("t_a2", tA2, u, Kind.LEN)
                .putN("t_th1", tTh1, u, Kind.NONE).putN("t_f1", tF1, u, Kind.FORCE).putN("t_th2", tTh2, u, Kind.NONE).putN("t_f2", tF2, u, Kind.FORCE)
                .putN("t_fa", tFa, u, Kind.NONE).putN("nTarget", nTargetS, u, Kind.NONE).putN("tol", tolS, u, Kind.NONE).putN("temp", tempS, u, Kind.NONE)
        }) { o ->
            type = o.optInt("type").coerceIn(0, 2); mat = matOf(o.optInt("mat")); end = endFromKey(o.optString("c_end")); peened = o.optBoolean("peen")
            o.getN("odMax", odMax, u, Kind.LEN); o.getN("c_idMin", cId, u, Kind.LEN); o.getN("c_l0Max", cL0, u, Kind.LEN)
            o.getN("c_l1", cL1, u, Kind.LEN); o.getN("c_f1", cF1, u, Kind.FORCE); o.getN("c_l2", cL2, u, Kind.LEN); o.getN("c_f2", cF2, u, Kind.FORCE)
            o.getN("e_l1", eL1, u, Kind.LEN); o.getN("e_f1", eF1, u, Kind.FORCE); o.getN("e_l2", eL2, u, Kind.LEN); o.getN("e_f2", eF2, u, Kind.FORCE)
            o.getN("e_hook", eHook, u, Kind.NONE); o.getN("t_shaft", tShaft, u, Kind.LEN); o.getN("t_len", tLen, u, Kind.LEN)
            o.getN("t_R", tR, u, Kind.LEN); o.getN("t_a1", tA1, u, Kind.LEN); o.getN("t_a2", tA2, u, Kind.LEN)
            o.getN("t_th1", tTh1, u, Kind.NONE); o.getN("t_f1", tF1, u, Kind.FORCE); o.getN("t_th2", tTh2, u, Kind.NONE); o.getN("t_f2", tF2, u, Kind.FORCE)
            o.getN("t_fa", tFa, u, Kind.NONE); o.getN("nTarget", nTargetS, u, Kind.NONE); o.getN("tol", tolS, u, Kind.NONE); o.getN("temp", tempS, u, Kind.NONE)
            treat.loadFrom(o)
        }
    }
    val types = listOf("壓縮彈簧", "拉伸彈簧", "扭力彈簧")
    Text("依可用空間、彈簧型式、操作位置與力量，計算建議的彈簧規格。", style = MaterialTheme.typography.bodySmall)
    DesignDiagram(type)
    Dropdown("彈簧型式", types.indices.toList(), type, { types[it] }) { type = it }
    MaterialPicker(u, mat) { mat = it }
    NumField("外徑上限（可用空間）", u.len, odMax)
    fun v(s: MutableState<String>, k: Kind) = s.value.toDoubleOrNull()?.let { u.toSi(it, k) }
    fun opt(s: MutableState<String>) = v(s, Kind.LEN)?.takeIf { it > 0 } ?: 0.0
    when (type) {
        0 -> {
            Row2({ NumField("內徑下限／桿徑（空白＝不限）", u.len, cId) }, { NumField("自由長上限（空白＝不限）", u.len, cL0) })
            Dropdown("端部型式", EndType.entries, end, { it.label }) { end = it }
            Row2({ NumField("安裝長度 L1", u.len, cL1) }, { NumField("安裝時力量 F1", u.force, cF1) })
            Row2({ NumField("工作最短長度 L2", u.len, cL2) }, { NumField("工作時力量 F2", u.force, cF2) })
        }
        1 -> {
            Row2({ NumField("安裝長度 L1（兩鉤內側）", u.len, eL1) }, { NumField("安裝時力量 F1", u.force, eF1) })
            Row2({ NumField("最大工作長度 L2", u.len, eL2) }, { NumField("L2 時力量 F2", u.force, eF2) })
            NumField("每個鉤的內側長度（× 中徑）", "倍", eHook)
            Text("鉤長係數：每個鉤占的長度 = 係數 × 彈簧中徑。自由長 L0（兩鉤內側）= (有效圈數 + 1) × 線徑 + 2 × 係數 × 中徑。一般圓環鉤用 1；長開口鉤可調大（如 1.5）、短鉤可調小（如 0.7），實際以鉤型圖面為準。", style = MaterialTheme.typography.bodySmall)
        }
        else -> {
            Row2({ NumField("軸徑（空白＝不限）", u.len, tShaft) }, { NumField("軸向長度上限（空白＝不限）", u.len, tLen) })
            Row2({ NumField("受力點半徑 R", u.len, tR) }, { NumField("臂長 L1", u.len, tA1) })
            NumField("臂長 L2", u.len, tA2)
            Row2({ NumField("安裝角度 θ1", "度", tTh1) }, { NumField("θ1 時力量", u.force, tF1) })
            Row2({ NumField("工作角度 θ2", "度", tTh2) }, { NumField("θ2 時力量", u.force, tF2) })
            NumField("自由角度 α0（兩腳夾角，用於工程圖與 3D）", "度", tFa)
        }
    }
    Row2({ NumField("目標壽命（空白＝不要求；同時用於衰減預估）", "次", nTargetS) }, { NumField("力量容許誤差", "%", tolS) })
    NumField("使用溫度（空白＝室溫 20°C）", "°C", tempS)
    PeenedSwitch(peened) { peened = it }
    TreatFields(matIdxOf(mat), treat)

    val odV = v(odMax, Kind.LEN)
    val tol = (tolS.value.toDoubleOrNull() ?: 5.0) / 100
    val nT = nTargetS.value.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
    var err: String? = null; var res: SpringCalc.DesignResult? = null
    var mk: ((SpringCalc.DesignCand) -> String)? = null
    var dzL2 = 0.0
    val imp = u == Units.IMPERIAL; val matIdx = Material.all.indexOf(mat)
    if (odV == null || odV <= 0) err = "請輸入外徑上限"
    else when (type) {
        0 -> {
            val l1 = v(cL1, Kind.LEN); val f1 = v(cF1, Kind.FORCE); val l2 = v(cL2, Kind.LEN); val f2 = v(cF2, Kind.FORCE)
            if (l1 == null || f1 == null || l2 == null || f2 == null) err = "請輸入完整的位置與力量"
            else if (!(l1 > l2 && l2 > 0 && f2 > f1 && f1 >= 0)) err = "需滿足：安裝長度 L1 > 工作長度 L2 > 0，且 F2 > F1 ≥ 0"
            else {
                dzL2 = l2
                res = SpringCalc.designCompression(SpringCalc.CompSpec(mat, end, odV, opt(cId), opt(cL0), l1, f1, l2, f2, peened, nT, tol))
                mk = { c -> treat.putTo(JSONObject()).put("type", 0).put("imp", imp).put("mat", matIdx).put("peen", peened).put("end", end.key())
                    .put("d", c.d).put("od", c.dm + c.d).put("na", c.na).put("l0", c.v["L0"] ?: 0.0).put("lw", JSONArray(listOf(l1, l2))).toString() }
            }
        }
        1 -> {
            val l1 = v(eL1, Kind.LEN); val f1 = v(eF1, Kind.FORCE); val l2 = v(eL2, Kind.LEN); val f2 = v(eF2, Kind.FORCE)
            val hf = eHook.value.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
            if (l1 == null || f1 == null || l2 == null || f2 == null) err = "請輸入完整的位置與力量"
            else if (!(l2 > l1 && l1 > 0 && f2 > f1 && f1 >= 0)) err = "需滿足：最大工作長度 L2 > 安裝長度 L1 > 0，且 F2 > F1 ≥ 0"
            else {
                res = SpringCalc.designExtension(SpringCalc.ExtSpec(mat, odV, hf, l1, f1, l2, f2, peened, nT, tol))
                mk = { c -> treat.putTo(JSONObject()).put("type", 2).put("imp", imp).put("mat", matIdx).put("peen", peened)
                    .put("d", c.d).put("od", c.dm + c.d).put("na", c.na).put("l0", c.v["L0"] ?: 0.0).put("fi", c.v["Fi"] ?: 0.0).put("fm", f2)
                    .put("r1", c.dm / 2).put("r2", 2 * c.d).put("lw", JSONArray(listOf(l2))).put("lmin", l1).toString() }
            }
        }
        else -> {
            val r = v(tR, Kind.LEN); val a1 = v(tA1, Kind.LEN); val a2 = v(tA2, Kind.LEN); val f1 = v(tF1, Kind.FORCE); val f2 = v(tF2, Kind.FORCE)
            val th1 = tTh1.value.toDoubleOrNull(); val th2 = tTh2.value.toDoubleOrNull()
            if (r == null || a1 == null || a2 == null || f1 == null || f2 == null || th1 == null || th2 == null) err = "請輸入完整的角度、力量與臂長"
            else if (!(th2 > th1 && r > 0 && f2 > f1 && f1 >= 0)) err = "需滿足：θ2 > θ1，R > 0，且 F2 > F1 ≥ 0"
            else {
                res = SpringCalc.designTorsion(SpringCalc.TorSpec(mat, odV, opt(tShaft), opt(tLen), r, a1, a2, th1, f1, th2, f2, peened, nT, tol))
                mk = { c -> val tot = c.v["thTot"] ?: 0.0
                    treat.putTo(JSONObject()).put("type", 1).put("imp", imp).put("fa", tFa.value.toDoubleOrNull() ?: 180.0).put("mat", matIdx).put("peen", peened)
                        .put("d", c.d).put("od", c.dm + c.d).put("nb", c.na).put("l1", a1).put("l2", a2).put("armR", r)
                        .put("ang", JSONArray(listOf(tot))).put("amin", maxOf(0.0, tot - (th2 - th1))).toString() }
            }
        }
    }
    if (err != null) { ResultCard(emptyList(), listOf(err), title = "請檢查輸入"); return }
    val r = res ?: return
    val kTxt = if (type == 2) "${f(u.torqueOut(r.kreq), 3)} ${u.torqueRate}" else "${f(u.rateOut(r.kreq), 3)} ${u.rate}"
    ResultCard(listOf("所需彈簧常數 k" to kTxt, "評估組合數" to "${r.total}", "完全符合條件的組合數" to "${r.pass}"),
        listOf(if (r.ok) "方案依預估壽命由最佳排到最差；每種線徑只列壽命最佳的一組。"
        else "找不到完全符合全部條件的規格，以下為最接近的方案，各卡片已標出不符項目。可放寬空間、力量誤差或目標壽命後再試。"),
        title = "設計結果摘要")
    TreatCard(matIdx, treat)
    r.cands.forEachIndexed { i, c ->
        val x = c.v; fun g(k: String) = x[k] ?: 0.0
        val title = "方案 ${i + 1}：線徑 ${f(u.lenOut(c.d), 3)} ${u.len}｜外徑 ${f(u.lenOut(g("OD")), 2)} ${u.len}"
        val rows: List<Pair<String, String>> = when (type) {
            0 -> listOf("線徑 d" to "${f(u.lenOut(c.d), 3)} ${u.len}",
                "中徑 D／旋繞比 C" to "${f(u.lenOut(c.dm), 2)} ${u.len}／${f(g("C"), 2)}", "內徑 ID" to "${f(u.lenOut(g("ID")), 2)} ${u.len}",
                "有效圈數 Na／總圈數 Nt" to "${f(c.na, 2)}／${f(g("nt"), 2)}", "自由長 L0" to "${f(u.lenOut(g("L0")), 2)} ${u.len}",
                "壓並高度 Ls" to "${f(u.lenOut(g("ls")), 2)} ${u.len}", "彈簧常數 k" to "${f(u.rateOut(g("k")), 3)} ${u.rate}",
                "F1 @L1" to "${f(u.forceOut(g("F1")), 2)} ${u.force}", "F2 @L2（實際）" to "${f(u.forceOut(g("F2")), 2)} ${u.force}",
                "壓並力 Fs" to "${f(u.forceOut(g("Fs")), 2)} ${u.force}", "工作剪應力 τ" to "${f(u.stressOut(g("tau2")), 1)} ${u.stress}",
                sRow("強度判定 τ/Sut", strengthOf(g("ratio"), false)), "疲勞安全係數" to sf(g("nf")),
                "預估壽命" to lifeText(g("N"), g("below") > 0, "無限壽命 (>10⁷ 次)"))
            1 -> listOf("線徑 d" to "${f(u.lenOut(c.d), 3)} ${u.len}",
                "中徑 D／旋繞比 C" to "${f(u.lenOut(c.dm), 2)} ${u.len}／${f(g("C"), 2)}", "有效圈數 Na" to f(c.na, 2),
                "本體長度 Lb" to "${f(u.lenOut(g("Lb")), 2)} ${u.len}", "自由長 L0（鉤內）" to "${f(u.lenOut(g("L0")), 2)} ${u.len}",
                "彈簧常數 k" to "${f(u.rateOut(g("k")), 3)} ${u.rate}",
                "初張力 Fi" to "${f(u.forceOut(g("Fi")), 2)} ${u.force}（建議 ${f(u.forceOut(g("FiLo")), 2)}～${f(u.forceOut(g("FiHi")), 2)}）",
                "F2 @L2（實際）" to "${f(u.forceOut(g("F2")), 2)} ${u.force}",
                "本體剪應力" to "${f(u.stressOut(g("tau")), 1)} ${u.stress}", sRow("　判定 τ/Sut", strengthOf(g("rB"), false)),
                "鉤環彎曲應力" to "${f(u.stressOut(g("hb")), 1)} ${u.stress}", sRow("　判定 σ/Sut", strengthOf(g("rH"), true)),
                "鉤側彎扭轉應力" to "${f(u.stressOut(g("ht")), 1)} ${u.stress}", sRow("　判定 τ/Sut", strengthOf(g("rT"), false)),
                "疲勞安全係數（取最小）" to sf(g("nf")),
                "預估壽命（取最短：${c.tag}）" to lifeText(g("N"), g("below") > 0, "無限壽命"))
            else -> listOf("線徑 d" to "${f(u.lenOut(c.d), 3)} ${u.len}",
                "中徑 D／旋繞比 C" to "${f(u.lenOut(c.dm), 2)} ${u.len}／${f(g("C"), 2)}",
                "本體圈數 N／等效圈數 Na" to "${f(c.na, 2)}／${f(g("naEq"), 2)}", "本體長度" to "${f(u.lenOut(g("body")), 2)} ${u.len}",
                "扭力常數 k" to "${f(u.torqueOut(g("k")), 3)} ${u.torqueRate}", "θ2 時扭矩（實際）" to "${f(u.torqueOut(g("M2")), 2)} ${u.torque}",
                "　相當受力點負荷" to "${f(u.forceOut(g("F2")), 2)} ${u.force}", "彎曲應力 σ" to "${f(u.stressOut(g("sigma")), 1)} ${u.stress}",
                sRow("強度判定 σ/Sut", strengthOf(g("ratio"), true)), "扭緊後內徑" to "${f(u.lenOut(g("idW")), 2)} ${u.len}",
                "預估壽命" to lifeText(g("N"), g("below") > 0, "無限壽命 (>10⁶ 次)"))
        }
        val rx = when (type) {
            0 -> relaxInfo(u, matIdx, 0, g("ratio"), g("N"), g("below") > 0, g("F2"), g("L0") - dzL2, nTargetS.value, tempS.value)
            1 -> relaxInfo(u, matIdx, 2, g("rB"), g("N"), g("below") > 0, g("F2"), g("F2") / g("k"), nTargetS.value, tempS.value)
            else -> relaxInfo(u, matIdx, 1, g("ratio") * 0.45 / 0.65, g("N"), g("below") > 0, g("F2"), g("thTot"), nTargetS.value, tempS.value)
        }
        ResultCard(rows + rx.first, c.fails.map { "不符：$it" } + c.warns + rx.second, title = title)
        mk?.let { f -> DrawingButton(onDrawing) { f(c) } }
    }
    Text("設計假設：線徑取常用規格系列，中徑以 0.25 mm 掃描，圈數取 0.25 圈的倍數。壓縮：壓並力需 ≥ 1.15×工作力、旋繞比 4～12、剪應力 ≤ 0.65Sut；" +
        "拉伸：自由長 = (Na+1)·d + 2×鉤內長度，初張力由 F1 反推且需在建議範圍，鉤環 r1 = D/2、r2 = 2d；扭力：臂長計入等效圈數，扭緊後內徑需大於軸徑、彎曲應力 ≤ 0.78Sut。" +
        "結果為初步建議，請到對應計算頁做完整驗算。", style = MaterialTheme.typography.bodySmall)
}

/** 兩位有效數字的循環次數，如 5.3×10⁵ */
private fun fmtCycles(n: Double): String {
    val e = Math.floor(Math.log10(n)).toInt()
    val m = n / Math.pow(10.0, e.toDouble())
    val sup = e.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
    return "%.1f×10%s".format(m, sup)
}
