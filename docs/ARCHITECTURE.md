# Architecture

## Goals

SnippetDeck favors a small local-first architecture, predictable behavior, and portable recovery over infrastructure. It has no account system or backend, and the device remains the source of truth.

The design prioritizes:

- Reliable expansion at the current cursor position.
- Explicit, reversible user actions.
- Stable installed-app and data compatibility.
- Testable backup and migration formats.
- Minimal dependencies and operational surface area.

## System overview

```text
Compose UI ──► SnippetRepository ──► Room database
    │                 │
    │                 └────────────► accessibility cache
    │                                      │
    ├──► backup/import UI                  ▼
    └──► UpdateViewModel       TextExpansionService
              │                          │
              ▼                          ▼
       GitHub Releases API        editable field
```

## Text expansion

`TextExpansionService` observes editable text through Android accessibility events. When an enabled trigger appears immediately before the cursor and the user enters a delimiter, the service:

1. Identifies the trigger range relative to the current selection.
2. Replaces only that range.
3. Preserves text after the cursor.
4. Places the cursor immediately after the expansion.

Nodes that do not expose selection information use an end-of-field fallback. The service never submits the target field.

Immediate Backspace restores the typed trigger at its original cursor location where the target node supports the required editing actions. Dynamic placeholders are resolved immediately before insertion, and virtual `!help` is generated from enabled snippets.

## Data layer

`SnippetDao` is the Room persistence boundary. `SnippetRepository` serves the Compose editor, accessibility cache, and restore flow.

Room schema v2 stores aliases as JSON in the existing `snippets` table. Migration 1→2 initializes legacy rows with an empty alias list and preserves every existing snippet.

Each snippet has one primary trigger and zero or more aliases:

- Primary triggers retain automatic `!` normalization.
- Aliases are trimmed but otherwise preserved exactly.
- Primary triggers and aliases are globally unique, case-insensitively.

A complete restore validates the input and then replaces the library in one Room transaction. Fresh local IDs are assigned during import.

## Compose UI

The Compose interface provides snippet editing, search, enabled state, accessibility onboarding, theme selection, backup and transfer, and manual update checks. Import always previews the source and snippet count and warns that the current library will be replaced.

White, Black, and Sepia use deterministic Material 3 schemes adapted from Textory. Dynamic wallpaper colors are deliberately disabled so canvas, cards, contrast, and screenshots remain predictable. The persisted legacy Light/Dark/System values migrate to the closest explicit palette without discarding other preferences.

UI state is owned by view models and repositories rather than composables. Platform actions such as document selection and clipboard access remain at the UI boundary.

## Backup formats

`SnippetBackupCodec` owns serialization and validation independently of Android storage APIs.

- JSON uses `format=snippetdeck-backup` and `schemaVersion=2`.
- Text uses the `SNIPPETDECK_BACKUP_V2` prefix, gzip, and URL-safe Base64 without wrapping.
- Raw JSON, legacy raw-array/v1.0 envelopes, and text V1 remain accepted.
- Validation rejects oversized input, unsupported future schemas, invalid snippets, empty expansions, and duplicate primary or alias triggers.
- An empty backup is valid and intentionally clears the library after confirmation.

`ImportExportManager` reads and writes content URIs with strict size limits. Clipboard interaction stays in the UI layer.

## Application updates

The updater is deliberately small and separate from snippet storage:

- `UpdateViewModel` owns check, download, permission, and presentation state.
- `GitHubReleaseUpdater` makes one `releases/latest` request, downloads the selected APK, and verifies it.
- `UpdateInstaller` grants a temporary `FileProvider` URI to Android's package installer.
- `UpdateDialog` is shown only for an available update, active download, install permission, or download failure.

A normal launcher start performs at most one silent metadata check per activity/view-model lifetime, and only when Android reports a validated internet connection. Automatic current/offline/error results remain invisible. Manual checks use the same request and report their result in Settings. `PROCESS_TEXT` launches do not check for updates, and there is no worker, polling loop, account, or backend.

APK download starts only after explicit confirmation. Acceptance requires the exact `snippet-deck-v<version>.apk` asset, a valid GitHub SHA-256 digest, the official application ID, matching semantic version, increasing version code, and the pinned release-signing certificate. Android still shows its own installation confirmation.

## Security and privacy boundaries

- Network access is limited to public GitHub release metadata and a user-approved APK download.
- Snippets, observed text, settings, and backups are never sent with update requests.
- There is no background network worker, polling process, data sync, or silent installation.
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
