# Changelog

## Unreleased

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
