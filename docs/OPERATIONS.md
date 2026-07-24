# Operations

This document covers public installation, builds, releases, and user-data recovery. Machine-specific paths, credentials, and maintainer infrastructure do not belong in the repository.

## Install an official release

1. Download the APK from the [latest GitHub release](https://github.com/Krablante/snippet-deck/releases/latest).
2. Install it on Android 13 or newer.
3. If Android blocks accessibility for the sideloaded app, open **App info → menu → Allow restricted settings**.
4. Enable **SnippetDeck** under Android Accessibility settings.

Android accepts an in-place update only when the APK has the same application ID and signing certificate as the installed version.

After installing a release with the built-in updater, future versions can be checked from **Settings → About → Check for updates**. The app also performs one silent metadata check on a normal launcher start when validated internet is available. APK download and installation remain user initiated.

## Local development build

Requirements:

- JDK 17 or newer.
- Android SDK 36.
- An Android 13+ device or emulator.

Configure the SDK through Android Studio or an ignored `local.properties`, then run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=2
```

The debug variant uses a distinct application ID and version suffix so it can coexist with an official release.

## Local signed build

Copy `keystore.properties.template` to ignored `keystore.properties` and provide your own signing values, or set:

- `KEYSTORE_FILE`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Then run:

```bash
./gradlew assembleRelease --no-daemon --max-workers=2
```

Never commit a keystore or signing credentials. A build signed with a different certificate cannot update an existing official installation.

## Official GitHub release

The `Release APK` workflow is started manually with a semantic version such as `1.5.0`. It:

1. Checks out the selected revision.
2. Restores the release keystore from encrypted GitHub Actions secrets.
3. Runs unit tests, lint, and the release build.
4. Verifies application ID, version name, APK signature validity, and the pinned release certificate.
5. Publishes `snippet-deck-v<version>.apk` under the matching `v<version>` tag.

Before starting the workflow:

- Update `versionName` and `versionCode` in `app/build.gradle.kts`.
- Confirm that the workflow input, `versionName`, and release tag describe the same version.
- Review user-visible documentation and compatibility notes.
- Confirm CI is green.

After publication:

- Install the release over the previous official APK without uninstalling it.
- Confirm existing snippets and settings remain available.
- Test one primary trigger, one alias, Backspace undo, and `!help`.
- Export and re-import a backup on a disposable test installation.
- Confirm the anonymous `releases/latest` API exposes the APK size and `sha256:` digest.
- Use the previous official version to check, download, verify, and hand off the update to Android's installer.

## Update troubleshooting

- Automatic checks are intentionally silent; use the Settings action for a visible result.
- A device that starts offline does not retry in the background. Check manually after connectivity returns.
- Android may require one-time **Install unknown apps** permission for SnippetDeck before opening the installer.
- Debug and unofficial package IDs cannot use the official self-updater.
- A missing digest, wrong asset name, changed signing key, non-increasing version code, or malformed version fails closed before installation.

## Backup and recovery

Use **Settings → Backup & transfer** before reinstalling or moving devices.

- File export creates readable versioned JSON.
- Text export creates a compact payload beginning with `SNIPPETDECK_BACKUP_V2`.
- Import previews the source and count, then replaces the full local library after confirmation.

To recover:

1. Install a correctly signed SnippetDeck APK.
2. Enable its accessibility service.
3. Import the latest file or text backup.
4. Confirm the restored count and test both an enabled and disabled snippet.

## Upstream changes

The project is derived from [rrajath/expander](https://github.com/rrajath/expander). Review upstream changes in a dedicated branch and preserve SnippetDeck's cursor behavior, local data model, backup compatibility, signing identity, and public documentation.
