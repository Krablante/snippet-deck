![Development](https://img.shields.io/badge/Category-Development-505477) [![EN](https://img.shields.io/badge/Language-EN-505477)](CONTRIBUTING.md) [![RU](https://img.shields.io/badge/Language-RU-714857)](docs/ru/CONTRIBUTING.md)

# Develop SnippetDeck

The repository has two applications with a shared user-facing backup and sync format. Android lives under `app/` and uses Kotlin, Room, Compose, and AccessibilityService. Desktop lives under `desktop/` and uses Rust, Tauri, and a static web editor. Pick the platform toolchain you need; changing desktop code does not require an Android SDK.

## Android

Install JDK 17 and Android SDK Platform 37.0 (`sdkmanager --channel=3 'platforms;android-37.0'`). The app still targets Android 36. Set your SDK path in an ignored `local.properties` (see `local.properties.example`) or open the project in Android Studio. An Android 13+ device or emulator is needed for interactive testing.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=2
```

The debug APK appears under `app/build/outputs/apk/debug/` with a `.debug` application ID, so it can coexist with the official build. Accessibility changes need a real typing check in more than one target editor; unit tests cannot represent every accessibility node.

With a device or emulator connected, run `./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2` for UI and Room integrity checks, including the v1→v2 migration. Build first, then start the emulator if resources are tight.

## Windows, macOS, and Linux

Install Rust and the [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/) for your OS. Debian/Ubuntu also need `libxdo-dev`. The editor under `desktop/ui/` has no frontend package install.

```bash
cd desktop/src-tauri
cargo test --locked
cargo build --locked
```

The binary is under `desktop/src-tauri/target/debug/` unless you set `CARGO_TARGET_DIR`. Google Drive sign-in in a local build also needs the `SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET` build environment variable; other features work without it. Do not put credentials in source or logs. GitHub Actions packages each OS on a matching runner; see [Operations](docs/OPERATIONS.md) for official releases.

Windows CI also opens Microsoft Edge and checks the real keyboard hook and insertion path in a textarea and a contenteditable field. It covers Russian/English aliases, text around the cursor, repeated pastes, Unicode, Backspace and clipboard ownership. To run it locally on an interactive Windows desktop with Edge installed, use `cargo test --locked windows_browser_replacements -- --ignored --nocapture --test-threads=1`. This check briefly takes foreground focus and uses a temporary browser profile.

## Change carefully

Keep trigger matching at the cursor, preserve text after it, and never submit a target field. Primary triggers and aliases must remain globally unique without regard to case. Desktop multiline expansion must paste rather than simulate Enter. Respect installed Android identifiers, Room migrations, the signing identity, backup readers, and the `snippetdeck-sync` format; old installations still need to read their data after an update.

Use the narrowest test that proves a change. Backup and database changes need a round trip or migration check, and UI changes need a screenshot or hands-on check. Keep snippets local unless a user connects Google Drive; do not add a backend, tokens in installers, or background polling. A release must include the official signed Android APK and all four desktop installers in the same stable GitHub release.

Explain the user-visible result and verification in a pull request. Small focused patches are easier to review than unrelated cleanup. Contributions are distributed under the [MIT License](LICENSE); upstream Expander attribution stays intact.

The root `LICENSE` is the canonical notice. Android copies it into generated assets; desktop bundles it as a resource and declares MIT in Cargo metadata. Keep the notice in distributed packages as well as source.

## Visual assets

The icon combines an ink squircle (`#282832`), chalk braces (`#F6F3ED`) and a lavender placeholder (`#C8C8EE`). Master vectors are `design/app_icons/source/icon.svg` and `icon-foreground.svg`. The rest of that directory is a reusable Android resource bundle; its copies in `app/src/main/res/` are the installed app's resources. Update both when re-exporting the vectors.

Launcher raster sizes for mdpi through xxxhdpi are 48, 72, 96, 144 and 192 px; adaptive foregrounds are 108, 162, 216, 324 and 432 px. Keep foreground content in the 66/108 safe zone. The Play Store raster is 512 px. Desktop bundles use `desktop/src-tauri/icons/`; the Pages favicon lives in `docs/images/`. Screenshots in `docs/images/` must show sample data and current behavior.

## Documentation and languages

The documentation has five categories: everyday use in `GUIDE.md`, development here, code boundaries in `ARCHITECTURE.md`, release and maintenance in `OPERATIONS.md`, and data handling in `PRIVACY.md`. README is the product entry point and links to those categories. Keep one explanation per topic and link to it from the other pages.

English keeps its conventional root and `docs/` paths. Every other language mirrors the same filenames under `docs/<language-code>/`, with the same categories, section order and examples. Add Ukrainian as `docs/uk/`, German as `docs/de/`, or another language in its own directory; no generator or application changes are needed. Add that language to the README table and page navigation in all existing languages. The public site follows the same rule with `docs/<language-code>/index.html` and `privacy.html`; English lives directly in `docs/`.

Update affected translations in the same change as behavior, commands or configuration. Translate meaning into natural prose, rather than copying sentence structure. When data flows change, update both Markdown privacy policies and their public HTML pages. The app interface currently uses English; translated documentation should retain its actual control labels.

[← Project overview](README.md) · [Guide](docs/GUIDE.md) · [Architecture](docs/ARCHITECTURE.md)
