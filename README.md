# PicotronNest

**The real Picotron fantasy workstation, running on Android.**

**Status: early feasibility research — no working APK or public download yet.**

[Picotron](https://www.lexaloffle.com/picotron.php) already has its own desktop, code editor, drawing tools, music tools, apps and cartridges. PicotronNest aims to bring that **actual workstation** to Android phones, tablets and gaming handhelds — not recreate those tools in a separate interface.

The intended result is simple: install **one Android APK**, import your own compatible, purchased Picotron runtime, open Picotron's desktop and create or play as you normally would. No root, Termux or second helper application as a final-user prerequisite.

## What PicotronNest is aiming for

- **Original Picotron** — its real desktop, windows, tools and `.p64` / `.p64.png` cartridges.
- **Android input that makes sense** — touch, mouse, physical keyboard, Android soft keyboard and gamepad-controlled pointer/shortcuts.
- **A proper screen** — crisp pixels and correct proportions across different Android handheld and phone screens.
- **Your files stay yours** — a persistent Picotron home, with safe backup/import/export paths.
- **An unobtrusive wrapper** — Android handles setup, the runtime bridge, input, audio and lifecycle; Picotron handles the actual workstation.

PicotronNest **does not include Picotron**. Users must obtain their own licensed copy from Lexaloffle. The feasibility work is investigating the official **Raspberry Pi 64-bit** build; compatibility, supported Android versions and devices are not yet verified.

## Where development actually stands

This repository currently contains **design documents, a static runtime-package
inspector and a temporary Android execution lab**, not a finished runner. The
owner's Raspberry Pi package is ARM64. An owned diagnostic fixture has run with
target API 36 on Android; **Picotron itself has not yet run**. See the
[P0a inspection](docs/P0_STATIC_INSPECTION.md) and
[P0b.1 execution findings](docs/P0_EXECUTION_MODEL.md). The first two hard gates are:

1. **P0 — run the official Picotron ARM64 binary** within a safe, app-owned Android/Linux compatibility environment.
2. **P1 — show the real, interactive Picotron desktop** on an Android screen.

The existing PICO-8 Android video bridge hardcodes 128×128 output; Picotron requires its own verified SDL video path. That is why we are testing execution and rendering before building a polished frontend.

The current plan then covers input/audio, saved data, a self-contained APK and real device acceptance. Nothing is labeled implemented until it has been demonstrated.

## Project documents

| Document | Purpose |
| --- | --- |
| [Product vision](docs/PRODUCT_VISION.md) | What PicotronNest should feel like and what it must not become |
| [Status](docs/STATUS.md) | Verified progress, known facts and the next task |
| [Roadmap](docs/ROADMAP.md) | P0–P5 delivery order and exit gates |
| [Architecture](docs/ARCHITECTURE.md) | Runtime, display, input, audio, storage and Android boundaries |
| [Runtime feasibility spike](docs/RUNTIME_SPIKE.md) | Reproducible P0/P1 investigation |
| [Acceptance](docs/ACCEPTANCE.md) | Device-proven pass/fail checklist |
| [Reuse and provenance](docs/REUSE_AND_PROVENANCE.md) | Safe reuse from PikoNest and upstream sources |
| [Agent instructions](AGENTS.md) | Developer/Codex guardrails and priorities |

## Related work

PicotronNest is inspired by the separate [PikoNest](https://github.com/EriArk/PikoNest) project, whose Android runtime work offers useful patterns for a single APK, official-user-runtime import and process recovery. Picotron is **not** PICO-8: this project does not depend on PikoNest's workshop or PICO-8 file formats.

Relevant upstream references: [official Picotron manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html), [Picotron roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap) and the [PICO-8 Android wrapper](https://github.com/Macs75/pico8-android) used in PikoNest research.

---

PicotronNest is an independent, unofficial project and is not affiliated with Lexaloffle Games. Picotron and its proprietary binaries/assets are not redistributed here.
