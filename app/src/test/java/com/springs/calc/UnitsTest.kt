package com.springs.calc

import com.springs.calc.ui.Kind
import com.springs.calc.ui.Units
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    private val u = Units.IMPERIAL
    @Test fun length() { assertEquals(1.0, u.lenOut(25.4), 1e-9); assertEquals(25.4, u.toSi(1.0, Kind.LEN), 1e-9) }
    @Test fun force() { assertEquals(1.0, u.forceOut(4.4482216), 1e-9) }
    @Test fun stress() { assertEquals(145037.7, u.stressOut(1000.0), 0.1) }
    @Test fun rate() { assertEquals(5.71015, u.rateOut(1.0), 1e-4) }
    @Test fun torque() { assertEquals(1.0, u.torqueOut(112.9848), 1e-4) }
}
