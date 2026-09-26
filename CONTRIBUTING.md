![Development](https://img.shields.io/badge/Category-Development-505477) [![EN](https://img.shields.io/badge/Language-EN-505477)](CONTRIBUTING.md) [![RU](https://img.shields.io/badge/Language-RU-714857)](docs/ru/CONTRIBUTING.md)

# Develop SnippetDeck

The repository has two applications with a shared user-facing backup and sync format. Android lives under `app/` and uses Kotlin, Room, Compose, and AccessibilityService. Desktop lives under `desktop/` and uses Rust, Tauri, and a static web editor. Pick the platform toolchain you need; changing desktop code does not require an Android SDK.

## Android

Install JDK 17 and Android SDK Platform 37.0 (`sdkmanager --channel=3 'platforms;android-37.0'`). The app still targets Android 36. Set your SDK path in an ignored `local.properties` (see `local.properties.example`) or open the project in Android Studio. An Android 13+ device or emulator is needed for interactive testing.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=2
```

The debug APK appears under `app/build/outputs/apk/debug/` with a `.debug` application ID, so it can coexist with the official build. Accessibility changes need a real typing check in more than one target editor; unit tests cannot represent every accessibility node.

## Windows, macOS, and Linux

Install Rust and the [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/) for your OS. Debian/Ubuntu also need `libxdo-dev`. The editor under `desktop/ui/` has no frontend package install.

```bash
cd desktop/src-tauri
cargo test --locked
cargo build --locked
```

The binary is under `desktop/src-tauri/target/debug/` unless you set `CARGO_TARGET_DIR`. Google Drive sign-in in a local build also needs the `SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET` build environment variable; other features work without it. Do not put credentials in source or logs. GitHub Actions packages each OS on a matching runner; see [Operations](docs/OPERATIONS.md) for official releases.

## Change carefully

Keep trigger matching at the cursor, preserve text after it, and never submit a target field. Primary triggers and aliases must remain globally unique without regard to case. Desktop multiline expansion must paste rather than simulate Enter. Respect installed Android identifiers, Room migrations, the signing identity, backup readers, and the `snippetdeck-sync` format; old installations still need to read their data after an update.

Use the narrowest test that proves a change. Backup and database changes need a round trip or migration check, and UI changes need a screenshot or hands-on check. Keep snippets local unless a user connects Google Drive; do not add a backend, tokens in installers, or background polling. A release must include the official signed Android APK and all four desktop installers in the same stable GitHub release.

Explain the user-visible result and verification in a pull request. Small focused patches are easier to review than unrelated cleanup. Contributions are distributed under the [MIT License](LICENSE); upstream Expander attribution stays intact.

[← Project overview](README.md) · [Guide](docs/GUIDE.md) · [Architecture](docs/ARCHITECTURE.md)
