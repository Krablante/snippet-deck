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

The trigger is replaced immediately before the cursor; text after it stays put. An immediate Backspace restores the trigger where the target field supports editing. `!help` lists enabled snippets without taking space in your library. Date and time placeholders include `{{date}}`, `{{time}}`, `{{year_short}}`, and `{{week_num}}`.

On desktop, the editor closes while the expansion agent remains in the tray. Reopen it from the tray or app launcher. **More options → Start at login** controls automatic launch; **Text expansion** pauses insertion. Multiline expansions briefly use the system clipboard and restore its previous supported content when possible.

## Back up or move a library

Android: **Settings → Backup & transfer** exports a JSON file or compact text, and imports either after showing the snippet count. Desktop: **More options → Export backup… / Import backup…** uses the same format. An import **replaces the whole local library**, including deletions, only after confirmation. Export the current library before importing another one if you might need both.

The working library stays in each device's app data. Keep backup files somewhere you control; they can contain sensitive text. Old raw-array JSON, v1.0 JSON, and text V1 backups remain readable.

On Android the floating toolbar, search, and add button are glass blocks. Text passing beneath them is diffused and refracted at their edges. Turn on **Settings → Appearance → Reduce transparency** for solid controls. System high contrast also uses solid controls; battery saver leaves your choice in place.

## Sync with Google Drive (optional)

On Android, tap **Connect** in the library toolbar; once connected, use the same control to sync again. A conflict changes it to **Resolve** and opens the choices in Settings. On desktop, choose **Connect** in the library. Sign in to the same account on each device. SnippetDeck uses only its own hidden `appDataFolder`, never your other Drive files. It syncs on app open, after local saves, or on a manual request, without background polling. Offline changes stay local until the next sync.

Independent changes merge, including deletions. If two devices edit the same trigger before exchanging changes, SnippetDeck asks you to choose a copy. Export backups from both devices before resolving a conflict. **Use other device** applies only when one other device is connected. Disconnecting stops sync on that device and keeps its local library; it does not remove the files already in Drive. Snippets in Drive are **not end-to-end encrypted**. See the [privacy policy](../PRIVACY.md).

## Check for updates

Both apps quietly check the latest stable GitHub release at startup. Android can also check from **Settings → About → Check for updates**; desktop uses **More options → Check for updates**. A download needs your confirmation and is checked against the release SHA-256 digest. Android also checks the APK identity, version and release signing certificate. Desktop opens the verified MSI, DMG, or DEB with the operating system; finish the installation there. Nothing installs unattended.

Desktop versions before v1.8.0 need one manual installation from GitHub Releases to get the in-app check. If a manual check fails, check your connection and try again. On Linux, if there is no `.deb` opener, use the downloaded path shown in the app with your package manager.

[← Project overview](../README.md) · [Development](../CONTRIBUTING.md) · [Architecture](ARCHITECTURE.md)
