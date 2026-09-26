<p align="center">
  <img src="design/app_icons/play-store/ic_launcher-playstore.png" width="128" alt="SnippetDeck app icon">
</p>

<h1 align="center">SnippetDeck</h1>

<p align="center">
  A fast, local-first text expander for Android and desktop.
</p>

<p align="center">
  <a href="https://github.com/Krablante/snippet-deck/actions/workflows/ci.yml"><img src="https://github.com/Krablante/snippet-deck/actions/workflows/ci.yml/badge.svg" alt="CI status"></a>
  <a href="https://github.com/Krablante/snippet-deck/actions/workflows/desktop.yml"><img src="https://github.com/Krablante/snippet-deck/actions/workflows/desktop.yml/badge.svg" alt="Desktop builds"></a>
  <a href="https://github.com/Krablante/snippet-deck/releases/latest"><img src="https://img.shields.io/github/v/release/Krablante/snippet-deck" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT license"></a>
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white" alt="Android 13 or newer">
</p>

Type a trigger such as `!review`, press Space, and SnippetDeck replaces it immediately before the cursor. Text after the cursor stays in place, and the app never submits the target field.

## See it in action

<table>
  <tr>
    <td width="33%" align="center">
      <img src="docs/images/snippet-library.png" width="260" alt="SnippetDeck library with example snippets">
      <br><strong>White · Build your library</strong>
      <br><sub>Keep triggers, aliases, and expansions easy to scan.</sub>
    </td>
    <td width="33%" align="center">
      <img src="docs/images/snippet-editor.png" width="260" alt="SnippetDeck editor with a date placeholder">
      <br><strong>Sepia · Create flexible snippets</strong>
      <br><sub>Add plain aliases and dynamic values such as <code>{{date}}</code>.</sub>
    </td>
    <td width="33%" align="center">
      <img src="docs/images/settings-backup.png" width="260" alt="SnippetDeck settings and backup actions">
      <br><strong>Black · Stay in control</strong>
      <br><sub>Manage the service, appearance, and portable backups.</sub>
    </td>
  </tr>
</table>

```text
Create a snippet  →  type !today + Space  →  SnippetDeck inserts the expansion
```

## Highlights

- Cursor-aware expansion that preserves surrounding text.
- One primary trigger and any number of aliases per snippet.
- Immediate Backspace undo after an expansion.
- Dynamic `!help` generated from enabled snippets.
- Date and time placeholders such as `{{date}}`, `{{time}}`, `{{year_short}}`, and `{{week_num}}`.
- A compact, searchable snippet library with enabled state and White, Black, or Sepia themes.
- Portable JSON files and compact text backups for moving a library between devices.
- Fully local snippet storage with no account, backend, analytics, advertising, or data sync.
- Quiet GitHub release checks and verified in-app APK updates.
- A desktop editor and background text expansion for Windows, macOS, and Linux X11.

## Install

### Android

SnippetDeck requires Android 13 or newer.

1. Download the APK from the [latest GitHub release](https://github.com/Krablante/snippet-deck/releases/latest).
2. Install the APK. Android may ask you to allow installation from your browser or file manager.
3. If Android blocks the accessibility service for a sideloaded app, open **App info → menu → Allow restricted settings**.
4. Open SnippetDeck and enable its accessibility service.

### Desktop

Windows, macOS (Apple Silicon and Intel), and Linux X11 installers are published as a [desktop preview](https://github.com/Krablante/snippet-deck/releases/tag/desktop-v0.1.0) (`.msi`, `.dmg`, or `.deb`). Development builds are also available from the [Desktop builds workflow](https://github.com/Krablante/snippet-deck/actions/workflows/desktop.yml). The macOS builds are currently unsigned and have not yet been tested on a Mac; Gatekeeper may require an explicit **Open Anyway** decision. Windows and macOS compatibility should be verified on real machines before relying on them for everyday use.

Launch the app and leave it running to expand text. Closing the editor keeps the background agent available in the tray. **More options → Start at login** enables automatic launch; **Text expansion** pauses it. On macOS, grant the app Accessibility permission when prompted, then reopen it. On Linux Wayland, the library editor remains usable, but automatic text expansion is not supported. The X11 build does not require root privileges.

## Updates

On a normal Android app launch, SnippetDeck makes one quiet request to the public GitHub Releases API when Android reports a validated internet connection. Nothing is shown when the installed version is current, the device is offline, or the automatic check fails. Desktop installers currently have no in-app updater.

You can also open **Settings → About → Check for updates** at any time. Downloading and installation always require an explicit tap. Before Android opens its package installer, SnippetDeck verifies:

- The exact versioned APK asset and GitHub-provided SHA-256 digest.
- The application ID, version name, and increasing version code.
- The official SnippetDeck signing certificate.

There is no background polling or automatic installation. Contextual `PROCESS_TEXT` launches never trigger an update check.

## Use

1. Create a snippet with a trigger and expansion.
2. Optionally add aliases separated by commas, semicolons, or new lines. Aliases are used exactly as entered and do not require `!`.
3. Type the trigger in an editable field and press Space.
4. Press Backspace immediately after an expansion to restore the trigger where supported by the target app.

Typing `!help` followed by Space produces a compact list of enabled snippets.

On desktop, click **+** to add a snippet, or open one to edit it. Triggers and aliases expand when followed by Space; the Space is consumed, as on Android. A Backspace immediately after expansion restores the trigger. The desktop editor closes completely when dismissed; you can reopen it from the tray or the app launcher. Behavior can vary between target applications, so verify it in the editors you use most. Single-line snippets use native input; multiline snippets paste as text so line breaks cannot submit a target field.

## Appearance

SnippetDeck includes three stable palettes that do not inherit wallpaper colors:

- **White** — a clean neutral canvas with restrained green actions.
- **Black** — Textory's deep low-light palette with high-contrast text.
- **Sepia Paper** — a warm book-like canvas with a brown accent.

Existing Light and Dark preferences migrate to White and Black. A legacy System preference resolves once to White or Black using the device mode active during migration.

## Backup and transfer

On Android, open **Settings → Backup & transfer**:

- **Export backup file** creates readable, versioned JSON suitable for long-term storage.
- **Import backup file** previews the snippet count before replacing the local library.
- **Copy backup text** creates a compact `SNIPPETDECK_BACKUP_V2` payload for a note or message to yourself.
- **Paste backup text** accepts that payload or a supported JSON backup.

Both formats preserve triggers, aliases, expansions, enabled state, and timestamps. Import is transactional and replaces the complete library so deletions transfer correctly. Legacy raw-array, v1.0 JSON, and text V1 backups remain importable.

On desktop, open **More options → Export backup…** to save the same JSON format, or **Import backup…** to read Android JSON and compact text backups. Import shows the snippet count and replaces the complete local library only after confirmation. The desktop app stores its working copy in the operating system's application-data directory. You can put an exported file in Google Drive and import it on another device; **this is manual transfer, not automatic sync**. If both devices changed, export both libraries before replacing either one and reconcile the changes yourself.

> [!CAUTION]
> Backups may contain sensitive text. Store and share them accordingly.

## Privacy and security

On Android, SnippetDeck uses the accessibility API to detect triggers and replace text in editable fields. The desktop agent keeps only a short in-memory buffer of recently typed characters to detect a trigger. Observed field content is not stored or transmitted.

- On Android, snippets and settings stay in the local Room database. Desktop keeps its library and settings in local app data.
- On Android, snippet content leaves the app only when you explicitly export or copy a backup. On desktop, multiline expansions briefly use the system clipboard and attempt to restore its previous content. Clipboard managers may retain copied text; avoid storing secrets in multiline snippets.
- Android networking is limited to GitHub release metadata and an APK download you explicitly approve. Snippets, observed text, settings, and backups are never included in those requests.
- The desktop app makes no network requests; its backups and library stay local until you choose where to save a file.
- There is no account system, analytics, advertising, remote-control component, background updater, or data-sync service.
- Official updates must keep the same Android signing identity so they can be installed over an existing version without clearing local data.

## Build from source

Android requirements:

- Android Studio or Android SDK 36
- JDK 17 or newer
- An Android 13+ device or emulator

Clone the repository, configure your Android SDK in an ignored `local.properties`, and run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=2
```

The debug APK is written under `app/build/outputs/apk/debug/` and uses a separate application ID, so it can be installed alongside an official release.

For desktop, install Rust and the [Tauri desktop prerequisites](https://v2.tauri.app/start/prerequisites/) (plus `libxdo-dev` on Debian/Ubuntu Linux), then build from `desktop/src-tauri` using `cargo build --locked`. The editor uses static HTML/CSS/JavaScript in `desktop/ui`; no frontend package install is needed. GitHub Actions creates the OS-specific installers.

See [Contributing](CONTRIBUTING.md) for development expectations and [Operations](docs/OPERATIONS.md) for signed builds and releases.

## Architecture

```text
Android: Compose editor ──► Room ──► AccessibilityService ──► editable field
Desktop: editor (on demand) ──► local JSON ──► Rust keyboard adapter ──► editable field
         Both libraries exchange compatible backup files by explicit import/export.
```

See [Architecture](docs/ARCHITECTURE.md) for component boundaries, data formats, and compatibility contracts.

## Lineage and license

SnippetDeck is based on [Expander](https://github.com/rrajath/expander) by Rajath Radhakrishnan and contributors. It is distributed under the [MIT License](LICENSE).
