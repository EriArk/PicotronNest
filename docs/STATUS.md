# Project status

**2026-10-13 — planning / zero implementation.**

PicotronNest is an Android wrapper proposal for the **official, user-provided Picotron runtime**. The repository currently contains planning documents, not a working Android launcher, runtime bridge, or APK. No device smoke test has been completed in this repository.

## Grounded upstream facts

- Picotron is a fantasy workstation with its own desktop, editors, apps and virtual filesystem, not simply a PICO-8 cartridge runner.
- The official manual documents SDL2, standard 480x270 and 240x135 video modes, .p64 and .p64.png cartridges, and the host command-line `-home` option.
- The official roadmap lists Raspberry Pi 64-bit support in the Picotron 0.2.x era; the exact currently downloadable archive and executable layout still require inspection of the owner's purchased copy.
- PikoNest has demonstrated an integrated, single-APK, user-imported PICO-8 ARM64 runtime on Android. This is evidence for **PikoNest**, not proof that Picotron works with the same adapter.
- The inspected PICO-8 SDL shim contains a hard-coded 128x128 framebuffer and cannot be copied unchanged for Picotron.

## Current milestone

**P0: runtime feasibility**, followed by **P1: visible interactive desktop**. Consult [the experiment plan](RUNTIME_SPIKE.md), [roadmap](ROADMAP.md) and [acceptance](ACCEPTANCE.md).

## Status vocabulary

- **Planned:** no implementation or test evidence.
- **Experimented:** a reproducible probe exists, with recorded observations.
- **Device-verified:** demonstrated on real Android hardware with the user's official runtime.
- **Product-ready:** passes end-to-end acceptance, migration/recovery and distribution review.

Do not upgrade a status because code compiles, ADB starts an activity, a process exists, or a screenshot is mocked. Record tested device, Android version, Picotron version, archive hash (not archive contents), build hash, command, observed result and remaining unknowns.

## Next action

Inventory the owner's legally obtained Raspberry Pi 64-bit Picotron package in a private local workspace; identify ELF/ABI, dynamic dependencies, launch requirements and SDL behavior without adding proprietary files to Git. Then perform the minimal headless/visible probe before designing a large Android UI.

References: [official manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html), [roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap), [PikoNest](https://github.com/EriArk/PikoNest).

## 2026-10-14 — owner archive inspected (metadata only)

A user-supplied combined PICO-8/Picotron ZIP was inspected outside the repository. Its embedded `picotron_0.3.0d2_raspi.zip` is 7,605,966 bytes (SHA-256 `ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75`), and contains a little-endian ELF64 AArch64 `picotron/picotron` plus `picotron.dat`. Details: [OWNER_RUNTIME_INVENTORY](OWNER_RUNTIME_INVENTORY.md). **The commercial archive has not been committed, and the ELF has not been launched or dependency-tested.** This establishes a concrete candidate for P0 without upgrading P0 or P1 acceptance.
