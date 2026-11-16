package com.springs.calc.ui

import androidx.compose.runtime.*

enum class Kind { LEN, FORCE, NONE }

/** 內部計算一律用 SI（mm、N、MPa、N·mm）；此處只負責顯示與輸入換算。 */
enum class Units(val label: String, val len: String, val force: String, val stress: String,
                 val rate: String, val torque: String, val torqueRate: String) {
    METRIC("公制", "mm", "N", "MPa", "N/mm", "N·mm", "N·mm/°"),
    IMPERIAL("英制", "in", "lbf", "psi", "lbf/in", "lbf·in", "lbf·in/°");

    private val imp get() = this == IMPERIAL
    private val lenF get() = if (imp) 25.4 else 1.0            // 單位 → mm
    private val forceF get() = if (imp) 4.4482216 else 1.0     // 單位 → N
    private val stressF get() = if (imp) 1 / 145.0377 else 1.0 // 單位 → MPa

    fun unit(k: Kind) = when (k) { Kind.LEN -> len; Kind.FORCE -> force; Kind.NONE -> "" }
    private fun factor(k: Kind) = when (k) { Kind.LEN -> lenF; Kind.FORCE -> forceF; Kind.NONE -> 1.0 }

    fun toSi(v: Double, k: Kind) = v * factor(k)
    fun fromSi(v: Double, k: Kind) = v / factor(k)
    fun stressOut(mpa: Double) = mpa / stressF
    fun rateOut(nPerMm: Double) = nPerMm / (forceF / lenF)
    fun torqueOut(nmm: Double) = nmm / (forceF * lenF)
    fun lenOut(mm: Double) = fromSi(mm, Kind.LEN)
    fun forceOut(n: Double) = fromSi(n, Kind.FORCE)
    /** 預設值以公制給定，依目前單位換算。 */
    fun def(mm: String, k: Kind) = fromSi(mm.toDouble(), k).let { "%.4f".format(it).trimEnd('0').trimEnd('.') }

    fun modulus(mpa: Double) = if (imp) "%.2f×10⁶ psi".format(mpa / stressF / 1e6) else "${mpa.toInt()} MPa"
}

/** 切換單位時，把欄位內現有數值換算到新單位。 */
@Composable
fun UnitSync(units: Units, fields: List<Pair<MutableState<String>, Kind>>) {
    var prev by remember { mutableStateOf(units) }
    LaunchedEffect(units) {
        if (prev != units) {
            fields.forEach { (s, k) ->
                s.value.toDoubleOrNull()?.let {
                    val v = units.fromSi(prev.toSi(it, k), k)
                    s.value = "%.4f".format(v).trimEnd('0').trimEnd('.')
                }
            }
            prev = units
        }
    }
}
