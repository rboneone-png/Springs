package com.springs.calc

import kotlin.math.PI
import kotlin.math.pow

/** 單位：長度 mm、力 N、應力/模數 MPa、扭矩 N·mm */
data class Material(val name: String, val g: Double, val e: Double) {
    companion object {
        val all = listOf(
            Material("琴鋼線 (Music wire)", 79300.0, 206000.0),
            Material("油回火鋼線", 77200.0, 203400.0),
            Material("不鏽鋼 302/304", 69000.0, 193000.0),
            Material("磷青銅", 41400.0, 103400.0),
            Material("鉻矽鋼線", 77200.0, 203400.0),
        )
    }
}

enum class EndType(val label: String) {
    CLOSED_GROUND("閉端磨平"), CLOSED("閉端不磨"), OPEN("開端不磨"), OPEN_GROUND("開端磨平");

    /** 總圈數 */
    fun totalCoils(na: Double) = when (this) {
        CLOSED_GROUND, CLOSED -> na + 2
        OPEN -> na
        OPEN_GROUND -> na + 1
    }

    /** 密合高度 */
    fun solidLength(na: Double, d: Double): Double {
        val nt = totalCoils(na)
        return when (this) {
            CLOSED_GROUND, OPEN_GROUND -> nt * d
            CLOSED, OPEN -> (nt + 1) * d
        }
    }
}

object SpringCalc {
    /** 彈簧中徑 */
    fun meanDia(od: Double, d: Double) = od - d

    /** 旋繞比 C = D/d */
    fun index(dm: Double, d: Double) = dm / d

    /** Wahl 應力修正係數 */
    fun wahl(c: Double) = (4 * c - 1) / (4 * c - 4) + 0.615 / c

    /** 剪應力 τ = Kw·8FD/(πd³) */
    fun shear(f: Double, dm: Double, d: Double) = wahl(index(dm, d)) * 8 * f * dm / (PI * d.pow(3))

    /** 線性彈簧常數 k = G d⁴ / (8 D³ Na)  (N/mm) */
    fun rate(g: Double, d: Double, dm: Double, na: Double) = g * d.pow(4) / (8 * dm.pow(3) * na)

    // ---------- 壓縮 ----------
    data class Compression(
        val dm: Double, val c: Double, val k: Double, val totalCoils: Double,
        val solid: Double, val load: Double, val stress: Double,
        val solidLoad: Double, val solidStress: Double, val slenderness: Double,
        val warnings: List<String>,
    )

    fun compression(d: Double, od: Double, na: Double, l0: Double, lw: Double, m: Material, end: EndType): Compression {
        val dm = meanDia(od, d); val c = index(dm, d); val k = rate(m.g, d, dm, na)
        val ls = end.solidLength(na, d)
        val f = k * (l0 - lw); val fs = k * (l0 - ls)
        val w = buildList {
            if (c < 4) add("旋繞比 C<4，難以成形")
            if (c > 12) add("旋繞比 C>12，易纏繞/不穩定")
            if (l0 / dm > 4) add("自由長/中徑 >4，注意挫曲，建議加導桿或導套")
            if (lw < ls) add("工作長度小於密合高度")
            if (lw > l0) add("工作長度大於自由長度")
        }
        return Compression(dm, c, k, end.totalCoils(na), ls, f, shear(f, dm, d), fs, shear(fs, dm, d), l0 / dm, w)
    }

    // ---------- 拉伸 ----------
    data class Extension(
        val dm: Double, val c: Double, val k: Double, val bodyLength: Double,
        val load: Double, val stress: Double, val initialStress: Double,
        val stressMax: Double, val warnings: List<String>,
    )

    /** l0 = 兩鉤內側自由長度, lw = 工作長度, fi = 初張力(N), fMax = 最大負荷(N) */
    fun extension(d: Double, od: Double, na: Double, l0: Double, lw: Double, fi: Double, fMax: Double, m: Material): Extension {
        val dm = meanDia(od, d); val c = index(dm, d); val k = rate(m.g, d, dm, na)
        val f = fi + k * (lw - l0)
        val w = buildList {
            if (c < 4) add("旋繞比 C<4，難以成形")
            if (c > 12) add("旋繞比 C>12，初張力難控制")
            if (lw < l0) add("工作長度小於自由長度，無拉力")
            if (f < fi) add("負荷小於初張力，彈簧尚未拉開")
        }
        return Extension(dm, c, k, (na + 1) * d, f, shear(f.coerceAtLeast(0.0), dm, d), shear(fi, dm, d), shear(fMax, dm, d), w)
    }

    // ---------- 扭力 ----------
    data class Torsion(
        val dm: Double, val c: Double, val na: Double, val ratePerDeg: Double,
        val torque: Double, val force: Double, val stress: Double,
        val closedDia: Double, val warnings: List<String>,
    )

    /** nb=本體圈數, l1/l2=兩臂長(mm), angle=扭轉角(度), arm=受力點半徑(mm) */
    fun torsion(d: Double, od: Double, nb: Double, l1: Double, l2: Double, angle: Double, arm: Double, m: Material): Torsion {
        val dm = meanDia(od, d); val c = index(dm, d)
        val na = nb + (l1 + l2) / (3 * PI * dm)           // 含臂等效圈數
        val kDeg = m.e * d.pow(4) / (3667 * dm * na)       // N·mm/度
        val mT = kDeg * angle
        val ki = (4 * c * c - c - 1) / (4 * c * (c - 1))
        val sigma = ki * 32 * mT / (PI * d.pow(3))
        val dClosed = dm * nb / (nb + angle / 360) + d      // 扭緊後外徑
        val w = buildList {
            if (c < 4) add("旋繞比 C<4，難以成形")
            if (c > 14) add("旋繞比 C>14，不穩定")
            if (angle / 360 > nb) add("扭轉角過大")
        }
        return Torsion(dm, c, na, kDeg, mT, if (arm > 0) mT / arm else 0.0, sigma, dClosed, w)
    }
}
