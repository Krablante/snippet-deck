package com.rrajath.expander.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePresentationTest {
    private val release = GitHubRelease(
        version = SemanticVersion(1, 6, 0),
        title = "SnippetDeck v1.6.0",
        asset = ReleaseAsset(
            name = "snippet-deck-v1.6.0.apk",
            downloadUrl = "https://github.com/Krablante/snippet-deck/releases/download/v1.6.0/snippet-deck-v1.6.0.apk",
            size = 17_000_000,
            digest = "sha256:${"e".repeat(64)}",
        ),
    )

    @Test
    fun newerReleaseIsPresentedForSilentAndManualChecks() {
        assertTrue(
            resolveUpdateCheckState(release, SemanticVersion(1, 5, 0), silent = true) is
                UpdateUiState.Available,
        )
        assertTrue(
            resolveUpdateCheckState(release, SemanticVersion(1, 5, 0), silent = false) is
                UpdateUiState.Available,
        )
    }

    @Test
    fun silentCurrentCheckStaysInvisible() {
        assertSame(
            UpdateUiState.Idle,
            resolveUpdateCheckState(release, SemanticVersion(1, 6, 0), silent = true),
        )
    }

    @Test
    fun manualCurrentCheckShowsInstalledVersion() {
        assertEquals(
            UpdateUiState.Current("1.6.0"),
            resolveUpdateCheckState(release, SemanticVersion(1, 6, 0), silent = false),
        )
    }

    @Test
    fun settingsStatusKeepsResultsConcise() {
        assertEquals(
            "GitHub Releases · Version 1.5.0",
            updateStatusText(UpdateUiState.Idle, "1.5.0"),
        )
        assertEquals(
            "Version 1.5.0 is up to date",
            updateStatusText(UpdateUiState.Current("1.5.0"), "1.5.0"),
        )
    }
}
