# SnippetDeck Android app icon assets

The SnippetDeck icon uses an ink squircle, chalk curly braces `{ }`,
and a lavender placeholder bar.

## File layout

This directory mirrors the standard `app/src/main/res/` structure. Copy each
folder into an Android project's `res/` directory to reuse the assets.

```
mipmap-mdpi/             ic_launcher.png (48), ic_launcher_round.png (48), ic_launcher_foreground.png (108)
mipmap-hdpi/             ic_launcher.png (72), ic_launcher_round.png (72), ic_launcher_foreground.png (162)
mipmap-xhdpi/            ic_launcher.png (96), ic_launcher_round.png (96), ic_launcher_foreground.png (216)
mipmap-xxhdpi/           ic_launcher.png (144), ic_launcher_round.png (144), ic_launcher_foreground.png (324)
mipmap-xxxhdpi/          ic_launcher.png (192), ic_launcher_round.png (192), ic_launcher_foreground.png (432)
mipmap-anydpi-v26/       ic_launcher.xml, ic_launcher_round.xml      (adaptive icon definition)
values/                  ic_launcher_background.xml                  (background color: #282832)
play-store/              ic_launcher-playstore.png (512x512)         (Play Console listing)
source/                  icon.svg, icon-foreground.svg               (master vectors)
```

## Adaptive icon composition

Android 8+ uses adaptive icons: a foreground layer + background layer that
the system composites and masks (circle, squircle, teardrop, etc.) based on
the launcher.

- **Background:** solid `#282832` (ink), defined as a color resource in
  `values/ic_launcher_background.xml`.
- **Foreground:** `ic_launcher_foreground.png` per density — braces and the
  lavender bar on a transparent canvas. Content is laid out within Android's
  66dp/108dp safe zone so any system mask shape stays clear of the design.

The legacy bitmaps (`mipmap-*/ic_launcher.png` and `_round.png`) are pre-shaped
(squircle / circle) and used by pre-O devices and any host that ignores the
adaptive XML.

## Manifest

Reference the icon in `AndroidManifest.xml` as you normally would:

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ...>
```

## Source

Master vectors live in `source/`. Edit `icon.svg` (used for legacy and Play
Store rasters) or `icon-foreground.svg` (adaptive foreground) and re-export
PNGs at the listed sizes.

## Colors

| Token            | Value     | Use                          |
|------------------|-----------|------------------------------|
| Ink              | `#282832` | Background / squircle fill   |
| Chalk            | `#F6F3ED` | Braces stroke                |
| Lavender         | `#C8C8EE` | Placeholder bar accent       |
