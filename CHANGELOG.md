# Changelog

## Unreleased

Unsaved input settings now survive Android Activity recreation, including pointer
mode, axes, raw numeric edits and button bindings. Saving remains explicit.
Owned Android checks also verify nested game-control drafts and one queued import
across screen recreation and Back. [Checks and limits](docs/IN_SESSION_CONTROLS.md).

Folder export always reports that incomplete files may remain when cleanup fails,
even if earlier errors filled the bounded detail list. Expanded owned Android
checks cover provider failures and cancellation after transfer starts.
[Checks and limits](docs/FOLDER_TRANSFER.md).

Android document imports now reject reliable-provider transfer errors at EOF,
preventing a valid cartridge prefix from being silently accepted after a failed
copy. Owned emulator tests cover cancellation and actual process death during
copying and before/after queue publication. [Checks and limits](docs/QUEUED_IMPORT.md).

Product and owned native-fixture builds now support Linux x86-64 alongside Windows
path resolution. Private output directories preserve published candidates; build
receipts record host/tool identities. [Build and emulator evidence](docs/LINUX_BUILD.md).

**Picotron controls** now opens from the controller menu as well as the floating
hamburger. The shared drawer supports D-pad navigation, configured confirm/cancel
and immediate slider changes. First-use guidance and live/closed-session explanations
help users reach settings without quitting. [Checks and limits](docs/IN_SESSION_CONTROLS.md).

**Import cartridge folder** and **Export cartridge folder** transfer nested
collections through Android SAF into separate new folders, with entry/byte/depth
limits and partial-result reporting. Only validated cartridges are copied; loose
assets are skipped. [Checks and limits](docs/FOLDER_TRANSFER.md).

**Help and community** is reachable from touch/controller controls. Website,
Discord discovery on the official website, GitHub reporting and missing-browser
copy fallback sit beside diagnostics. [Checks and redirect dependency](docs/COMMUNITY_LINKS.md).

**Save screenshot** captures the native Picotron frame from touch/controller
controls and writes PNG through Android's document picker. Owned Android tests
cover pixels, rotation, cancellation and failure. [Limits](docs/SCREENSHOTS.md).

Cartridges can be selected from the live overlay and queued outside the active
home. Finish imports after closing Picotron; interrupted attempts require review
and existing files are preserved. [Checks and limits](docs/QUEUED_IMPORT.md).

The in-session host menu offers **Restart Picotron** with an unsaved-work warning,
verified native exit and storage teardown before one relaunch. Failed shutdowns
do not restart. [Checks and limits](docs/IN_SESSION_RESTART.md).

Cartridge import and discovery preserve mixed-case Picotron extensions. PICO-8
imports explain the format mismatch; the library explains skipped PICO-8 and
unindexable cached cartridges. [Checks and limits](docs/CARTRIDGE_DISCOVERY.md).

## 0.1.0-beta.2 - 2026-11-02 prerelease

Native Splore catalog/download/play, source-built libcurl and Mbed TLS, Android
DNS and system certificate trust; owner-selected MIT license. Original PNG
gallery and screenshot ZIP for website posts. Failed sessions now retain their
process receipts for diagnostics. IME Enter and short native input edges are preserved.

See [release notes](docs/releases/0.1.0-beta.2.md).

## 0.1.0-beta.1 — prepared, not published

First product candidate: official user-runtime import, original workstation
display, keyboard/IME/mouse/controller input, configurable translucent touch
gamepad, audio pipeline, persistent home, project transfer/backup, safe recovery,
versioned ARM64 packaging and local diagnostic export.

See [release notes](docs/releases/0.1.0-beta.1.md) for requirements and known limits.
