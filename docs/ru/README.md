<p align="center"><img src="../../desktop/src-tauri/icons/icon.png" width="112" alt="Значок SnippetDeck"></p>

<h1 align="center">SnippetDeck</h1>

<p align="center">Превратите короткий триггер в текст, который часто вводите. SnippetDeck работает на Android, Windows, macOS и Linux; библиотека остаётся на устройстве, пока вы сами не включите синхронизацию.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-3d8060" alt="Android 13+">
  <img src="https://img.shields.io/badge/Windows-x64-3d8060" alt="Windows x64">
  <img src="https://img.shields.io/badge/macOS-Apple_Silicon_%26_Intel-3d8060" alt="macOS Apple Silicon и Intel">
  <img src="https://img.shields.io/badge/Linux-x64_%7C_X11_expansion-3d8060" alt="Linux x64; подстановка в X11">
</p>

<p align="center"><a href="https://github.com/Krablante/snippet-deck/releases/latest"><strong>Скачать</strong></a> · <a href="https://krablante.github.io/snippet-deck/ru/">Сайт</a> · <a href="../../LICENSE">Лицензия MIT</a></p>

<p align="center"><a href="../../README.md"><img src="https://img.shields.io/badge/Language-EN-47795e" alt="English"></a> <a href="README.md"><img src="https://img.shields.io/badge/Language-RU-806d5e" alt="Русский"></a></p>

Введите `!review` и нажмите пробел: SnippetDeck заменит триггер перед курсором сохранённым текстом. Всё после курсора останется на месте. Для сниппета можно задать псевдонимы и подстановки даты или времени. Немедленное нажатие Backspace вернёт триггер, если поле поддерживает это действие.

## Как выглядит

На компьютере библиотека и открытый сниппет находятся рядом. На снимке показаны демонстрационные записи.

<p align="center"><img src="../images/desktop-editor.png" width="900" alt="Редактор на компьютере с примерной библиотекой"></p>

На Android та же библиотека помещается на экране телефона.

<p align="center"><img src="../images/snippet-library.png" width="235" alt="Библиотека на Android"> &nbsp; <img src="../images/snippet-editor.png" width="235" alt="Редактор на Android"></p>

## Начало работы

Выберите файл для своей платформы в [последнем стабильном релизе](https://github.com/Krablante/snippet-deck/releases/latest):

| Платформа | Установщик | Подстановка текста |
| --- | --- | --- |
| Android 13+ | Подписанный `.apk` | Служба специальных возможностей Android |
| Windows x64 | `.msi` | Фоновый агент в трее |
| macOS Apple Silicon / Intel | Соответствующий `.dmg` | Фоновый агент с разрешением специальных возможностей |
| Linux x64 | `.deb` | X11; редактор работает и в Wayland |

Установите приложение, создайте сниппет и введите его триггер с пробелом в поле ввода. Библиотека хранится локально. Перенести её можно резервной копией или подключить необязательную синхронизацию через свою папку Google Drive `appDataFolder`. У SnippetDeck нет собственного сервера, учётной записи, аналитики и фонового опроса. Сборки для macOS пока не подписаны; перед установкой прочтите [заметки по установке](GUIDE.md#установка).

## Документация

Английские тексты находятся на обычных путях проекта. Переводы повторяют те же имена файлов в `docs/<код-языка>/`; новый язык добавляется отдельным каталогом и ссылками навигации. Стикеры категории и языка на каждой странице помогают ориентироваться.

| Категория | English | Русский |
| --- | --- | --- |
| ![Guide](https://img.shields.io/badge/Docs-Guide-47795e) Использование, установка, копии, синхронизация | [Guide](../GUIDE.md) | [Руководство](GUIDE.md) |
| ![Development](https://img.shields.io/badge/Docs-Development-47795e) Сборка и вклад в проект | [Contributing](../../CONTRIBUTING.md) | [Разработка](CONTRIBUTING.md) |
| ![Architecture](https://img.shields.io/badge/Docs-Architecture-47795e) Код, данные, границы | [Architecture](../ARCHITECTURE.md) | [Архитектура](ARCHITECTURE.md) |
| ![Operations](https://img.shields.io/badge/Docs-Operations-47795e) Выпуск и сопровождение | [Operations](../OPERATIONS.md) | [Эксплуатация](OPERATIONS.md) |
| ![Privacy](https://img.shields.io/badge/Docs-Privacy-47795e) Данные и разрешения | [Privacy](../../PRIVACY.md) | [Конфиденциальность](PRIVACY.md) |

В основе SnippetDeck — [Expander](https://github.com/rrajath/expander) Раджата Радхакришнана и других участников. Установленные Android-версии сохраняют совместимость идентификаторов и старых форматов данных.
