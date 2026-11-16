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
}
