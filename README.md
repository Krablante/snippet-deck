<p align="center"><img src="desktop/src-tauri/icons/icon.png" width="112" alt="SnippetDeck icon"></p>

<h1 align="center">SnippetDeck</h1>

<p align="center">Turn a short trigger into the text you use every day. SnippetDeck works where you type on Android, Windows, macOS, and Linux; your library stays on your device unless you choose to sync it.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-505477" alt="Android 13+">
  <img src="https://img.shields.io/badge/Windows-x64-505477" alt="Windows x64">
  <img src="https://img.shields.io/badge/macOS-Apple_Silicon_%26_Intel-505477" alt="macOS Apple Silicon and Intel">
  <img src="https://img.shields.io/badge/Linux-x64_%7C_X11_expansion-505477" alt="Linux x64; X11 for text expansion">
</p>

<p align="center"><a href="https://github.com/Krablante/snippet-deck/releases/latest"><strong>Download</strong></a> · <a href="https://krablante.github.io/snippet-deck/">Website</a> · <a href="LICENSE">MIT License</a></p>

<p align="center"><a href="README.md"><img src="https://img.shields.io/badge/Language-EN-505477" alt="English"></a> <a href="docs/ru/README.md"><img src="https://img.shields.io/badge/Language-RU-714857" alt="Русский"></a></p>

Type `!review` and press Space: SnippetDeck replaces the trigger before your cursor with its saved expansion. Text after the cursor stays put. Add aliases, use date and time placeholders, or press Backspace immediately after an expansion to restore the trigger where the target app supports it.

## See it

The desktop editor keeps your library beside the snippet you are editing. These are sample snippets.

<p align="center"><img src="docs/images/desktop-editor.png" width="900" alt="Desktop editor with sample snippets and an open snippet"></p>

On Android, the same library fits a phone screen.

<p align="center"><img src="docs/images/snippet-library.png" width="235" alt="Android snippet library"> &nbsp; <img src="docs/images/snippet-editor.png" width="235" alt="Android snippet editor"></p>

## Get started

Download the file for your platform from the [latest stable release](https://github.com/Krablante/snippet-deck/releases/latest):

| Platform | Installer | Expansion |
| --- | --- | --- |
| Android 13+ | Signed `.apk` | Android accessibility service |
| Windows x64 | `.msi` | Background agent in the tray |
| macOS Apple Silicon / Intel | Matching `.dmg` | Background agent with Accessibility permission |
| Linux x64 | `.deb` | X11; the library editor also works on Wayland |

Install the app, create a snippet, then type its trigger followed by Space in an editable field. The library is local by default. You can move it with a portable backup or optionally sync through your own Google Drive `appDataFolder`. No SnippetDeck server, account, analytics, or background polling is involved. The macOS builds are currently unsigned; see the [installation notes](docs/GUIDE.md#install) before using them.

## Documentation

English lives at the normal project paths. Translations mirror the same filenames under `docs/<language-code>/`; adding another language means adding a directory and navigation links. The category and language stickers on each page show where you are.

| Category | English | Русский |
| --- | --- | --- |
| ![Guide](https://img.shields.io/badge/Docs-Guide-505477) Use, installation, backups, sync, updates | [Guide](docs/GUIDE.md) | [Руководство](docs/ru/GUIDE.md) |
| ![Development](https://img.shields.io/badge/Docs-Development-505477) Build and contribute | [Contributing](CONTRIBUTING.md) | [Разработка](docs/ru/CONTRIBUTING.md) |
| ![Architecture](https://img.shields.io/badge/Docs-Architecture-505477) Code, data, and boundaries | [Architecture](docs/ARCHITECTURE.md) | [Архитектура](docs/ru/ARCHITECTURE.md) |
| ![Operations](https://img.shields.io/badge/Docs-Operations-505477) Release and maintenance | [Operations](docs/OPERATIONS.md) | [Эксплуатация](docs/ru/OPERATIONS.md) |
| ![Privacy](https://img.shields.io/badge/Docs-Privacy-505477) Data and permissions | [Privacy](PRIVACY.md) | [Конфиденциальность](docs/ru/PRIVACY.md) |

SnippetDeck builds on [Expander](https://github.com/rrajath/expander) by Rajath Radhakrishnan and contributors. Installed Android identifiers and legacy data formats remain compatible with existing libraries.
