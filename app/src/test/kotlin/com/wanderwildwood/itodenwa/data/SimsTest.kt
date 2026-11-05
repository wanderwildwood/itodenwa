package com.wanderwildwood.itodenwa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimsTest {
    private val word = "SIM %1\$d"

    @Test fun slotAndName() = assertEquals("SIM 2 · Northwind Mobile", Sims.format(2, "Northwind Mobile", word))
    @Test fun nameRepeatsSlot() = assertEquals("SIM 1", Sims.format(1, "sim1", word))
    @Test fun noName() = assertEquals("SIM 1", Sims.format(1, "  ", word))
    @Test fun noSlot() = assertEquals("Northwind Mobile", Sims.format(null, " Northwind Mobile ", word))
    @Test fun nothing() = assertNull(Sims.format(null, null, word))
}
