package com.rrajath.expander

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.ui.screens.AddEditSnippetScreen
import com.rrajath.expander.ui.theme.SnippetDeckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddEditSnippetScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialExpansion_isPrefilledInExpansionField() {
        composeTestRule.setContent {
            SnippetDeckTheme {
                AddEditSnippetScreen(
                    snippet = null,
                    reservedTriggers = emptySet(),
                    initialExpansion = "Hello world",
                    onSave = { _, _, _ -> },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("Add Snippet").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hello world").assertIsDisplayed()
    }

    @Test
    fun nullInitialExpansion_expansionFieldIsEmpty() {
        composeTestRule.setContent {
            SnippetDeckTheme {
                AddEditSnippetScreen(
                    snippet = null,
                    reservedTriggers = emptySet(),
                    initialExpansion = null,
                    onSave = { _, _, _ -> },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("Add Snippet").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hello world").assertDoesNotExist()
    }

    @Test
    fun editMode_snippetExpansionWinsOverInitialExpansion() {
        val snippet = Snippet(
            id = 1L,
            trigger = ":hi",
            expansion = "Existing expansion"
        )

        composeTestRule.setContent {
            SnippetDeckTheme {
                AddEditSnippetScreen(
                    snippet = snippet,
                    reservedTriggers = emptySet(),
                    initialExpansion = "Should be ignored",
                    onSave = { _, _, _ -> },
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Existing expansion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Should be ignored").assertDoesNotExist()
    }
}
