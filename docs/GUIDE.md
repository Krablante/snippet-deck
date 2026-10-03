![Guide](https://img.shields.io/badge/Category-Guide-505477) [![EN](https://img.shields.io/badge/Language-EN-505477)](GUIDE.md) [![RU](https://img.shields.io/badge/Language-RU-714857)](ru/GUIDE.md)

# Use SnippetDeck

SnippetDeck replaces a short trigger with saved text when you type the trigger and press Space. Your snippets live on your device. A backup moves them manually; Google Drive sync is an optional way to keep several devices in step.

## Install

All platforms share the [latest stable GitHub release](https://github.com/Krablante/snippet-deck/releases/latest). Choose the file for your device:

| Platform | File | Before you start |
| --- | --- | --- |
| Android 13+ | `snippet-deck-v<version>.apk` | Allow installation from your browser or file manager. If Android blocks the sideloaded accessibility service, use **App info → menu → Allow restricted settings**, then enable SnippetDeck in Accessibility settings. |
| Windows x64 | `SnippetDeck_<version>_x64_en-US.msi` | Install and launch the app; it remains in the tray when you close the editor. |
| macOS Apple Silicon or Intel | Matching `arm64.dmg` or `intel.dmg` | Move SnippetDeck into Applications, allow Accessibility access, then reopen it. Builds are currently unsigned; Gatekeeper may require **Open Anyway**. |
| Linux x64 | `SnippetDeck_<version>_amd64.deb` | Install the package through your package manager. Text expansion requires X11; the editor also works on Wayland. |

Windows and macOS behavior should be checked in the applications you type into: target fields and permissions vary. The X11 build does not require root privileges.

## Expand text

Create a snippet with a primary trigger and its expansion. Add aliases if you want more shortcuts. Primary triggers gain `!` automatically; aliases are used as written. Triggers and aliases must be unique regardless of letter case.

```text
Trigger: !review   Alias: rv
Expansion: Thanks for the update. I will review it today.

Type !review + Space → Thanks for the update. I will review it today.
```

The trigger and its trailing Space are replaced immediately before the cursor; text after it stays put. An immediate Backspace restores the trigger where the target field supports editing. `!help` lists enabled snippets without taking space in your library. Triggers and aliases are limited to 40 characters and cannot contain whitespace. Android leaves password fields and SnippetDeck's own editor alone.

On desktop, the editor closes while the expansion agent remains in the tray. Reopen it from the tray or app launcher. **More options → Start at login** controls automatic launch; **Text expansion** pauses insertion. Multiline expansions briefly use the system clipboard and restore its previous supported content when possible.

Save before closing the editor or app. Android keeps a draft across activity recreation while its editor destination remains open; drafts are not saved backups. With Drive connected, deleting a snippet also removes it from the other devices on their next sync.

## Date and time

Placeholders use the device's local time at insertion. Repeated placeholders in one expansion use the same instant.

| Placeholder | Result |
| --- | --- |
| `{{date}}` | `2026-10-02` |
| `{{time}}` | `14:30:00` |
| `{{datetime}}` | `2026-10-02 14:30:00` |
| `{{day}}`, `{{day_long}}` | Short or full weekday name |
| `{{month}}`, `{{month_long}}` | Short or full month name |
| `{{year}}`, `{{year_short}}` | `2026`, `26` |
| `{{week_num}}` | Week number |
| `{{date:dd/MM/yyyy}}` | `02/10/2026` |
| `{{time:hh:mm a}}` | `02:30 PM` |

Android uses its system locale and `SimpleDateFormat` patterns. Desktop uses English names, ISO week numbers, and these pattern tokens: `yyyy`, `yy`, `M`/`MM`/`MMM`/`MMMM`, `d`/`dd`, `EEE`/`EEEE`, `H`/`HH`, `h`/`hh`, `m`/`mm`, `s`/`ss`, and `a`. Quote literal letters with single quotes. Unknown placeholders and invalid or unsupported patterns stay as written; use the listed tokens for backups shared between platforms.

## Back up or move a library

Android: **Settings → Backup & transfer** exports a JSON file or compact text, and imports either after showing the snippet count. Desktop: **More options → Export backup… / Import backup…** uses the same format. An import **replaces the whole local library**, including deletions, only after confirmation. Export the current library before importing another one if you might need both.

The working library stays in each device's app data. Keep backup files somewhere you control; they can contain sensitive text. Old raw-array JSON, v1.0 JSON, and text V1 backups remain readable.

The library supports up to 10,000 snippets and 2 MB of serialized JSON. These limits keep backups and sync usable; an oversized save or import leaves the current library intact. Android's system backup can also preserve the library and settings when enabled on the device; sync history is excluded, so reconnect Drive after a system restore. Keep an explicit export for recovery you control.

On Android the floating toolbar, search, and add button are glass blocks. Text passing beneath them is diffused and refracted at their edges. Turn on **Settings → Appearance → Reduce transparency** for solid controls. System high contrast also uses solid controls; battery saver leaves your choice in place.

## Sync with Google Drive (optional)

On Android, tap **Connect** in the library toolbar; once connected, use the same control to sync again. A conflict changes it to **Resolve** and opens the choices in Settings. On desktop, choose **Connect** in the library. Sign in to the same account on each device. SnippetDeck uses only its own hidden `appDataFolder`, never your other Drive files. It syncs on app open, after local saves, or on a manual request, without background polling. Offline changes stay local until the next sync.

Independent changes merge, including deletions. Edits to the same trigger, or different snippets claiming the same trigger or alias, require a choice. Export backups from both devices before resolving a conflict. **Use this device** keeps its entire library, including deletions; **Use other device** takes the other device's entire library and applies only when one other device is connected. Disconnecting stops sync on that device and keeps its local library; it does not remove the files already in Drive. Snippets in Drive are **not end-to-end encrypted**. See the [privacy policy](../PRIVACY.md).

## Check for updates

Both apps quietly check the latest stable GitHub release at startup. Android can also check from **Settings → About → Check for updates**; desktop uses **More options → Check for updates**. A download needs your confirmation and is checked against the release SHA-256 digest. Android also checks the APK identity, version and release signing certificate. Desktop opens the verified MSI, DMG, or DEB with the operating system; finish the installation there. Nothing installs unattended.

Desktop versions before v1.8.0 need one manual installation from GitHub Releases to get the in-app check. If a manual check fails, check your connection and try again. On Linux, if there is no `.deb` opener, use the downloaded path shown in the app with your package manager.

## When something goes wrong

| Symptom | What to do |
| --- | --- |
| No text expansion | Check that expansion is enabled. On Android, also enable the accessibility service. On macOS, grant Accessibility access and reopen the app. On Linux, use an X11 session. |
| One editor does not expand | Try a plain text editor. Some fields do not expose Android editing actions or accept simulated input. Desktop navigation, shortcuts and mouse clicks reset the trigger buffer; type the complete trigger again. |
| “Snippet changed while editing” | Keep your draft text, reopen the snippet and reapply the edit. Sync or another save changed the copy you opened; overwriting it silently would lose data. |
| Desktop library could not be opened | The original file stays on disk. Use **Import backup…** to confirm replacement with a valid export; saves, exports and sync are blocked until recovery. |
| Drive conflict | Export each device's library, then choose the device whose whole library should be kept. Sync the remaining devices afterward. |
| Different Google account | Use **Switch Google account** to reset this device's sync history. On Android, revoke the old connection in your Google Account first. Resetting history leaves snippets and cloud files; reconnecting the same account can restore old deletions. |
| Backup or sync is too large | Shorten large expansions or reduce the library. Drive exchanges complete libraries, supports up to 50 device files, and retains deletion history; keep exports before resetting any history. |

[← Project overview](../README.md) · [Development](../CONTRIBUTING.md) · [Architecture](ARCHITECTURE.md)
