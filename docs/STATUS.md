# Project status

**2026-10-23 — P0a static inspection experimented; Android execution not tested.**

PicotronNest is an Android wrapper for the **official, user-imported Picotron runtime**.
The repository now contains a bounded static inspection tool and synthetic
archive regression checks alongside the design documents. There is no working
Android launcher, runtime bridge or APK. No Picotron device smoke test has been
completed in this repository.

## Latest evidence

- Owner package `picotron_0.3.0d2_raspi.zip` contains an AArch64 ELF at
  `picotron/picotron`; its bundled manual identifies itself as `Picotron v0.3.0d`.
- GNU readelf confirms `/lib/ld-linux-aarch64.so.1`, SDL2 and glibc-linked direct
  dependencies, with required GLIBC versions 2.17 and 2.29. Transitive dependency
  closure is not yet established.
- SDL texture/renderer and window-surface functions are imported. This does not
  identify the actual rendering path, frame dimensions or selected backend.
- Read-only ADB inventory: Retroid Pocket Classic, Android 14 / API 34,
  `arm64-v8a,armeabi-v7a,armeabi`. No APK installed or device files changed.
- Reproducible procedure, hashes, tool versions and remaining gates:
  [P0 static inspection](P0_STATIC_INSPECTION.md).

## Grounded upstream facts

- Picotron is a fantasy workstation with its own desktop, editors, apps and virtual filesystem, not simply a PICO-8 cartridge runner.
- The official manual documents SDL2, standard 480x270 and 240x135 video modes, .p64 and .p64.png cartridges, and the host command-line `-home` option.
- The owner's supplied Raspberry Pi archive has now been statically inspected;
  this establishes its specific layout and ABI, not general compatibility with
  other versions or Android.
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

P0b: review and pin the open-source compatibility environment, resolve the SDL2
and glibc dependency closure, and choose an isolated app-owned execution probe.
Use a new PicotronNest lab package and empty disposable home. Establish a modern
Android target execution strategy before packaging; do not inherit PikoNest's
temporary target-28 workaround as an accepted PicotronNest decision. Then run
P0c with process and output evidence before beginning P1 display work.

References: [official manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html), [roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap), [PikoNest](https://github.com/EriArk/PikoNest).

## 2026-10-14 — owner archive inspected (metadata only)

A user-supplied combined PICO-8/Picotron ZIP was inspected outside the repository. Its embedded `picotron_0.3.0d2_raspi.zip` is 7,605,966 bytes (SHA-256 `ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75`), and contains a little-endian ELF64 AArch64 `picotron/picotron` plus `picotron.dat`. Details: [OWNER_RUNTIME_INVENTORY](OWNER_RUNTIME_INVENTORY.md). **The commercial archive has not been committed, and the ELF has not been launched or dependency-tested.** This establishes a concrete candidate for P0 without upgrading P0 or P1 acceptance.
