package com.springs.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class SpringCalcTest {
    private val steel = Material.all[0]

    @Test fun compression() {
        val r = SpringCalc.compression(2.0, 16.0, 8.0, 60.0, 40.0, steel, EndType.CLOSED_GROUND)
        assertEquals(7.2249, r.k, 1e-3); assertEquals(144.50, r.load, 0.01)
        assertEquals(781.0, r.stress, 0.5); assertEquals(20.0, r.solid, 1e-9)
    }

    @Test fun torsion() {
        val r = SpringCalc.torsion(2.0, 16.0, 6.0, 30.0, 30.0, 90.0, 30.0, steel)
        assertEquals(9.9465, r.ratePerDeg, 1e-3); assertEquals(895.18, r.torque, 0.05)
        assertEquals(1275.5, r.stress, 0.5)
    }

    @Test fun extension() {
        val r = SpringCalc.extension(2.0, 16.0, 8.0, 60.0, 80.0, 10.0, 200.0, steel)
        assertEquals(10.0 + 7.2249 * 20, r.load, 0.01)
    }

    @Test fun extensionHook() {
        val r = SpringCalc.extension(2.0, 16.0, 8.0, 60.0, 80.0, 10.0, 200.0, steel, 7.0, 4.0)
        assertEquals(2058.4, r.hookBendingMax, 0.5); assertEquals(1114.1, r.hookTorsionMax, 0.5)
    }

    @Test fun fatigueLife() {
        val r = SpringCalc.fatigue(780.99, 0.0, 2.0, steel, false)
        assertEquals(1999.6, r.sut, 0.5); assertEquals(262.0, r.sse, 0.5)
        assertEquals(0.647, r.fatigueSf, 0.01)
        assertEquals(5.27e5, r.cycles, 5.27e5 * 0.02)
    }

    @Test fun fatigueInfinite() {
        val r = SpringCalc.fatigue(500.0, 0.0, 2.0, steel, false)
        assertEquals(Double.POSITIVE_INFINITY, r.cycles, 0.0)
    }

    @Test fun torsionFatigue() {
        val r = SpringCalc.fatigueBending(1275.47, 0.0, 2.0, steel, false)
        assertEquals(473.9, r.sse, 0.5); assertEquals(0.705, r.fatigueSf, 0.01)
        assertEquals(1.234e5, r.cycles, 1.234e5 * 0.02)
    }
}
