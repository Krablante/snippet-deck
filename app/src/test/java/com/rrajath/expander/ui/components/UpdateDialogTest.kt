package com.rrajath.expander.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDialogTest {
    @Test
    fun byteSizesUseCompactReadableUnits() {
        assertEquals("900 B", formatBytes(900))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }
}
