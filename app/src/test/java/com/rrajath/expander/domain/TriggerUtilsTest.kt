package com.rrajath.expander.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerUtilsTest {

    @Test
    fun `aliases accept separators and preserve exact prefix choice`() {
        assertEquals(
            listOf("mail", "!contact", "reply"),
            TriggerUtils.parseAliases("mail, !contact; reply\nMAIL")
        )
    }

    @Test
    fun `aliases cannot duplicate primary trigger`() {
        assertEquals(
            "Alias duplicates the primary trigger",
            TriggerUtils.aliasesValidationError("!main", "!main")
        )
    }

    @Test
    fun `matching is case insensitive for primary and aliases`() {
        assertTrue(TriggerUtils.matches("!MAIN", "!main", listOf("!alias")))
        assertTrue(TriggerUtils.matches("ALIAS", "!main", listOf("alias")))
        assertEquals(TriggerUtils.matchKey("!İ"), TriggerUtils.matchKey("!i"))
        assertEquals(TriggerUtils.matchKey("КК"), TriggerUtils.matchKey("кк"))
        assertEquals(TriggerUtils.matchKey("\uD801\uDC00"), TriggerUtils.matchKey("\uD801\uDC28"))
    }

    @Test
    fun `global conflict reports primary or alias`() {
        val reserved = setOf("!used", "reserved")
        assertEquals(
            "reserved",
            TriggerUtils.conflictingTrigger("!new", listOf("reserved"), reserved)
        )
        assertNull(
            TriggerUtils.conflictingTrigger("!new", listOf("free"), reserved)
        )
    }

    @Test
    fun `plain alias never receives exclamation prefix`() {
        assertEquals(listOf("кк"), TriggerUtils.parseAliases("кк"))
    }

    @Test
    fun `trigger limit counts unicode characters rather than surrogate halves`() {
        assertNull(TriggerUtils.validationError("!" + "😀".repeat(39)))
        assertEquals("Trigger must be at most 40 characters", TriggerUtils.validationError("!" + "😀".repeat(40)))
    }
}
