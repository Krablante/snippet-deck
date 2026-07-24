package com.rrajath.expander.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetConvertersTest {
    @Test
    fun `aliases survive Room converter round trip`() {
        val converter = SnippetConverters()
        val aliases = listOf("urgent", "!priority", "русский")

        assertEquals(aliases, converter.jsonToAliases(converter.aliasesToJson(aliases)))
    }
}
