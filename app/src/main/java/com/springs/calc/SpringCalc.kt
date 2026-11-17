package com.springs.calc

import kotlin.math.PI
import kotlin.math.pow

/** 單位：長度 mm、力 N、應力/模數 MPa、扭矩 N·mm */
/** a, m：抗拉強度 Sut = a / d^m (MPa, d 以 mm)；steel：疲勞係數是否適用 */
data class Material(val name: String, val g: Double, val e: Double, val a: Double, val m: Double, val steel: Boolean = true) {
    fun sut(d: Double) = a / Math.pow(d, m)

    companion object {
        val all = listOf(
            Material("琴鋼線 (Music wire)", 79300.0, 206000.0, 2211.0, 0.145),
            Material("油回火鋼線", 77200.0, 203400.0, 1855.0, 0.187),
            Material("不鏽鋼 302/304", 69000.0, 193000.0, 1867.0, 0.146),
            Material("磷青銅", 41400.0, 103400.0, 932.0, 0.064, steel = false),
            Material("鉻矽鋼線", 77200.0, 203400.0, 1974.0, 0.108),
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


    // ---------- 疲勞壽命（壓縮彈簧）----------
    data class Fatigue(
        val sut: Double, val ssu: Double, val sse: Double, val tauMax: Double, val tauMin: Double,
        val staticSf: Double, val fatigueSf: Double,
        /** 預估循環次數；Infinity = 無限壽命(>1e7)；1e3 以下以 1e3 表示下限 */
        val cycles: Double, val belowThousand: Boolean, val warnings: List<String>,
    )

    /**
     * 依 Shigley/Zimmerli 法：Ssu=0.67Sut；Ssa/Ssm = 241/379 MPa（珠擊 398/534）；
     * Gerber 平均應力修正，S-N 以 10³(0.9Ssu) 與 10⁷(Sse) 兩點在對數座標內插。
     */
    fun fatigue(tauMax: Double, tauMin: Double, d: Double, m: Material, peened: Boolean): Fatigue {
        val sut = m.sut(d); val ssu = 0.67 * sut
        val ssa = if (peened) 398.0 else 241.0; val ssm = if (peened) 534.0 else 379.0
        val sse = minOf(ssa / (1 - (ssm / ssu).pow(2)).coerceAtLeast(0.05), 0.5 * ssu)
        val tm = (tauMax + tauMin) / 2; val ta = (tauMax - tauMin) / 2
        val w = buildList {
            if (!m.steel) add("疲勞係數以鋼線試驗為基礎，此材質結果僅供參考")
            if (tauMax > 0.45 * sut) add("最大應力超過靜態降伏約 0.45Sut，可能永久變形")
            if (ta <= 0) add("循環應力幅為 0，無疲勞")
        }
        val staticSf = if (tauMax > 0) 0.45 * sut / tauMax else Double.POSITIVE_INFINITY
        if (ta <= 0 || tm >= ssu) {
            return Fatigue(sut, ssu, sse, tauMax, tauMin, staticSf, Double.POSITIVE_INFINITY,
                if (ta <= 0) Double.POSITIVE_INFINITY else 1.0, ta > 0, w)
        }
        val nf = 0.5 * (ssu / tm).pow(2) * (ta / sse) *
            (-1 + kotlin.math.sqrt(1 + (2 * tm * sse / (ssu * ta)).pow(2)))
        val eq = ta / (1 - (tm / ssu).pow(2))
        val s1 = 0.9 * ssu
        return when {
            eq >= s1 -> Fatigue(sut, ssu, sse, tauMax, tauMin, staticSf, nf, 1e3, true, w)
            eq <= sse -> Fatigue(sut, ssu, sse, tauMax, tauMin, staticSf, nf, Double.POSITIVE_INFINITY, false, w)
            else -> {
                val logN = 3 + 4 * kotlin.math.ln(s1 / eq) / kotlin.math.ln(s1 / sse)
                Fatigue(sut, ssu, sse, tauMax, tauMin, staticSf, nf, 10.0.pow(logN), false, w)
            }
        }
    }

    /**
     * 扭力彈簧（線材受彎曲）疲勞：Sr = 0.45Sut（珠擊 0.67Sut，10⁶ 次，R=0 的最大應力），
     * Se = (Sr/2)/(1−(Sr/2Sut)²)，Gerber 修正；S-N 以 10³(0.9Sut) 與 10⁶(Se) 對數內插。
     * cycles = Infinity 代表 >10⁶ 次。
     */
    fun fatigueBending(sigMax: Double, sigMin: Double, d: Double, m: Material, peened: Boolean): Fatigue {
        val sut = m.sut(d)
        val sr = (if (peened) 0.67 else 0.45) * sut
        val se = (sr / 2) / (1 - (sr / 2 / sut).pow(2))
        val sm = (sigMax + sigMin) / 2; val sa = (sigMax - sigMin) / 2
        val sf = if (sigMax > 0) 0.78 * sut / sigMax else Double.POSITIVE_INFINITY
        val w = buildList {
            if (!m.steel) add("疲勞係數以鋼線試驗為基礎，此材質結果僅供參考")
            if (sigMax > 0.78 * sut) add("最大彎曲應力超過建議上限 0.78Sut，可能永久變形")
            if (sa <= 0) add("循環應力幅為 0，無疲勞")
        }
        if (sa <= 0 || sm >= sut) return Fatigue(sut, sut, se, sigMax, sigMin, sf, Double.POSITIVE_INFINITY,
            if (sa <= 0) Double.POSITIVE_INFINITY else 1.0, sa > 0, w)
        val nf = 0.5 * (sut / sm).pow(2) * (sa / se) *
            (-1 + kotlin.math.sqrt(1 + (2 * sm * se / (sut * sa)).pow(2)))
        val eq = sa / (1 - (sm / sut).pow(2)); val s1 = 0.9 * sut
        return when {
            eq >= s1 -> Fatigue(sut, sut, se, sigMax, sigMin, sf, nf, 1e3, true, w)
            eq <= se -> Fatigue(sut, sut, se, sigMax, sigMin, sf, nf, Double.POSITIVE_INFINITY, false, w)
            else -> Fatigue(sut, sut, se, sigMax, sigMin, sf, nf,
                10.0.pow(3 + 3 * kotlin.math.ln(s1 / eq) / kotlin.math.ln(s1 / se)), false, w)
        }
    }

    // ---------- 材質強度判定 ----------
    enum class Verdict(val label: String) { OK("✔ 合格"), CAUTION("⚠ 需預壓除應力"), FAIL("✘ 超限") }
    data class Strength(val ratio: Double, val verdict: Verdict)

    /** 剪應力 / 抗拉強度：≤0.45 合格；≤0.65 須經預壓(除應力)處理；以上超限 */
    fun strengthShear(tau: Double, sut: Double): Strength {
        val r = tau / sut
        return Strength(r, when { r <= 0.45 -> Verdict.OK; r <= 0.65 -> Verdict.CAUTION; else -> Verdict.FAIL })
    }

    /** 彎曲應力 / 抗拉強度（扭力彈簧、鉤環彎曲）：≤0.65 合格；≤0.78 須除應力；以上超限 */
    fun strengthBending(sigma: Double, sut: Double): Strength {
        val r = sigma / sut
        return Strength(r, when { r <= 0.65 -> Verdict.OK; r <= 0.78 -> Verdict.CAUTION; else -> Verdict.FAIL })
    }

    // ---------- 拉伸彈簧疲勞 ----------
    data class ExtFatigue(
        val body: Fatigue, val hookBend: Fatigue, val hookTors: Fatigue,
        val cycles: Double, val belowThousand: Boolean, val governing: String, val warnings: List<String>,
    )

    /** 循環於 lmin(最小長度) ～ lw(工作長度)；本體、鉤環彎曲、鉤側彎扭轉三處取最短壽命 */
    fun extensionFatigue(
        d: Double, dm: Double, k: Double, l0: Double, lw: Double, lmin: Double, fi: Double,
        r1: Double, r2: Double, m: Material, peened: Boolean,
    ): ExtFatigue {
        fun force(l: Double) = fi + k * (l - l0).coerceAtLeast(0.0)
        val fMax = force(lw); val fMin = force(lmin)
        val body = fatigue(shear(fMax, dm, d), shear(fMin, dm, d), d, m, peened)
        val hb = fatigueBending(hookBending(fMax, d, dm, r1), hookBending(fMin, d, dm, r1), d, m, peened)
        val ht = fatigue(hookTorsion(fMax, d, dm, r2), hookTorsion(fMin, d, dm, r2), d, m, peened)
        val all = listOf("本體" to body, "鉤環彎曲" to hb, "鉤側彎扭轉" to ht)
        val g = all.minWith(compareBy({ it.second.cycles }, { it.second.fatigueSf }))
        val w = (body.warnings + hb.warnings + ht.warnings).distinct() +
            (if (lmin >= lw) listOf("循環最小長度需小於工作長度") else emptyList())
        return ExtFatigue(body, hb, ht, g.second.cycles, g.second.belowThousand, g.first, w)
    }

    // ---------- 拉伸彈簧初張力（自動值）----------
    data class InitTension(val fi: Double, val fiLo: Double, val fiHi: Double, val tau: Double, val tauLo: Double, val tauHi: Double)

    /**
     * Shigley 建議的初應力範圍：τi = 33.5 ksi / exp(0.105C) ± 1 ksi·(4 − (C−3)/6.5)，C = D/d；
     * 初張力 Fi = π·d³·τi / (8·D)（初應力不含 Wahl 修正）。fi 取範圍中值。
     */
    fun initialTension(d: Double, dm: Double): InitTension {
        val c = dm / d
        val tau = 33.5 * 6.894757 / Math.exp(0.105 * c)
        val tol = 6.894757 * (4 - (c - 3) / 6.5)
        fun f(t: Double) = (PI * d.pow(3) * t / (8 * dm)).coerceAtLeast(0.0)
        return InitTension(f(tau), f(tau - tol), f(tau + tol), tau, tau - tol, tau + tol)
    }

    // ---------- 拉伸 ----------
    data class Extension(
        val dm: Double, val c: Double, val k: Double, val bodyLength: Double,
        val load: Double, val stress: Double, val initialStress: Double,
        val stressMax: Double, val warnings: List<String>,
        val hookBending: Double, val hookTorsion: Double,
        val hookBendingMax: Double, val hookTorsionMax: Double,
    )

    /** 全圈鉤環彎曲應力 σ = F[Ka·16D/(πd³) + 4/(πd²)]，Ka = (4C1²−C1−1)/(4C1(C1−1)), C1 = 2r1/d */
    fun hookBending(f: Double, d: Double, dm: Double, r1: Double): Double {
        val c1 = (2 * r1 / d).coerceAtLeast(1.05)
        val ka = (4 * c1 * c1 - c1 - 1) / (4 * c1 * (c1 - 1))
        return f * (ka * 16 * dm / (PI * d.pow(3)) + 4 / (PI * d.pow(2)))
    }

    /** 鉤環側彎處扭轉應力 τ = Kb·8FD/(πd³)，Kb = (4C2−1)/(4C2−4), C2 = 2r2/d */
    fun hookTorsion(f: Double, d: Double, dm: Double, r2: Double): Double {
        val c2 = (2 * r2 / d).coerceAtLeast(1.05)
        return (4 * c2 - 1) / (4 * c2 - 4) * 8 * f * dm / (PI * d.pow(3))
    }

    /** l0 = 兩鉤內側自由長度, lw = 工作長度, fi = 初張力(N), fMax = 最大負荷(N) */
    fun extension(d: Double, od: Double, na: Double, l0: Double, lw: Double, fi: Double, fMax: Double, m: Material, r1: Double = (od - d) / 2, r2: Double = 2 * d): Extension {
        val dm = meanDia(od, d); val c = index(dm, d); val k = rate(m.g, d, dm, na)
        val f = fi + k * (lw - l0)
        val w = buildList {
            if (c < 4) add("旋繞比 C<4，難以成形")
            if (c > 12) add("旋繞比 C>12，初張力難控制")
            if (lw < l0) add("工作長度小於自由長度，無拉力")
            if (f < fi) add("負荷小於初張力，彈簧尚未拉開")
            if (2 * r1 / d <= 1.0 || 2 * r2 / d <= 1.0) add("鉤環半徑過小（C1、C2 必須大於 1）")
        }
        val fw = f.coerceAtLeast(0.0)
        return Extension(dm, c, k, (na + 1) * d, f, shear(f.coerceAtLeast(0.0), dm, d), shear(fi, dm, d), shear(fMax, dm, d), w,
            hookBending(fw, d, dm, r1), hookTorsion(fw, d, dm, r2),
            hookBending(fMax, d, dm, r1), hookTorsion(fMax, d, dm, r2))
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
