package com.rrajath.expander.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.ui.theme.SnippetDeckTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetItemLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun snippetWithAliasesUsesCompactCardHeight() {
        composeTestRule.setContent {
            SnippetDeckTheme {
                SnippetItem(
                    snippet = Snippet(
                        trigger = "!review",
                        aliases = listOf("feedback", "rv"),
                        expansion = "Thanks for the update. I will review it today.",
                    ),
                    onClick = {},
                    onDelete = {},
                    onToggle = {},
                    modifier = Modifier.testTag("snippet-card"),
                )
            }
        }

        val heightPx = composeTestRule.onNodeWithTag("snippet-card")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
            .density

        assertTrue("Card height was $heightPx px", heightPx <= 96f * density)
    }
}
