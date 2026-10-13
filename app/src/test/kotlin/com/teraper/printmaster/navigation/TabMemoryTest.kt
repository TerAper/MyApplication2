package com.teraper.printmaster.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabMemoryTest {

    private var time = 0L
    private val memory = TabMemory { time }

    @Test
    fun aTabComesBackAsLeftWithinTenSecondsAndFreshAfter() {
        // Never left yet: nothing to lose.
        assertTrue(memory.restoreOnSwitch(TopLevelDestination.TODAY, TopLevelDestination.CLIENTS))
        time = 1_000
        assertTrue(memory.restoreOnSwitch(TopLevelDestination.CLIENTS, TopLevelDestination.ORDERS))
        time = 11_000
        assertTrue(memory.restoreOnSwitch(TopLevelDestination.ORDERS, TopLevelDestination.CLIENTS))
        time = 12_000
        memory.restoreOnSwitch(TopLevelDestination.CLIENTS, TopLevelDestination.PAYMENTS)
        time = 22_001
        assertFalse(memory.restoreOnSwitch(TopLevelDestination.PAYMENTS, TopLevelDestination.CLIENTS))
        // Orders was left at 11 s: much later it starts over too.
        time = 60_000
        assertFalse(memory.restoreOnSwitch(TopLevelDestination.CLIENTS, TopLevelDestination.ORDERS))
    }
}
