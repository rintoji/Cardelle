<div align="center">

<img src="src/Cardelle_logo.png" alt="Cardelle" width="280">

# Cardelle

**Drop in images. Drop in a song. Walk away with a cinematic MP4.**

![status](https://img.shields.io/badge/status-alpha-ff9f43?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%2B-007396?style=flat-square)
![UI](https://img.shields.io/badge/UI-Java%20Swing-6C5CE7?style=flat-square)
![renderer](https://img.shields.io/badge/renderer-FFmpeg-000000?style=flat-square)
![platform](https://img.shields.io/badge/platform-Linux%20%7C%20Windows-2E86AB?style=flat-square)

</div>

---

## What is it?

Cardelle is a small desktop tool that turns a stack of images (and a handful of generated color cards) into a music-synced slideshow video with animated transitions. It is built with plain Java Swing — no frameworks, no build system — and uses FFmpeg under the hood for the actual encoding.

The whole editor lives in one modest window: a scrollable row of tilting cards you can drag around, a status bar, and a keyboard-driven control scheme.

> **Status:** alpha. It works, it renders, and it is still being shaped.

## Downloads

Grab the latest build from the [releases page](https://github.com/rintoji/Cardelle/releases/latest), or jump straight to the current alpha (`v0.1-alpha`):

| Platform | File | What you need |
| --- | --- | --- |
| Linux | [Cardelle-x86_64.AppImage](https://github.com/rintoji/Cardelle/releases/download/v0.1-alpha/Cardelle-x86_64.AppImage) | Nothing — a Java runtime is bundled. On older distros you may need `fuse2` for AppImages. |
| Windows | [Cardelle.exe](https://github.com/rintoji/Cardelle/releases/download/v0.1-alpha/Cardelle.exe) | Nothing — a Windows JRE is bundled and unpacks on first launch. |
| Anywhere | [Cardelle.jar](https://github.com/rintoji/Cardelle/releases/download/v0.1-alpha/Cardelle.jar) | Java 17+ installed. The logo is baked into the jar, so the watermark works here too. |

**Linux**

```bash
chmod +x Cardelle-x86_64.AppImage
./Cardelle-x86_64.AppImage
```

**Windows**

Double-click `Cardelle.exe`. The first run unpacks its runtime into a temporary folder, then starts the editor. Windows may show a SmartScreen warning because the alpha build is not code-signed.

**JAR**

```bash
java -jar Cardelle.jar
```

> All three variants still need **FFmpeg and FFprobe** on your `PATH` to render videos. See [Requirements](#requirements).

## Features

- **Drop it in** — drag image files and one MP3 onto the window, or paste from the clipboard with `Ctrl+V`.
- **Color cards** — `Ctrl+N` adds a generated color card. Hues are spread with a golden-angle walk so neighbors never collapse into the same shade of pink.
- **A deck you can touch** — left-drag to reorder with animated reflow and a subtle tilt; right-click to remove a card. Hovering gives each card a moving shine.
- **25 transitions** — slides, wipes, pushes, reveals, blurs, pixelize, shutters, and more. FFmpeg `xfade` transitions are picked randomly without repeating the last one.
- **Music-synced output** — the MP3 duration is measured first, then slide lengths are calculated so the video lands with the track. Renders at **1280×720, 60 fps**.
- **Optional logo watermark** — `Ctrl+L` toggles the Cardelle logo, embedded in every build, in the center of the final video.
- **Full blur mode** — `Ctrl+B` renders with a soft blur over the whole track, for use as a background layer.
- **No guesswork** — live progress bar, a small self-hiding help overlay (`Ctrl+H`), and the finished video opens automatically when rendering completes.

## Build from source

### Requirements

- **JDK 17 or newer** (the code uses Java records)
- **FFmpeg and FFprobe** installed and available on your `PATH`
  - Debian/Ubuntu: `sudo apt install ffmpeg`
  - Arch: `sudo pacman -S ffmpeg`
  - macOS: `brew install ffmpeg`

Easiest path: open the folder in IntelliJ IDEA and run `Visuals.main()`.

Or from a terminal:

```bash
javac -d out src/*.java
cp src/Cardelle_logo.png out/   # keep the logo on the classpath for the watermark
java -cp out Visuals
```

To rebuild the portable jar yourself:

```bash
javac --release 21 -d build/classes src/*.java
cp src/Cardelle_logo.png build/classes/
jar --create --file Cardelle.jar \
    --manifest src/META-INF/MANIFEST.MF \
    -C build/classes .
```

The logo is copied to the **root of the jar** so `getResourceAsStream("/Cardelle_logo.png")` always finds it — no external `src/` folder needed at runtime.

The release builds are produced with a bundled runtime: `jlink` for the AppImage's Linux JRE, and a Temurin Windows JRE inside a 7-Zip self-extracting executable for the `.exe`.

## Make a video

1. Launch the app.
2. Drop images onto the window, or press `Ctrl+V` to paste files.
3. Drop a single `.mp3` onto the window — this becomes the soundtrack.
4. Add color cards with `Ctrl+N` if you want breathing room between images.
5. Reorder by dragging, delete with right-click.
6. Toggle the logo watermark with `Ctrl+L` (off by default).
7. Press `Ctrl+E` for a normal render, or `Ctrl+B` for the blurred version.
8. Name the video, then let it cook. The result is saved to `~/Videos` when that folder exists, otherwise next to your MP3, and opens automatically.

## Controls

| Shortcut | Action |
| --- | --- |
| `Ctrl+H` | Show / pin / close the help overlay |
| `Ctrl+N` | Add a unique color card |
| `Ctrl+V` | Paste images or an MP3 from the clipboard |
| `Ctrl+L` | Toggle the Cardelle logo watermark |
| `Ctrl+X` | Reset the deck and MP3 |
| `Ctrl+E` | Name the video and render |
| `Ctrl+B` | Name the video and render with full blur |
| Left-drag | Reorder cards (with tilt) |
| Right-click | Delete a card |
| Mouse wheel | Scroll the card deck horizontally |

## Under the hood

A render runs in a dedicated thread so the UI stays responsive:

1. `ffprobe` reads the MP3 length.
2. Each slide is rendered to a temporary frame at 1280×720.
3. Frames are chained with FFmpeg `xfade` filters using a randomly chosen transition for each cut.
4. The selected logo (if enabled) is composited over the center of the video.
5. Everything is muxed with the original MP3 into the final `.mp4`.

Temporary files are cleaned up when the render finishes.

## Project layout

```
.
├── src/
│   ├── Visuals.java           # UI, window, shortcuts, render triggers
│   ├── Cards.java             # tilting, shine-y card component
│   ├── MediaManager.java      # slide list, color generation, MP3 state
│   ├── DropSystem.java        # drag & drop file handling
│   ├── TransitionManager.java # transition catalog + logo handling
│   ├── VideoRenderer.java     # FFmpeg pipeline and progress reporting
│   ├── Main.java              # small console entry point
│   └── Cardelle_logo.png      # the mark you see above (also embedded in builds)
├── releases/                  # locally built artifacts (not tracked in git)
└── README.md
```

## Roadmap

- [ ] Transition picker instead of random-only selection
- [ ] Configurable resolution, fps, and per-slide duration
- [ ] Text / caption overlays
- [ ] Export presets for vertical and square formats
- [ ] Bundle FFmpeg so downloads are fully standalone
- [ ] Code signing for the Windows build

---

<div align="center">

Built with Java, Swing, and an unreasonable number of FFmpeg filters.

</div>
