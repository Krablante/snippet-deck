# Contributing

Thank you for helping improve SnippetDeck. Small, focused changes with clear tests are the easiest to review and maintain.

## Development setup

You need JDK 17, Android SDK 36, and an Android 13+ device or emulator.

Desktop development uses Rust and the [Tauri system prerequisites](https://v2.tauri.app/start/prerequisites/) (`libxdo-dev` is also needed on Debian/Ubuntu). From `desktop/src-tauri`, run `cargo build --locked`. The desktop UI is static and does not need an npm dependency install. The `Desktop builds` workflow packages on each target OS.

Create an ignored `local.properties` with your SDK location or open the project in Android Studio, then verify the checkout:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=2
```

Debug builds use the `.debug` application ID suffix and can be installed alongside an official release.

## Change guidelines

- Keep the architecture local-first and avoid introducing services or dependencies without a concrete product need.
- Desktop local data must remain compatible with Android JSON and text backups. Preserve full-replacement import confirmation, primary/alias uniqueness, and the cursor's surrounding text when expanding.
- Multiline desktop expansion must not simulate Enter in a target field. Preserve clipboard content when using paste and stop before changing the field if insertion cannot be prepared.
- Every stable release must contain the verified official signed Android APK alongside the desktop installers; the Android updater resolves GitHub's latest release.
- Preserve cursor-aware replacement: only the trigger immediately before the cursor is replaced, text after the cursor remains intact, and the cursor moves to the end of the expansion.
- Never make expansion submit or send the target field.
- Keep every primary trigger and alias globally unique, case-insensitively.
- Preserve plain aliases exactly as entered after trimming; do not automatically prefix them with `!`.
- Treat the application ID, package namespace, Room database name, preferences filenames, and release signing identity as compatibility contracts.
- Add explicit Room migrations for schema changes and tests for every supported upgrade path.
- Preserve current and legacy backup compatibility unless a documented migration path is provided.
- Keep GitHub update checks separate from optional Google Drive sync. Never embed access or refresh tokens, request access to all Drive files, add a server backend, or poll in the background. A cloud edit must not overwrite concurrent local work silently.
- Preserve the exact release asset name, semantic version, increasing version code, package ID, SHA-256 verification, and pinned signing identity required by installed updates.
- Keep White, Black, and Sepia deterministic; do not re-enable wallpaper-derived dynamic colors. New UI must remain legible in all three palettes and preserve migration of legacy Light/Dark/System values.
- Do not commit SDK paths, keystores, credentials, APKs, exported backups, device data, or local agent/editor state.

## Tests

Choose the narrowest useful test and keep the full verification command green.

- Unit tests cover snippet validation, aliases, placeholders, backup codecs, import semantics, release parsing, semantic versions, digest checks, and update presentation.
- Instrumented tests cover Android-specific behavior and Compose flows.
- Changes to accessibility expansion should also be exercised manually in more than one editable target because apps expose accessibility nodes differently.
- Changes to backup or database formats require round-trip and migration coverage.

## Pull requests

Describe the user-visible outcome, important implementation choices, and how the change was verified. Keep unrelated refactors out of the same pull request. Include screenshots or a short recording when the UI changes materially.

By contributing, you agree that your work is distributed under the repository's [MIT License](LICENSE).
