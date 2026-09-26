![Operations](https://img.shields.io/badge/Category-Operations-505477) [![EN](https://img.shields.io/badge/Language-EN-505477)](OPERATIONS.md) [![RU](https://img.shields.io/badge/Language-RU-714857)](ru/OPERATIONS.md)

# Operate SnippetDeck

SnippetDeck has no project backend to deploy. The operated surfaces are a GitHub Pages site, Android and desktop CI, and one stable GitHub release containing the signed APK plus four desktop installers. The [user guide](GUIDE.md) covers installation, everyday use, backups, and update troubleshooting; this page covers the build and release path.

## Build and CI

The Android workflow runs unit tests, lint and a debug build. The desktop workflow packages Linux x64 (`.deb`), Windows x64 (`.msi`), and Apple Silicon and Intel macOS (`.dmg`) on their respective runners. Pushes and pull requests touching only documentation skip app builds; changes to app code or each platform's workflow run the corresponding jobs. A manual desktop workflow run leaves artifacts in Actions but does not publish a release.

The Android and desktop source toolchains and local test commands are in [Contributing](../CONTRIBUTING.md). Linux builds need `libxdo-dev`; the `.deb` installs a `libxdo3` dependency. The current macOS installers are unsigned and unnotarized. Public distribution without Gatekeeper prompts would need the maintainer's Apple Developer signing and notarization.

## Release from `main`

Release through `.github/workflows/release.yml` with a semantic tag such as `v1.8.2`. It must contain the official signed APK and all four installers. Both apps resolve GitHub `releases/latest`, so a desktop-only latest release would break Android's updater.

Before dispatching the workflow:

1. Bump Android `versionName` and increasing `versionCode` in `app/build.gradle.kts`; set the same version in `desktop/src-tauri/Cargo.toml`, its `Cargo.lock`, and `desktop/src-tauri/tauri.conf.json`. Update the expected Android version code in the release workflow.
2. Confirm the tag matches both versions, review user-facing changes and their EN/RU documentation, and wait for the relevant CI checks.
3. Confirm the encrypted Actions credentials exist. Never commit a keystore, signing values, OAuth tokens or the desktop OAuth client secret.

The release workflow validates the tag and desktop OAuth client, runs Android tests/lint and a signed release build, and checks application ID, version, increasing version code and the pinned signing certificate. It builds the four desktop installers, collects five binaries plus `SHA256SUMS.txt`, checks all five published GitHub digests against the checksum list and the asset count in a draft, then publishes one stable release. `preview=true` publishes a prerelease instead; latest-release checks continue to see the prior stable version until it is promoted. Do not replace an asset without repeating integrity checks.

After publication, inspect the anonymous `releases/latest` API: five correctly named binaries, their nonempty sizes, `sha256:` digests, the checksum file, and the stable tag. On devices you can reach, install over the old Android build without clearing its data, and test a trigger, alias, undo, `!help`, backup and update handoff. Test desktop installer handoff and preservation of local snippets on each OS where a machine is available. Record any platform you did not exercise on hardware; a successful CI build does not prove every target editor works.

## Signing and Google Drive

An Android APK updates an installed release only with the same application ID and signing certificate. Local signed builds can use ignored `keystore.properties` (see `keystore.properties.template`) or `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Different keys produce incompatible updates. The release workflow receives the official key from encrypted GitHub Actions secrets and verifies its pinned certificate. Keep signing material out of the repository and logs.

The public app uses separate Android and desktop OAuth clients with `drive.appdata`. Register the Android release package and pinned certificate with Google; the debug package/signature needs its own client for testing. Google's desktop token exchange needs `SNIPPETDECK_DESKTOP_OAUTH_CLIENT_SECRET` at build time. The release workflow rejects a missing or invalid value. This native-app value can be extracted from an installer and must not be treated as server authentication. The user's refresh token belongs in the OS credential store or session memory, never a build artifact.

Google OAuth branding uses the [public site](https://krablante.github.io/snippet-deck/) and its [privacy page](https://krablante.github.io/snippet-deck/privacy.html). GitHub Pages publishes `docs/` from `main`; the English privacy page mirrors [PRIVACY.md](../PRIVACY.md), and `docs/ru/privacy.html` mirrors the [Russian policy](ru/PRIVACY.md). Check the consent screen's real publishing status and a real login before claiming public Drive access. An External OAuth app in **Testing** expires authorizations after seven days. The shared Pages hostname cannot satisfy a future DNS ownership check; that would require a maintainer-controlled domain.

## Recovery and upstream

Exports are full local-library backups. Before reinstalling or resolving a sync conflict, save a copy from each affected device. An import replaces the entire local library after confirmation; disconnecting Drive does not remove its remote files. The [guide](GUIDE.md#back-up-or-move-a-library) gives the user's recovery path. Do not point two running desktop installations at one working library file.

Review upstream [Expander](https://github.com/rrajath/expander) changes separately. Preserve cursor behavior, data migrations, signing identity, backup formats, and attribution when incorporating upstream code.

[← Project overview](../README.md) · [Guide](GUIDE.md) · [Development](../CONTRIBUTING.md) · [Architecture](ARCHITECTURE.md)
