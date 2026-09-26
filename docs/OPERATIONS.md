# Operations

This document covers public installation, builds, releases, and user-data recovery. Machine-specific paths, credentials, and maintainer infrastructure do not belong in the repository.

## Desktop builds and installation

The `Desktop builds` workflow compiles the same Rust source on Windows, macOS, and Linux. Its build artifacts contain `.msi`, `.dmg`, and `.deb` installers respectively; manual runs leave artifacts in Actions without publishing a release. The `Release SnippetDeck` workflow combines those four installers with the official signed Android APK in a single stable release. Both updaters require their platform's exact asset name and GitHub SHA-256 digest from the latest stable release. Android also requires the official signed APK. Desktop builds are unsigned; macOS Gatekeeper may require a manual **Open Anyway** decision.

Run SnippetDeck after installation. The tray's **Open library** item opens the editor; closing the editor keeps expansion active. Choose **Start at login** in the editor's menu to make the agent available after sign-in. If you grant Accessibility permission on macOS after starting SnippetDeck, quit and reopen it. Linux expansion is supported in an X11 session; in Wayland the editor works, while expansion is disabled.

To build locally, install the [Tauri desktop prerequisites](https://v2.tauri.app/start/prerequisites/) and Rust, plus `libxdo-dev` on Debian/Ubuntu Linux. From `desktop/src-tauri` run `cargo build --locked`; the app binary is under `target/debug`. The static UI lives in `desktop/ui`. The Linux `.deb` depends on `libxdo3` and installs it through the package manager. To package a release installer, use Tauri CLI or the GitHub workflow on the matching OS. A macOS installer intended for seamless public distribution still needs signing and notarization with the maintainer's Apple Developer identity.

## Install an official release

1. Download the APK from the [latest GitHub release](https://github.com/Krablante/snippet-deck/releases/latest).
2. Install it on Android 13 or newer.
3. If Android blocks accessibility for the sideloaded app, open **App info → menu → Allow restricted settings**.
4. Enable **SnippetDeck** under Android Accessibility settings.

Android accepts an in-place update only when the APK has the same application ID and signing certificate as the installed version.

After installing a release with the built-in updater, future versions can be checked from **Settings → About → Check for updates**. The app also performs one silent metadata check on a normal launcher start when validated internet is available. APK download and installation remain user initiated.

Desktop performs one silent release check at process startup, including tray startup; an available update appears when the library opens. **More options → Check for updates** reports the result or error. **Install update** asks for confirmation, downloads the platform installer to the application cache, verifies the size and SHA-256 digest, and opens the OS installer. Windows exits the app after launching MSI; on macOS move the app from the opened DMG into Applications; on Linux finish through the configured `.deb` handler or use the displayed path with a package manager. Reopen the app to confirm the installed version. Existing local application data stays in place. No periodic checks or unattended installs occur.

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

The `Release SnippetDeck` workflow is started manually from `main` with a semantic tag such as `v1.8.0`. The optional `preview` input publishes the same signed builds as a prerelease for device testing; both latest-release updaters keep pointing to the prior stable release until the prerelease is promoted. It:

1. Checks out the selected revision.
2. Restores the release keystore from encrypted GitHub Actions secrets.
3. Runs unit tests, lint, and the release build.
4. Verifies application ID, version name, APK signature validity, and the pinned release certificate.
5. Builds the Windows, macOS (Apple Silicon and Intel), and Linux X11 installers from the same commit.
6. Uploads the five assets and a checksum list to a draft release, checks the APK digest and asset count, then publishes it as the latest stable release.

For a preview, the final step publishes a prerelease instead. After testing the signed APK and the desktop installers, edit that release to clear **prerelease** and mark it **latest**. Do not replace assets after verification without repeating integrity checks.

Before starting the workflow:

- Update `versionName` and the increasing `versionCode` in `app/build.gradle.kts`, plus the version in `desktop/src-tauri/Cargo.toml` and `desktop/src-tauri/tauri.conf.json`. Update the release workflow's expected Android version code when bumping it.
- Confirm that the workflow input, both platform versions, and the release tag describe the same version.
- Review user-visible documentation and compatibility notes.
- Confirm CI is green.

After publication:

- Install the release over the previous official APK without uninstalling it.
- Confirm existing snippets and settings remain available.
- Test one primary trigger, one alias, Backspace undo, and `!help`.
- Export and re-import a backup on a disposable test installation.
- Confirm the anonymous `releases/latest` API exposes the APK size and `sha256:` digest.
- Confirm the same release includes both macOS installers, the Windows installer, and the Linux installer.
- Confirm the anonymous API exposes `sha256:` digests, exact filenames, and download URLs for each desktop installer.
- Use the previous official version to check, download, verify, and hand off the update to Android's installer.
- Use the previous desktop release on each supported operating system to check, download, verify, and open the platform installer; confirm the in-place update preserves local snippets. If a matching machine is unavailable, record that limitation.

## Update troubleshooting

- Automatic checks are intentionally silent on failure or when current; use Android Settings or desktop More options for a visible result.
- A device that starts offline does not retry in the background. Check manually after connectivity returns.
- Android may require one-time **Install unknown apps** permission for SnippetDeck before opening the installer.
- Debug and unofficial package IDs cannot use the official self-updater.
- A missing digest, wrong asset name, or malformed version blocks either updater. A changed signing key or non-increasing version code also blocks Android installation.
- Desktop requires the latest *stable* GitHub release to include the platform's installer. An unsupported CPU architecture reports an error during manual checking. Linux requires a `.deb` opener; use the displayed installer path and your package manager if no handler is configured.

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

On desktop, select **More options → Export backup…** to create Android-compatible JSON. **Import backup…** accepts that file or a compact text backup, previews the count, and replaces the whole library after confirmation. Export from each device before importing when both libraries have changed independently. Manual transfer of backup files remains separate from the in-app sync feature. The working library file belongs in local app data, never in a folder shared between live installations.

## Google Drive sync setup and recovery

The public app uses separate Android and desktop OAuth client IDs within the same Google Cloud project. Enable Drive API and request only `drive.appdata`. Register the Android release package and pinned signing certificate; a debug build with its different package/signature needs a separate Android OAuth client to exercise sign-in. Google's token endpoint requires a client secret for the Desktop app client even with PKCE. Official installers receive it through the `SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET` GitHub Actions secret at build time; release builds fail if it is missing. Local builds can supply the same environment variable. Native app secrets can be extracted from installers, so this value does not authenticate the app as a trusted server. Do not put a desktop client secret, access token, refresh token, or downloaded OAuth JSON in the repository or logs. The public [privacy policy](../PRIVACY.md) describes the opt-in data flow. An External OAuth app left in **Testing** expires user authorizations after seven days; publish the consent screen for everyday use. Standard Drive API use fits the free tier, but keep paid billing and quota increases disabled if costs are unacceptable.

The public homepage and consent-screen privacy policy live in `docs/index.html` and `docs/privacy.html`, published by GitHub Pages from `main` under `https://krablante.github.io/snippet-deck/`. Keep the privacy page in step with the canonical [policy text](../PRIVACY.md) when the data flow changes. Google Auth Platform's Branding page uses those URLs and the authorized domain `krablante.github.io`. The optional logo is unset. If Google later requires DNS-level ownership verification for branding, the shared GitHub Pages hostname cannot provide it; use a domain with DNS records controlled by the maintainer. Check the actual Audience publishing status and a real account login before telling users that Google sign-in works publicly.

To connect, use the Android Settings card or the desktop library's Google Drive row and select the same account. Snippets stay local when offline. Sync runs on app open or local edits, or when requested manually, without continuous polling. If a conflict appears, export backups from both devices before selecting the library to keep. Only one other device can be selected with **Use other device**; with several devices, make the choice on the device whose data you want to keep. Disconnect stops sync on that installation; it does not delete its local snippets or the cloud data. Reconnecting to a different Google account requires a deliberate reset of local sync history, not a silent upload to the new account.

## Upstream changes

The project is derived from [rrajath/expander](https://github.com/rrajath/expander). Review upstream changes in a dedicated branch and preserve SnippetDeck's cursor behavior, local data model, backup compatibility, signing identity, and public documentation.
