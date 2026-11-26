package com.springs.calc.ui

import androidx.compose.runtime.*
import com.springs.calc.EndType
import com.springs.calc.Material
import org.json.JSONArray
import org.json.JSONObject

/** 規格檔存取：各分頁把「讀取目前輸入／套用檔案內容」登錄在這裡。檔案為 JSON，數值一律以 SI 單位存放，與網頁版通用。 */
class FileIo {
    val snaps = mutableMapOf<Int, () -> JSONObject>()
    val loads = mutableMapOf<Int, (JSONObject) -> Unit>()
    /** 目前檔名（不含副檔名）；存檔或開啟過才有，沒有時出圖 PDF 要請使用者輸入 */
    var name by mutableStateOf<String?>(null)
    fun reg(tab: Int, snap: () -> JSONObject, load: (JSONObject) -> Unit) { snaps[tab] = snap; loads[tab] = load }
}

fun JSONObject.putN(key: String, st: MutableState<String>, u: Units, k: Kind): JSONObject {
    val v = st.value.toDoubleOrNull()
    return if (v == null) put(key, JSONObject.NULL) else put(key, if (k == Kind.NONE) v else u.toSi(v, k))
}

fun JSONObject.getN(key: String, st: MutableState<String>, u: Units, k: Kind) {
    if (!has(key)) return
    st.value = if (isNull(key)) "" else if (k == Kind.NONE) fmtPlain(optDouble(key)) else u.def(optDouble(key).toString(), k)
}

fun JSONObject.putList(key: String, items: List<MutableState<String>>, u: Units, k: Kind): JSONObject {
    val a = JSONArray()
    items.forEach { st -> val v = st.value.toDoubleOrNull(); if (v == null) a.put(JSONObject.NULL) else a.put(if (k == Kind.NONE) v else u.toSi(v, k)) }
    return put(key, a)
}

fun JSONObject.getList(key: String, items: androidx.compose.runtime.snapshots.SnapshotStateList<MutableState<String>>, u: Units, k: Kind) {
    val a = optJSONArray(key) ?: return
    items.clear()
    for (i in 0 until a.length()) items.add(mutableStateOf(if (a.isNull(i)) "" else if (k == Kind.NONE) fmtPlain(a.optDouble(i)) else u.def(a.optDouble(i).toString(), k)))
    if (items.isEmpty()) items.add(mutableStateOf(""))
}

fun fmtPlain(x: Double) = "%.6f".format(x).trimEnd('0').trimEnd('.')

fun endFromKey(k: String?) = EndType.entries.firstOrNull { it.key() == k } ?: EndType.CLOSED_GROUND

fun matOf(i: Int) = Material.all.getOrElse(i) { Material.all[0] }
