<div align="center">

<img src="src/Cardelle_logo.png" alt="Cardelle" width="280">

# Cardelle

**Drop in images. Drop in a song. Walk away with a cinematic MP4.**

![status](https://img.shields.io/badge/status-alpha-ff9f43?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%2B-007396?style=flat-square)
![UI](https://img.shields.io/badge/UI-Java%20Swing-6C5CE7?style=flat-square)
![renderer](https://img.shields.io/badge/renderer-FFmpeg-000000?style=flat-square)
![license](https://img.shields.io/badge/license-personal-lightgrey?style=flat-square)

</div>

---

## What is it?

Cardelle is a small desktop tool that turns a stack of images (and a handful of generated color cards) into a music-synced slideshow video with animated transitions. It is built with plain Java Swing — no frameworks, no build system — and uses FFmpeg under the hood for the actual encoding.

The whole editor lives in one modest window: a scrollable row of tilting cards you can drag around, a status bar, and a keyboard-driven control scheme.

> **Status:** alpha. It works, it renders, and it is still being shaped.

## Features

- **Drop it in** — drag image files and one MP3 onto the window, or paste from the clipboard with `Ctrl+V`.
- **Color cards** — `Ctrl+N` adds a generated color card. Hues are spread with a golden-angle walk so neighbors never collapse into the same shade of pink.
- **A deck you can touch** — left-drag to reorder with animated reflow and a subtle tilt; right-click to remove a card. Hovering gives each card a moving shine.
- **25 transitions** — slides, wipes, pushes, reveals, blurs, pixelize, shutters, and more. FFmpeg `xfade` transitions are picked randomly without repeating the last one.
- **Music-synced output** — the MP3 duration is measured first, then slide lengths are calculated so the video lands with the track. Renders at **1280×720, 60 fps**.
- **Optional logo watermark** — `Ctrl+L` toggles the Cardelle logo in the center of the final video.
- **Full blur mode** — `Ctrl+B` renders with a soft blur over the whole track, for use as a background layer.
- **No guesswork** — live progress bar, a small self-hiding help overlay (`Ctrl+H`), and the finished video opens automatically when rendering completes.

## Getting started

### Requirements

- **JDK 17 or newer** (the code uses Java records)
- **FFmpeg and FFprobe** installed and available on your `PATH`
  - Debian/Ubuntu: `sudo apt install ffmpeg`
  - Arch: `sudo pacman -S ffmpeg`
  - macOS: `brew install ffmpeg`

### Run it

Easiest path: open the folder in IntelliJ IDEA and run `Visuals.main()`.

Or from a terminal:

```bash
javac -d out src/*.java
cp src/*.png src/*.jpg out/   # put the logo and sample art on the classpath
java -cp out Visuals
```

### Make a video

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
│   ├── Visuals.java          # UI, window, shortcuts, render triggers
│   ├── Cards.java            # tilting, shine-y card component
│   ├── MediaManager.java     # slide list, color generation, MP3 state
│   ├── DropSystem.java       # drag & drop file handling
│   ├── TransitionManager.java# transition catalog + logo handling
│   ├── VideoRenderer.java    # FFmpeg pipeline and progress reporting
│   ├── Cardelle_logo.png     # the mark you see above
│   ├── inverted.png          # sample art
│   ├── mahoraga.jpg          # sample art
│   └── void.jpg              # sample art
└── README.md
```

## Roadmap

- [ ] Transition picker instead of random-only selection
- [ ] Configurable resolution, fps, and per-slide duration
- [ ] Text / caption overlays
- [ ] Export presets for vertical and square formats
- [ ] A packaged JAR / installer

---

<div align="center">

Built with Java, Swing, and an unreasonable number of FFmpeg filters.

</div>
