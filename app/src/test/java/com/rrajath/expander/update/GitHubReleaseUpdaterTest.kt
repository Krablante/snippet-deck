package com.rrajath.expander.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdaterTest {
    @Test
    fun semanticVersionParsesStableTagsAndComparesComponents() {
        assertEquals(SemanticVersion(1, 5, 0), SemanticVersion.parse("v1.5.0"))
        assertEquals(SemanticVersion(2, 0, 3), SemanticVersion.parse("2.0.3"))
        assertTrue(SemanticVersion(1, 10, 0) > SemanticVersion(1, 9, 9))
    }

    @Test
    fun semanticVersionRejectsMalformedAndPrereleaseTags() {
        assertNull(SemanticVersion.parse("1.5"))
        assertNull(SemanticVersion.parse("v1.5.0-beta"))
        assertNull(SemanticVersion.parse("release-1.5.0"))
    }

    @Test
    fun latestReleaseRequiresExactApkAndDigest() {
        val digest = "a".repeat(64)
        val release = GitHubReleaseUpdater.parseRelease(
            """
            {
              "tag_name": "v1.6.0",
              "name": "SnippetDeck v1.6.0",
              "assets": [
                {
                  "name": "snippet-deck-v1.6.0.apk",
                  "browser_download_url": "https://github.com/Krablante/snippet-deck/releases/download/v1.6.0/snippet-deck-v1.6.0.apk",
                  "size": 17000000,
                  "digest": "sha256:$digest"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(SemanticVersion(1, 6, 0), release.version)
        assertEquals("snippet-deck-v1.6.0.apk", release.asset.name)
        assertEquals(17_000_000L, release.asset.size)
        assertEquals("sha256:$digest", release.asset.digest)
    }

    @Test
    fun latestReleaseRejectsMissingDigest() {
        val error = assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.parseRelease(
                """
                {
                  "tag_name": "v1.6.0",
                  "assets": [{
                    "name": "snippet-deck-v1.6.0.apk",
                    "browser_download_url": "https://github.com/Krablante/snippet-deck/releases/download/v1.6.0/snippet-deck-v1.6.0.apk",
                    "size": 17000000
                  }]
                }
                """.trimIndent(),
            )
        }

        assertTrue(error.message.orEmpty().contains("SHA-256"))
    }

    @Test
    fun latestReleaseRejectsWrongAssetNameAndHost() {
        val digest = "b".repeat(64)
        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.parseRelease(
                """
                {
                  "tag_name": "v1.6.0",
                  "assets": [{
                    "name": "snippet-deck.apk",
                    "browser_download_url": "https://github.com/Krablante/snippet-deck/releases/download/v1.6.0/snippet-deck.apk",
                    "size": 17000000,
                    "digest": "sha256:$digest"
                  }]
                }
                """.trimIndent(),
            )
        }

        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.parseRelease(
                """
                {
                  "tag_name": "v1.6.0",
                  "assets": [{
                    "name": "snippet-deck-v1.6.0.apk",
                    "browser_download_url": "https://example.com/snippet-deck-v1.6.0.apk",
                    "size": 17000000,
                    "digest": "sha256:$digest"
                  }]
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun digestVerificationAcceptsGitHubFormatAndFailsClosed() {
        val digest = "c".repeat(64)
        GitHubReleaseUpdater.verifyDigest(digest, "sha256:$digest")

        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.verifyDigest("d".repeat(64), "sha256:$digest")
        }
        assertThrows(UpdateException::class.java) {
            GitHubReleaseUpdater.verifyDigest(digest, "sha256:not-a-digest")
        }
    }
}
