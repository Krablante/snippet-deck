package com.rrajath.expander.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.expander.ui.theme.SnippetDeckTheme
import com.rrajath.expander.update.UpdateUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUpdateActionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun githubUpdateActionIsVisibleAndClickable() {
        var clicked = false
        composeTestRule.setContent {
            SnippetDeckTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    onExportClick = {},
                    onImportClick = {},
                    onCopyTextClick = {},
                    onImportText = {},
                    updateState = UpdateUiState.Idle,
                    onCheckForUpdates = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Check for updates")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun themeDialogShowsWhiteBlackAndSepiaPreviews() {
        composeTestRule.setContent {
            SnippetDeckTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    onExportClick = {},
                    onImportClick = {},
                    onCopyTextClick = {},
                    onImportText = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Theme")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("Clean neutral canvas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deep low-light palette").assertIsDisplayed()
        composeTestRule.onNodeWithText("Warm book-like paper").assertIsDisplayed()
    }
}
