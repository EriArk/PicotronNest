# Project status

**2026-11-01 — Shared pointer controls integrated into the desktop lab; official input acceptance and the functional beta gate remain open.**

PicotronNest is an Android wrapper for the **official, user-imported Picotron runtime**.
The repository contains a bounded archive inspector and a temporary target-36
Android execution lab. Its owned Linux guest has now run through packaged
PRoot on Retroid and an Android emulator. The official owner-imported ARM64
Picotron now also executes an owned Lua probe on Retroid. There is no product
launcher or product APK. Original desktop frames stream into an Android View;
the minimal pointer path is built and host-tested, with device interaction still
the next gate.

## Latest evidence

- Shared session input: the official-runtime lab now exposes optional direct-touch,
  mouse and explicit cursor-profile/touchpad selection. The same MainActivity
  controls pass an owned API 35 x86_64 check for mode changes, invalid configuration,
  release, recreation and stale/disconnected controls. All fourteen bridge scenarios
  have passing final-APK evidence across collections, with Gboard timeouts retained.
  35 lab and nine archive host tests pass; both APKs build. No new official Picotron interaction or Retroid
  test was performed. Keyboard/IME transport integration is next.
  [Evidence, launch options and limits](SESSION_INPUT.md).
- Cursor action fixture: saved secondary-button and four-direction wheel bindings,
  explicit touch controls, source-owned button chords, repeat suppression and
  focus/removal/recreation release pass through the native receiver. V1 records
  remain readable without rewriting; v2 survives interrupted staging and loads
  in a new host process. All thirteen bridge scenarios pass in one final API 35
  x86_64 run; 35 lab and nine archive host tests pass. Both APKs build. Retroid
  remains untouched, and official-runtime integration/input acceptance remain
  open. [Evidence and next integration](CURSOR_ACTIONS.md).
- Android mouse fixture: absolute movement, primary/secondary buttons, wheel,
  touch/device ownership and focus/removal/resize/recreation cleanup pass through
  real View dispatch to the owned native process. All eleven bridge scenarios
  pass in the final API 35 x86_64 run; 35 lab and nine archive host tests pass.
  An earlier Gboard visibility timeout and passing reruns are retained. The
  official launcher does not select this adapter; physical mouse and Picotron
  consumption remain untested. Retroid was not used. [Evidence](ANDROID_MOUSE.md).
- Secondary-button/wheel transport: independent button edges, fractional two-axis
  scroll, resize release and shared SDL event construction pass on the API 35
  x86_64 emulator. All ten bridge scenarios pass on one final APK, alongside
  35 lab and nine archive tests. The matching ARM64 observer builds; it was not
  installed on Retroid. Android mouse routing and official input consumption
  remain open. The private PNI2 format requires paired sender/observer builds.
  [Evidence, actual screenshot scope and next step](POINTER_WHEEL.md).
- Host-menu fixture: controller/touch menu and keyboard commands, Unicode
  receipt, exit cancellation and one verified native shutdown pass on the API 35
  x86_64 emulator. 34 lab and nine archive tests pass. This menu is not attached
  to official Picotron. [Evidence](HOST_MENU.md).
- The owner will free Retroid for a complete candidate check when the required
  functionality is assembled. [Beta readiness](BETA_READINESS.md) records the
  outstanding import, home, input, audio and lifecycle work; that gate is not met.
- Saved cursor profile fixture: settings survive Activity recreation/rotation
  with one native process, and a new host process loads them from disk.
  Interrupted staging, rollback, unsupported records and stale input are checked
  on the API 35 x86_64 emulator. 33 lab and nine archive tests pass; all eight
  bridge scenarios pass on the final APK across collections. An IME visibility
  timeout and its passing rerun are retained in the evidence.
  Retroid remains untouched; official input acceptance is open.
  [Evidence and limits](CURSOR_PROFILES.md).
- D-pad and simultaneous IME fixture: key/HAT profiles, keyboard/controller
  routing and shared focus cleanup pass on the API 35 x86_64 emulator. The frame
  and cursor fit above docked Gboard; opening/hiding it releases pointer capture.
  Six bridge scenarios, 32 lab tests and 9 archive tests pass. Official Picotron
  and physical controller/IME behavior remain untested. [Evidence](DPAD_AND_IME.md).
- Relative touchpad/stick fixture: fractional cursor motion, explicit primary
  drag ownership, remapped axes/buttons, precision and cancellation pass on the
  Android 15/API 35 x86_64 emulator. Seven down/up pairs reach the native receiver;
  all five bridge scenarios pass, with 31 lab and 9 archive tests passing.
  The official launcher does not attach this adapter; no physical controller or
  Picotron input acceptance is claimed. [Evidence and actual screenshot scope](CURSOR_SPIKE.md).
- Keyboard/Unicode fixture: source-aware modifier ownership, bounded text
  transport and limited Android IME composition/commit reach an owned native
  process on the Android 15/API 35 x86_64 emulator. Cyrillic/CJK/emoji bytes,
  focus release and stale-connection rejection pass; a real emulator screenshot
  is retained. 30 lab tests and 9 archive tests pass. This path is not attached
  to official Picotron. [Evidence and limits](KEYBOARD_SPIKE.md).
- Owned bridge emulator checks PASS: real Android View/socket code plus a shared
  native pointer decoder, Android 15/API 35 x86_64, SELinux Enforcing, target 36.
  Recreation, rotation, dialog focus loss and Home/return retain one native
  process and release held input. Resize-during-drag, partial EOF and abrupt
  exit are checked. **No Picotron ran in these tests.** [Evidence](P1_BRIDGE_TESTS.md).
- Host transport hardening: the shared frame reader rejects partial/invalid
  packets and supports size changes. A real localhost duplex test preserves 40
  rapid press/release pairs while frames flow in the opposite direction.
  Overflow, broken writes and pending input at close are explicit failures.
  29 lab tests pass; this is host evidence, not official-runtime input acceptance.
- P1b.2: lab 0.0.6 adds bounded touch-to-SDL pointer transport, inverse display
  mapping and release on cancel/focus loss/pause. Host tests and APK build pass;
  this version was not installed or screen-tested, at the owner's request to
  leave Retroid available. No real click or drag is claimed. [Scope](P1_DESKTOP.md).
- P1b.1: 67 original frames received from the verified Picotron socket peer;
  the final sequence was drawn in Android. Boot and desktop screenshots were
  inspected privately. Normal exit, host-only census and repeat process launch
  passed. This evidence predates the pointer adapter. [Display evidence](P1_DESKTOP.md).
- P1a: the official desktop uses a 480x270 ARGB8888 streaming texture and SDL's
  software renderer, presenting a 1024x768 window. Complete RGBA frames were
  captured before present and visually inspected locally. SDL_QUIT ends the
  probe normally with no leftover children. [Desktop trace](P1_DESKTOP.md).
- P0c: the unchanged owner Picotron 0.3.0d2 runs twice through packaged PRoot,
  glibc 2.36 and a source-built minimal SDL2 2.32.10. Both launches print distinct
  current-session Lua markers and exit 0. Retroid Pocket Classic, Android 14/API
  34, ARM64; host target 36. Installed APK, runtime/component hashes and final
  host-only process census are checked. [Headless evidence](P0_HEADLESS.md).
- Proprietary executable/data are provisioned separately into private storage;
  neither is bundled in the APK. This is a bounded developer import, not the
  future SAF setup flow. The enforcing x86_64 emulator has not run this ARM64
  probe. Input, audio and user-project persistence remain unproven on device.

- P0b.2: a pinned PRoot tracer/loader and audited minimal native closure execute
  an owned Linux ELF at target 36. The guest reads the current root's unique
  marker (exit 0), and a second launch without that marker fails as expected
  (exit 41). Real guest identity/output/exit, installed hashes and absence of
  leftover guest processes were verified on Retroid ARM64 and the enforcing
  Android 15 x86_64 emulator. [Evidence and PikoNest comparison](P0_LINUX_LOADER.md).
- This uses no installed PikoNest or Termux app. PikoNest's bootstrap, private
  homes and process contracts informed the experiment; its unrelated work was
  preserved. glibc and SDL2 were not part of that earlier probe; P0c adds them.

- Target-36 execution lab: packaged native process, private Bionic fixture via
  the system linker, child tracing and timeout/reaping verified twice on Retroid
  Pocket Classic (Android 14/API 34/ARM64) and an Android 15/API 35 x86_64 emulator.
- Direct private execution succeeded on Retroid but was denied on the enforcing
  emulator. Retroid's SELinux enforcement mode was not readable; do not generalize
  its behavior. Actual child contexts and process evidence were recorded.
- The initial lab contained only owned diagnostics and NDK notices; P0b.2 adds
  the explicitly locked PRoot closure. [Earlier P0b.1 limits](P0_EXECUTION_MODEL.md).

- Owner package `picotron_0.3.0d2_raspi.zip` contains an AArch64 ELF at
  `picotron/picotron`; its bundled manual identifies itself as `Picotron v0.3.0d`.
- GNU readelf confirms `/lib/ld-linux-aarch64.so.1`, SDL2 and glibc-linked direct
  dependencies, with required GLIBC versions 2.17 and 2.29. The minimal headless
  transitive closure is now established; the display experiment is recorded above
  and real audio output remains open.
- Static imports alone did not identify the rendering path; P1a subsequently
  observed the texture/renderer path, dimensions and software backend.
- Initial P0a inventory was read-only; P0b.1 subsequently installed only the
  isolated execution lab and created its disposable private test sessions.
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

**P1a presentation tracing: device-verified in the scoped Retroid experiment.
P1b live Android display has evidence; the pointer bridge has host/emulator
fixture evidence, with official Picotron interaction still unverified.**
Consult [the experiment plan](RUNTIME_SPIKE.md), [roadmap](ROADMAP.md) and [acceptance](ACCEPTANCE.md).

## Status vocabulary

- **Planned:** no implementation or test evidence.
- **Experimented:** a reproducible probe exists, with recorded observations.
- **Device-verified:** demonstrated on real Android hardware with the user's official runtime.
- **Product-ready:** passes end-to-end acceptance, migration/recovery and distribution review.

Do not upgrade a status because code compiles, ADB starts an activity, a process exists, or a screenshot is mocked. Record tested device, Android version, Picotron version, archive hash (not archive contents), build hash, command, observed result and remaining unknowns.

## Next action

When the owner makes Retroid available, install the pointer lab and verify a
real desktop click, drag, boundary mapping and focus-loss release. Do not treat
host tests or a zero-input session as this interaction proof.
The [three-session hardware procedure](RETROID_INPUT_CHECK.md) is prepared.
[Phone/handheld input direction](INPUT_DESIGN.md) is accepted; exact defaults
and physical-device usability remain undecided.
Keep enforcing ARM64 Android execution as
an explicit compatibility gap; the Retroid headless result does not close it.

References: [official manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html), [roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap), [PikoNest](https://github.com/EriArk/PikoNest).

## 2026-10-14 — owner archive inspected (metadata only)

A user-supplied combined PICO-8/Picotron ZIP was inspected outside the repository. Its embedded `picotron_0.3.0d2_raspi.zip` is 7,605,966 bytes (SHA-256 `ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75`), and contains a little-endian ELF64 AArch64 `picotron/picotron` plus `picotron.dat`. Details: [OWNER_RUNTIME_INVENTORY](OWNER_RUNTIME_INVENTORY.md). **The commercial archive has not been committed, and the ELF has not been launched or dependency-tested.** This establishes a concrete candidate for P0 without upgrading P0 or P1 acceptance.
