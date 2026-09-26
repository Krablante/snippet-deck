![Architecture](https://img.shields.io/badge/Category-Architecture-47795e) [![EN](https://img.shields.io/badge/Language-EN-47795e)](ARCHITECTURE.md) [![RU](https://img.shields.io/badge/Language-RU-806d5e)](ru/ARCHITECTURE.md)

# Architecture

## Goals

SnippetDeck favors a small local-first architecture, predictable behavior, and portable recovery over infrastructure. It has no account system or backend; each device stores its own library. Google Drive sync is optional, and portable backup export/import remains available without it.

The design prioritizes:

- Reliable expansion at the current cursor position.
- Explicit, reversible user actions.
- Stable installed-app and data compatibility.
- Testable backup and migration formats.
- Minimal dependencies and operational surface area.

## System overview

```text
Android: Compose → repository → Room → accessibility trigger index → editable field
Desktop: Tauri editor → Rust library → local JSON → keyboard trigger index → editable field
                         │
                         ├─ explicit backup import/export (compatible JSON/text)
                         ├─ optional Google Drive appData sync (one file per device)
                         └─ GitHub Releases check → confirmed installer handoff
```

The two apps share formats, not runtime code. Android storage and system permissions differ from desktop, so a shared implementation layer would couple decisions that can change independently. On either platform the input agent reads the local library; network work does not belong on the typing path.

## Text expansion

`TextExpansionService` observes editable text through Android accessibility events. It builds a lookup index when the enabled snippet list changes, so a space typed into another app does not scan the whole library. When a trigger appears immediately before the cursor and the user enters a delimiter, the service:

1. Identifies the trigger range relative to the current selection.
2. Replaces only that range.
3. Preserves text after the cursor.
4. Places the cursor immediately after the expansion.

Nodes that do not expose selection information use an end-of-field fallback. The service never submits the target field.

Immediate Backspace restores the typed trigger at its original cursor location where the target node supports the required editing actions. Dynamic placeholders are resolved immediately before insertion, and virtual `!help` is generated from enabled snippets.

The desktop Rust agent observes keyboard events through `rdev` and replaces the immediately preceding typed trigger through `enigo`. It keeps no field text on disk. The editor is a Tauri WebView that can close while the agent stays in the tray, releasing the WebView process when it is not needed. Single-line text is inserted through native input; Linux X11 uses the fast `libxdo` backend. Multiline text uses a paste operation so a simulated Enter cannot submit the target field. The app temporarily owns the clipboard and restores its previous supported content after paste, unless the user copied something else meanwhile. The agent does not inspect the entire target field: mouse clicks, navigation, or shortcuts clear its short in-memory trigger buffer. It works on Windows and macOS and on Linux X11; Wayland text expansion is deliberately disabled. The desktop engine maintains a lookup table for triggers and aliases and rebuilds it only after the library changes.

## Data layer

`SnippetDao` is the Room persistence boundary. `SnippetRepository` serves the Compose editor, accessibility cache, and restore flow.

Room schema v2 stores aliases as JSON in the existing `snippets` table. Migration 1→2 initializes legacy rows with an empty alias list and preserves every existing snippet.

Each snippet has one primary trigger and zero or more aliases:

- Primary triggers retain automatic `!` normalization.
- Aliases are trimmed but otherwise preserved exactly.
- Primary triggers and aliases are globally unique, case-insensitively.

A complete restore validates the input and then replaces the library in one Room transaction. Fresh local IDs are assigned during import.

Desktop keeps a separate local JSON file under the OS application-data directory. Saves write a temporary file in the same directory and rename it over the old copy. The file uses the Android backup envelope with `format=snippetdeck-backup` and `schemaVersion=2`, so export and import are reversible across platforms. Sync has its own versioned format and local metadata file; backup imports still replace the full library after confirmation. Avoid pointing two live desktop installations at the same working file.

Libraries allow up to 10,000 snippets and 2 MB of backup JSON. Saves serialize the full desktop library, while the typing index is rebuilt only after an edit. Google Drive sync lists at most 50 device files and transfers their bounded replicas when the app opens, saves, or the user requests sync. This is a deliberate whole-library exchange at the project's current limits; if those limits change, measure network transfer and merge cost before adding caching or a server.

## Compose UI

The Compose interface provides snippet editing, search, enabled state, accessibility onboarding, theme selection, backup and transfer, Google Drive connection, and manual update checks. Import always previews the source and snippet count and warns that the current library will be replaced.

White, Black, and Sepia use deterministic Material 3 schemes. Dynamic wallpaper colors are deliberately disabled so canvas, cards, contrast, and screenshots remain predictable. The persisted legacy Light/Dark/System values migrate to the closest explicit palette without discarding other preferences.

UI state is owned by view models and repositories rather than composables. Platform actions such as document selection and clipboard access remain at the UI boundary.

Desktop uses a small static web interface following the same White, Black, and Sepia palettes. It presents the list and editor side by side on wide screens and opens the editor as a full-screen panel on narrow screens. The Rust side owns persistence, validation, file dialogs, and expansion; the WebView has no direct filesystem or network access. At most 80 library rows are rendered at a time until the user requests more.

## Backup formats

`SnippetBackupCodec` owns serialization and validation independently of Android storage APIs.

- JSON uses `format=snippetdeck-backup` and `schemaVersion=2`.
- Text uses the `SNIPPETDECK_BACKUP_V2` prefix, gzip, and URL-safe Base64 without wrapping.
- Raw JSON, legacy raw-array/v1.0 envelopes, and text V1 remain accepted.
- Validation rejects oversized input, unsupported future schemas, invalid snippets, empty expansions, and duplicate primary or alias triggers.
- An empty backup is valid and intentionally clears the library after confirmation.

`ImportExportManager` reads and writes content URIs with strict size limits. Clipboard interaction stays in the UI layer.

The desktop import accepts Android's JSON envelope, raw-array and legacy backups, and compressed text V1/V2. It validates the complete library and previews the count before a full replacement. The desktop export writes the same versioned JSON envelope, without local row IDs. Backups are for explicit recovery, not the cloud sync transport.

## Optional Google Drive sync

`SyncLibrary` on Android and `sync.rs` on desktop implement the same `snippetdeck-sync` v1 format. The key of a sync record is its case-insensitive primary trigger; changing a trigger is a deletion and a creation. Each record holds its snippet or a deletion marker and a small version clock. An installation stores a local baseline outside the Android backup area or beside the desktop library. Local edits produce a new version relative to that baseline. Independent changes merge; concurrent different values of the same trigger remain unresolved until the user explicitly chooses a library. The existing backup validator rejects alias collisions before applying a merge. An Android Room transaction refuses to replace snippets if they changed during the network request; desktop checks its current library under a lock before saving.

Every installation owns a separate file in the account's hidden Drive `appDataFolder`. No device overwrites another device's file. This avoids an unsafe last-writer-wins update when two devices sync at once. A conflicted merge stays in local sync metadata and is not uploaded as the device's chosen library. Selecting **Use this device** records a version that incorporates all versions the device has seen; **Use other device** accepts the one other device's resolved copy. Deletion markers prevent offline copies from reviving deleted snippets. Files remain bounded by the app's size limits and are transferred only on a foreground app open, a local edit, or a manual action; there is no background polling or server.

Android requests the non-sensitive `drive.appdata` scope through Google Play services when the user connects. Desktop uses the system browser and a PKCE loopback callback. Google's token endpoint also requires the desktop client's native-app secret, which is injected when the installer is built and cannot be kept confidential in a distributed binary. Its refresh token belongs in the operating-system credential store, with session-only access if that store is unavailable. Tokens are separate from backups and never enter the WebView. `GoogleDriveSync` and `google.rs` own the Drive HTTP operations; the UI handles consent and explicit conflict choices. The user can disconnect without deleting their local library or cloud history. The provider can read the cloud data because the files are not end-to-end encrypted.

## Application updates

The updater is deliberately small and separate from snippet storage:

- `UpdateViewModel` owns check, download, permission, and presentation state.
- `GitHubReleaseUpdater` makes one `releases/latest` request, downloads the selected APK, and verifies it.
- `UpdateInstaller` grants a temporary `FileProvider` URI to Android's package installer.
- `UpdateDialog` is shown only for an available update, active download, install permission, or download failure.

A normal launcher start performs at most one silent metadata check per activity/view-model lifetime, and only when Android reports a validated internet connection. Automatic current/offline/error results remain invisible. Manual checks use the same request and report their result in Settings. `PROCESS_TEXT` launches do not check for updates, and there is no worker, polling loop, account, or backend.

APK download starts only after explicit confirmation. Acceptance requires the exact `snippet-deck-v<version>.apk` asset, a valid GitHub SHA-256 digest, the official application ID, matching semantic version, increasing version code, and the pinned release-signing certificate. Android still shows its own installation confirmation.

The desktop Rust `update.rs` checks the same latest stable release once at startup and on demand from the editor menu. It requires an increasing semantic version and the exact installer asset for the operating system and CPU architecture. A download starts only after confirmation; its byte count and SHA-256 must match the GitHub release metadata. A verified installer is cached in the OS application cache and opened by `msiexec.exe`, `open`, or `xdg-open`; Windows exits the running app so MSI can replace it. The OS handles installation and its own prompts. Neither platform polls in the background.

## Security and privacy boundaries

- Android and desktop check public GitHub release metadata and download an installer only after approval. The optional sync flow contacts Google OAuth and Drive.
- Multiline desktop insertion briefly exposes snippet text to the local system clipboard. Clipboard managers may retain it; this is not a channel for secrets.
- Snippets, observed text, settings, and backups are never sent with GitHub update requests. Only user-created snippets and sync metadata go to Drive after connecting; observed typing is never sent.
- There is no background network worker, polling process, backend, or silent installation.
- Observed editable text is not persisted or transmitted.
- Export occurs only after explicit user action.
- File and clipboard backups contain user data and must be treated as sensitive.
- Signing material and credentials never belong in source control.

## Compatibility contracts

The following identifiers are retained so updates preserve installed state:

- Android application ID and package namespace.
- Room database name and schema migrations.
- Preferences filenames and stored keys.
- Android release signing identity.
- GitHub release asset naming and SHA-256 metadata required by the updater.
- Current and documented legacy backup formats.

Changing one of these requires an explicit migration and upgrade test. Historical identifiers are compatibility details, not current product branding.

[← Project overview](../README.md) · [Guide](GUIDE.md) · [Development](../CONTRIBUTING.md) · [Operations](OPERATIONS.md)
