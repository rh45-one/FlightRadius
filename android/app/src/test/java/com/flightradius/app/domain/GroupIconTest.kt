package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupIconTest {
    @Test fun `sixteen icons`() = assertEquals(16, GroupIcon.entries.size)

    @Test fun `every name round trips`() {
        for (icon in GroupIcon.entries) assertEquals(icon, GroupIcon.fromKey(icon.name))
    }

    @Test fun `unknown or missing keys fall back to plane`() {
        assertEquals(GroupIcon.PLANE, GroupIcon.fromKey(null))
        assertEquals(GroupIcon.PLANE, GroupIcon.fromKey(""))
        assertEquals(GroupIcon.PLANE, GroupIcon.fromKey("DRAGON"))
        assertEquals(GroupIcon.PLANE, GroupIcon.fromKey("star")) // keys are case-sensitive names
        assertTrue(GroupIcon.entries.first() == GroupIcon.PLANE)
    }
}
