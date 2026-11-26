package com.springs.calc.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** 表面處理與熱處理（只做資料、提示與檢核，不改動應力／壽命的計算結果）。與網頁版 index.html 的 SURF／HTS／htSuggest／treatCheck 相同。 */
object Treat {
    class Surf(val n: String, val plate: Boolean = false, val steelOnly: Boolean = false, val ssOnly: Boolean = false)

    val SURF = listOf(
        Surf("無（素材，不上油）"), Surf("上油（防鏽油）"), Surf("電鍍鋅（含鈍化）", plate = true), Surf("鋅鎳合金電鍍", plate = true),
        Surf("電鍍鎳", plate = true), Surf("電鍍鉻", plate = true), Surf("磷化（磷酸鋅／錳）", steelOnly = true), Surf("發黑（氧化處理）", steelOnly = true),
        Surf("達克羅／鋅鋁塗層"), Surf("塗裝（粉體／環氧／電著）"), Surf("PTFE／特氟龍塗層"), Surf("鈍化（不鏽鋼）", ssOnly = true),
        Surf("電解拋光（不鏽鋼）", ssOnly = true), Surf("其他（見規範欄）"),
    )
    val HTS = listOf("無", "消除應力（低溫回火）", "去氫烘烤（電鍍後）", "淬火＋回火（熱成形／大線徑）", "時效硬化（沉澱硬化材）", "氮化（表面硬化）", "其他熱處理（自訂）")

    /** 消除應力建議 [溫度°C, 時間min, 下限°C, 上限°C]，依材質順序；鉻矽鋼線為推估 */
    private val SR = listOf(listOf(230, 60, 190, 260), listOf(290, 30, 260, 320), listOf(300, 30, 230, 370), listOf(200, 60, 150, 230), listOf(315, 60, 250, 425))
    private val BAKE = listOf(200, 240, 190, 220)
    private val CARBON = listOf(0, 1, 4)

    class Sug(val t: Int, val min: Int, val lo: Int, val hi: Int, val infer: Boolean)

    fun suggest(mi: Int, hk: Int): Sug? = when (hk) {
        1 -> SR.getOrElse(mi) { SR[0] }.let { Sug(it[0], it[1], it[2], it[3], mi == 4) }
        2 -> Sug(BAKE[0], BAKE[1], BAKE[2], BAKE[3], false)
        3 -> if (mi == 4) Sug(450, 60, 370, 540, false) else Sug(430, 60, 370, 480, false)
        4 -> Sug(480, 60, 300, 570, true)
        5 -> Sug(450, 240, 400, 520, true)
        else -> null
    }

    fun fmtMin(t: Int) = if (t >= 60) "$t min（${"%.1f".format(t / 60.0)} h）" else "$t min"

    class Check(val warns: List<String>, val notes: List<String>, val sug: Sug?)

    fun check(mi: Int, surf: Int, ht: Int, T: Double?, tt: Double?): Check {
        val sf = SURF[surf]; val w = mutableListOf<String>(); val n = mutableListOf<String>()
        val okTT = T != null && tt != null && T > 0 && tt > 0
        val carbon = mi in CARBON
        if (sf.plate && carbon && ht != 2) w.add("電鍍後有氫脆風險：高強度彈簧鋼線電鍍後應盡快做去氫烘烤。請把熱處理設為「去氫烘烤」並填溫度與時間（建議值見下方）。")
        if (sf.plate) n.add("電鍍會降低疲勞壽命（建議搭配珠擊）；本程式的疲勞與壽命估算未計入表面處理的影響。")
        if (sf.steelOnly && !carbon) w.add("磷化／發黑適用碳鋼與合金鋼線，不適用於此材質。")
        if (sf.ssOnly && mi != 2) w.add("鈍化／電解拋光僅適用不鏽鋼。")
        if (ht == 2 && !sf.plate) w.add("去氫烘烤通常用在電鍍之後；目前表面處理不是電鍍類。")
        if (ht == 2 && !carbon) w.add("此材質（不鏽鋼／銅合金）通常不需去氫烘烤。")
        if (ht == 3 && mi !in listOf(1, 4)) w.add("淬火＋回火用於熱成形或大線徑的彈簧鋼；此材質為預硬化／冷拉線材，一般不需要，淬火還會改變材料狀態。")
        if (ht == 3) n.add("溫度與時間請填「回火」；淬火條件（約 790～900°C 油淬）請記在表面規範欄或備註。")
        if (ht == 4) w.add("時效硬化只適用沉澱硬化材質（例如 17-7PH 不鏽鋼、鈹銅）；本程式的材質清單沒有這類材質，請確認材質後再使用。")
        if (ht == 5 && mi !in listOf(1, 4)) w.add("氮化通常用於合金鋼線（如鉻矽鋼閥簧）；此材質一般不氮化。")
        if (ht == 5) n.add("氮化溫度不可高於線材的回火溫度，否則會軟化。")
        if (ht > 0 && !okTT) w.add("請填入熱處理的溫度與時間。")
        val s = suggest(mi, ht)
        if (okTT && s != null) {
            if (T!! < s.lo || T > s.hi) w.add("熱處理溫度 ${fmtNum(T)}°C 超出一般建議範圍 ${s.lo}～${s.hi}°C。")
            if (ht == 1 && mi == 3 && T > 230) w.add("磷青銅高於約 230°C 會軟化，建議只做輕度加熱。")
            if (ht == 2 && tt!! < 240) w.add("去氫烘烤時間偏短：一般建議至少數小時（≥4 h），確切時間依 ASTM B850 強度等級與客戶規範。")
        }
        if (ht == 1 && okTT && sf.plate) n.add("電鍍件的消除應力通常安排在電鍍前（較低溫度），電鍍後再做去氫烘烤。")
        if (ht > 0 && s != null && s.infer) n.add(if (ht == 1) "鉻矽鋼線的建議值為依同類鋼線推估，請以線材商或客戶規範為準。" else "此項建議值為依一般做法推估（未查到可靠的時間資料），請以材料或製程規範為準。")
        return Check(w, n, s)
    }

    private fun fmtNum(x: Double) = if (x == Math.floor(x)) x.toLong().toString() else x.toString()
}

/** 表面處理／熱處理的輸入狀態 */
class TreatState {
    var surf by mutableIntStateOf(0)
    val surfn = mutableStateOf("")
    var ht by mutableIntStateOf(0)
    val htT = mutableStateOf("")
    val htt = mutableStateOf("")

    fun check(mi: Int) = Treat.check(mi, surf, ht, htT.value.toDoubleOrNull(), htt.value.toDoubleOrNull())

    fun htShort(): String {
        val base = Treat.HTS[ht].replace(Regex("（.*?）"), "")
        val t = htT.value.toDoubleOrNull(); val m = htt.value.toDoubleOrNull()
        return if (ht > 0 && t != null && m != null) "$base ${fmtD(t)}°C×${fmtD(m)}min" else if (ht > 0) "$base（未填）" else base
    }

    private fun fmtD(x: Double) = if (x == Math.floor(x)) x.toLong().toString() else x.toString()

    fun htText(): String {
        val t = htT.value.toDoubleOrNull(); val m = htt.value.toDoubleOrNull()
        return Treat.HTS[ht] + if (ht > 0 && t != null && m != null) " ${fmtD(t)}°C × ${Treat.fmtMin(m.toInt())}" else if (ht > 0) "（未填溫度／時間）" else ""
    }

    /** 由規格檔還原（欄位名同 putTo） */
    fun loadFrom(o: JSONObject) {
        if (o.has("surf")) surf = o.optInt("surf").coerceIn(0, Treat.SURF.size - 1)
        if (o.has("surfn")) surfn.value = o.optString("surfn", "")
        if (o.has("ht")) ht = o.optInt("ht").coerceIn(0, Treat.HTS.size - 1)
        htT.value = if (o.has("htT") && !o.isNull("htT")) fmtPlain(o.optDouble("htT")) else ""
        htt.value = if (o.has("htt") && !o.isNull("htt")) fmtPlain(o.optDouble("htt")) else ""
    }

    /** 寫入工程圖／3D 用的 JSON 欄位（欄位名同網頁版） */
    fun putTo(o: JSONObject): JSONObject {
        o.put("surf", surf).put("surfn", surfn.value.trim()).put("ht", ht)
        htT.value.toDoubleOrNull()?.let { o.put("htT", it) }
        htt.value.toDoubleOrNull()?.let { o.put("htt", it) }
        return o
    }
}

@Composable
fun TreatFields(mi: Int, st: TreatState) {
    val ctx = LocalContext.current
    Dropdown("表面處理", Treat.SURF.indices.toList(), st.surf, { Treat.SURF[it].n }) { st.surf = it }
    OutlinedTextField(
        value = st.surfn.value, onValueChange = { st.surfn.value = it }, singleLine = true,
        label = { Text("表面規範／厚度（選填，例：ASTM B633 Fe/Zn 8，8~12 µm）") }, modifier = Modifier.fillMaxWidth(),
    )
    Dropdown("熱處理", Treat.HTS.indices.toList(), st.ht, { Treat.HTS[it] }) { st.ht = it }
    Row2({ NumField("熱處理溫度", "°C", st.htT) }, { NumField("熱處理時間", "分鐘", st.htt) })
    val sug = Treat.suggest(mi, st.ht)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            if (sug != null) { st.htT.value = sug.t.toString(); st.htt.value = sug.min.toString() }
            else Toast.makeText(ctx, "請先在「熱處理」選擇「消除應力」或「去氫烘烤」，才有建議值", Toast.LENGTH_SHORT).show()
        }) { Text("套用建議值") }
        if (sug != null) Text(
            "建議：${sug.t}°C、${Treat.fmtMin(sug.min)}（範圍 ${sug.lo}～${sug.hi}°C）${if (sug.infer) "（推估）" else ""}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 「表面處理與熱處理」結果卡片（含檢核提示） */
@Composable
fun TreatCard(mi: Int, st: TreatState) {
    val c = st.check(mi)
    val rows = mutableListOf("表面處理" to Treat.SURF[st.surf].n)
    if (st.surfn.value.isNotBlank()) rows.add("表面規範" to st.surfn.value.trim())
    rows.add("熱處理" to st.htText())
    ResultCard(rows, c.warns, title = "表面處理與熱處理")
    if (c.notes.isNotEmpty()) Column { c.notes.forEach { Text("・$it", style = MaterialTheme.typography.bodySmall) } }
}
