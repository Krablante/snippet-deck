package com.rrajath.expander.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)$")

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
            )
        }
    }
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val digest: String,
)

internal data class GitHubRelease(
    val version: SemanticVersion,
    val title: String,
    val asset: ReleaseAsset,
)

internal class UpdateException(message: String) : Exception(message)

internal class GitHubReleaseUpdater(
    private val context: Context,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
    fun isOfficialBuild(): Boolean = context.packageName == PACKAGE_NAME

    fun hasValidatedInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun installedVersion(): SemanticVersion? = SemanticVersion.parse(installedPackageInfo().versionName.orEmpty())

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = openConnection(latestReleaseUrl, GITHUB_ACCEPT)
        try {
            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(
                    connection.inputStream.bufferedReader().use { it.readText() },
                )

                HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException(
                    "No public SnippetDeck release is available yet.",
                )

                HttpURLConnection.HTTP_FORBIDDEN,
                HTTP_TOO_MANY_REQUESTS,
                -> throw UpdateException("GitHub rate-limited update checks. Try again later.")

                else -> throw UpdateException("GitHub returned HTTP $responseCode.")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndVerify(
        release: GitHubRelease,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (release.asset.size !in 1..MAX_APK_BYTES) {
            throw UpdateException("The release APK has an invalid size.")
        }

        val updateDirectory = File(context.cacheDir, UPDATE_CACHE_DIRECTORY)
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw UpdateException("Android could not create the update cache.")
        }
        updateDirectory.listFiles()?.forEach(File::delete)

        val baseName = "snippet-deck-v${release.version}"
        val partialFile = File(updateDirectory, "$baseName.apk.part")
        val apkFile = File(updateDirectory, "$baseName.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openConnection(release.asset.downloadUrl, APK_ACCEPT)

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw UpdateException("The APK download returned HTTP ${connection.responseCode}.")
            }
            val responseLength = connection.contentLengthLong
            if (responseLength > MAX_APK_BYTES) throw UpdateException("The release APK is too large.")

            var downloaded = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                partialFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentCoroutineContext().ensureActive()
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) throw UpdateException("The release APK is too large.")
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)

                        val progress = ((downloaded * 100L) / release.asset.size)
                            .toInt()
                            .coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }

            if (downloaded != release.asset.size) {
                throw UpdateException("The downloaded APK size does not match the GitHub release.")
            }
            verifyDigest(digest.digest().toHex(), release.asset.digest)
            if (!partialFile.renameTo(apkFile)) {
                throw UpdateException("Android could not prepare the downloaded APK.")
            }
            verifyPackage(apkFile, release.version)
            onProgress(100)
            apkFile
        } catch (error: Exception) {
            partialFile.delete()
            apkFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyPackage(apkFile: File, releaseVersion: SemanticVersion) {
        val packageInfo = packageArchiveInfo(apkFile)
            ?: throw UpdateException("Android did not recognize the downloaded APK.")
        if (packageInfo.packageName != PACKAGE_NAME) {
            throw UpdateException("The APK belongs to another application.")
        }

        val packageVersion = SemanticVersion.parse(packageInfo.versionName.orEmpty())
        if (packageVersion != releaseVersion) {
            throw UpdateException("The APK version does not match the GitHub release.")
        }
        if (PackageInfoCompat.getLongVersionCode(packageInfo) <= installedVersionCode()) {
            throw UpdateException("The downloaded APK is not newer than the installed version.")
        }

        val signerDigests = signingCertificates(packageInfo).map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }
        if (signerDigests.size != 1 || signerDigests.single() != EXPECTED_SIGNER_SHA256) {
            throw UpdateException("The APK is signed by an unknown certificate.")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apkFile: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(packageInfo: PackageInfo) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, 0)

    private fun installedVersionCode(): Long = PackageInfoCompat.getLongVersionCode(installedPackageInfo())

    private fun openConnection(url: String, accept: String): HttpURLConnection {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw UpdateException("The update address is invalid.")
        if (uri.scheme != "https") throw UpdateException("The update address is not secure.")
        if (uri.host !in ALLOWED_DOWNLOAD_HOSTS && uri.host != GITHUB_API_HOST) {
            throw UpdateException("The update address uses an unknown host.")
        }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "SnippetDeck/${installedPackageInfo().versionName.orEmpty()}")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
        }
    }

    companion object {
        internal const val PACKAGE_NAME = "com.rrajath.expander"
        internal const val EXPECTED_SIGNER_SHA256 =
            "627216136dc143c9a2d4d4cc0851441e46753fc1ecd30548e25f5ebe15ae358d"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Krablante/snippet-deck/releases/latest"
        private const val GITHUB_API_HOST = "api.github.com"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val APK_ACCEPT = "application/octet-stream"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val UPDATE_CACHE_DIRECTORY = "updates"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_APK_BYTES = 100L * 1024L * 1024L
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        internal fun parseRelease(json: String): GitHubRelease {
            val root = JSONObject(json)
            val tag = root.optString("tag_name")
            val version = SemanticVersion.parse(tag)
                ?: throw UpdateException("The GitHub release has an invalid version: $tag")
            val expectedAssetName = "snippet-deck-v$version.apk"
            val assets = root.optJSONArray("assets")
                ?: throw UpdateException("The GitHub release has no APK asset.")

            var matchingAsset: ReleaseAsset? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                if (!name.equals(expectedAssetName, ignoreCase = true)) continue

                val downloadUrl = asset.optString("browser_download_url")
                val uri = runCatching { URI(downloadUrl) }.getOrNull()
                if (uri?.scheme != "https" || uri.host != "github.com") {
                    throw UpdateException("The release APK has an invalid download address.")
                }
                val digest = asset.optString("digest").takeIf(String::isNotBlank)
                    ?: throw UpdateException("The GitHub release has no SHA-256 digest.")
                validateDeclaredDigest(digest)
                matchingAsset = ReleaseAsset(
                    name = name,
                    downloadUrl = downloadUrl,
                    size = asset.optLong("size", -1L),
                    digest = digest,
                )
                break
            }

            return GitHubRelease(
                version = version,
                title = root.optString("name").ifBlank { "SnippetDeck $version" },
                asset = matchingAsset
                    ?: throw UpdateException("The release is missing $expectedAssetName."),
            )
        }

        internal fun verifyDigest(actual: String, declared: String) {
            val expected = validateDeclaredDigest(declared)
            if (actual.lowercase() != expected) {
                throw UpdateException("The APK checksum does not match the GitHub release.")
            }
        }

        private fun validateDeclaredDigest(declared: String): String {
            val expected = declared.removePrefix("sha256:").lowercase()
            if (!expected.matches(Regex("[0-9a-f]{64}"))) {
                throw UpdateException("GitHub returned an invalid APK digest.")
            }
            return expected
        }
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
