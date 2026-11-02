# Project status

**2026-11-02 - [0.1.0-beta.3 is published as a prerelease](https://github.com/EriArk/PicotronNest/releases/tag/v0.1.0-beta.3), with owner authorization, from `2dbdbf68f88c807b5adc785a147a31b1a096736b`. It adds guided setup, version-flexible imports, a cartridge launcher and audio buffering controls. All 12 uploaded assets match local SHA-256; an anonymous public APK download and the tag target were independently verified. Audio listening remains open.**

PicotronNest is an Android wrapper for the **official, user-imported Picotron runtime**.
The repository contains a bounded archive inspector and a temporary target-36
Android execution lab. Its owned Linux guest has now run through packaged
PRoot on Retroid and an Android emulator. The official owner-imported ARM64
Picotron now also executes an owned Lua probe on Retroid. The development APK
now has a separately signed, non-debuggable product candidate that opens Picotron
directly after runtime import. Original desktop frames stream into an Android View;
the shared input bundle has emulator evidence. Official Picotron interaction and actual save/reopen now have scoped Retroid evidence.
Audible output acceptance, broader phone ergonomics and modern-device compatibility
remain open. Native HTTP(S) and Android DNS are integrated; accounts, uploads,
VPN/network-change behavior and raw socket workflows remain tester checks. Public
beta 3 is now published; future releases require owner authorization. Windowed and
fullscreen Splore have scoped Retroid evidence; list maximization is a separate action.

## Latest evidence

- **Beta 3 audio and library:** [implementation and evidence](AUDIO_AND_LIBRARY.md).
  Persistent live buffer modes and sample-count pacing in the open-source SDL
  dummy backend. A button beside the hamburger shows cartridge covers and requests
  native launches. The 45 host checks, 13 tools checks and Android 15 AudioTrack
  fixture pass. Retroid Android 14 runs the updated product without resetting its
  existing home. Four supplied games reached interactive gameplay; a downloaded
  cartridge launched from the product popup. A 25-minute pre-fix session exposed
  underruns and led to a buffer-reprime fix. A separate 123-second Phoenix rerun
  counted one underrun and one adaptation to 100 ms, then exited cleanly.
  Phone listening and broader device/version coverage remain open.

- **Beta 3 onboarding:** [first-run wizard and runtime import](ONBOARDING.md).
  Three explicit steps introduce the app, select the owner ZIP and explain controls
  and saves. Runtime admission now checks ELF architecture/dependencies instead of
  an exact version/hash allowlist. Old installations remain readable, replacement
  and rollback retain the same home, and managed environments are separated by
  both app and runtime identity. The 43 host tests, 13 tools tests and three
  Android 15 owned storage/wizard phases pass; portrait screens were reviewed.
  The handheld is now available: an in-place beta 3 update launched the existing
  official runtime. Fresh-import/replacement/rollback hardware checks remain
  separate. Public beta 2 retains its original import restriction.


- [Splore fullscreen follow-up](SPLORE_FULLSCREEN.md): the unchanged published
  beta 2 on Retroid / Android 14 passed fullscreen toggle, list maximize/restore,
  category/item selection, IME viewport and return to desktop. The native app
  intentionally preserves list geometry when switching workspaces. The previous
  layout warning was mistaken and is withdrawn; no runtime/bridge patch was needed.

- [Networking and Splore](NETWORK.md): source-built HTTP(S) dependencies, Android
  resolver and system trust roots. Product beta 2 downloaded and ran *Corre, Flavio!*
  and *Ball Fall 2* through native Splore. The latter responded to touch D-pad input.
  Native HTTPS fetch succeeded; an expired-certificate endpoint failed. Android
  network fixture passed. The first product session exited 0 with 1790/1790 ordered
  records acknowledged and no remaining child. Original PNGs are in the
  [screenshot gallery](screenshots/README.md); MIT and all dependency notices are
  included. This does not establish account, audio-listening or broad device acceptance.

- [Product candidate and release preparation](RELEASE_CANDIDATE.md): independent
  `art.picotronnest` package, release signature, original icon, versioned manifest,
  private internal components, notices and allowlisted diagnostic export. Retroid
  imported the owner's ZIP through SAF and displayed the original desktop and
  owned showcase project. Git current tree/reachable history contain no runtime
  payloads; release tooling audits APK/source payloads and prepares local attachments.
  Beta 2 is now published; broad tester acceptance remains open.

- [Touch gamepad](TOUCH_GAMEPAD.md): compact/full layouts, two sticks, triggers,
  saved visibility/size/opacity, capability-based Auto and shared player-one
  input with independent touch/physical ownership. Eleven host checks, both APK
  builds and focused Android multi-pointer instrumentation pass. Official
  Picotron receives touchscreen buttons on Retroid; native pause/resume and
  clean exit pass (109 ordered records acknowledged, no remaining child).
  Actual phone ergonomics and physical gameplay acceptance remain with testers.

- [Gamepad and display bundle](GAMEPAD_AND_DISPLAY.md): SDL virtual controllers,
  configurable game buttons, sticks/triggers and four source-owned slots; explicit
  games/pointer modes. Official `btn(..., ..., 1)` receives injected Android
  controller input on Retroid; Start opens native Picotron pause and Guide opens
  the host menu. The complete SDL frame follows the actual texture dimensions
  and streams each presentation in managed mode. Full workflow acknowledged 762
  ordered records; the final first-press fix acknowledged 127, with native exit 0
  and no remaining child. Ten Java transport checks,
  seven headless checks, six desktop checks, owned ARM64 native self-tests and
  both APK builds pass. Physical sticks, multiple players and broad device
  acceptance remain with testers; these results do not establish them.

- [Managed workstation and Retroid workflow](MANAGED_WORKSTATION.md): original
  Picotron 0.3.0d2 on Retroid Pocket Classic, Android 14/API 34, target 36.
  Actual SAF runtime import, official menu/terminal interaction, `.p64` save and
  relaunch/reopen, Home/resume with the same PID, normal quit with receipts,
  interrupted-session recovery, cartridge import/export and home backup/restore.
  Input bindings now have a settings UI. Managed launches open directly into
  immersive Picotron with two translucent icons and a hidden controls drawer.
  Two focused host checks and both APK builds pass. Physical controller buttons,
  audible output and broad device compatibility are not established. Older
  entries below describe the evidence available in their respective increments.

- Audio/session bundle: callback PCM converts to 48 kHz S16 stereo and reaches
  an authenticated Android AudioTrack receiver, with focus/duck/background rules
  and bounded buffering. Managed sessions skip diagnostic probes, remove the
  20/30/35-second timers and 16-launch quota, reuse a verified environment and
  run with a foreground notification. Icon launch enters setup or the active
  workstation. Both APKs compile; one focused PCM protocol test passes.
  No emulator suite, physical session or listening check was run this increment.
  [Implementation, artifacts, limits and reproduction](AUDIO_AND_SESSIONS.md).
  Per owner direction, deep/physical tests belong to the owner/testers; development
  proceeds with builds and short critical checks. Next: user-facing input
  configuration, project transfer/backup and session recovery.
- Storage bundle: the shared setup screen imports a pinned owner runtime via
  Android documents; separate objects, active/previous pointers and home are
  protected by a cross-process lease and conservative unfinished-session marker.
  The official desktop launcher can bind the durable home without changing
  native `-home` semantics. 39 lab and nine archive tests, three Android storage
  phases and all 17 input scenarios pass. Owned native processes write/reopen a
  preserved file across runtime replacement; this is not real `.p64` acceptance.
  The owner's archive also passes the new importer privately on Windows.
  [Evidence, screenshot, limits and reproduction](STORAGE_BUNDLE.md).
  Retroid remains untouched. The subsequent audio/session bundle is recorded above.
- Integrated input bundle: keyboard, IME and pointer traffic now share one bounded,
  acknowledged native sequence. The shared panel adds source-owned editor keys
  and session commands; quit waits for real process completion and matching
  delivery receipts. Rapid Ctrl-clicks, mixed Shift ownership, repeat/cancel,
  Unicode/deletion, focus/recreation and disconnect are checked together. Visual
  review caught and fixed IME panning that hid the toolbar. 38 lab and nine archive
  host tests pass; both APKs build. See [the consolidated evidence](INPUT_BUNDLE.md)
  for final artifacts, full 17-scenario collection and actual screenshot scope.
  Retroid and official Picotron were not tested in this increment. Next is the
  persistent-home and safe runtime-import bundle, now recorded above.
- Shared keyboard session: the lab now exposes `keyboard_controls`, an authenticated
  channel owner and Show keyboard control shared with the fixture. The keyboard
  PID must match the frame peer before input is attached. Cancellation, wrong PID,
  failed writes, recreation, stale IME rejection and final receipts pass on API 35
  x86_64. Visual validation caught and fixed IME reopening/viewport errors after
  recreation. All fifteen scenarios pass in one final-APK collection; 36 lab and
  nine archive tests pass, and both APKs build. No new official Picotron or Retroid
  test was performed. The subsequent input bundle above addresses ordering.
  [Evidence, screenshot scope and remaining work](SESSION_KEYBOARD.md).
- Native keyboard bridge: an opt-in SDL observer endpoint now shares bounded
  key/text event construction and nonblocking record reading with owned tests.
  The API 35 x86_64 keyboard scenario passes ten check groups, including repeats,
  all eight modifier bits, UTF-8, every partial-record split and EOF cleanup.
  Live input produces 15 SDL event structures with no held keys at exit. All
  fourteen bridge scenarios pass in one final-APK collection. 36 lab
  and nine archive host tests pass; both APKs build with matching source hashes.
  The official Android session does not open the channel yet; real SDL/Picotron
  consumption, channel lifecycle and modifier-plus-pointer ordering remain open.
  Retroid was not touched. [Evidence, setup correction and next step](NATIVE_KEYBOARD.md).
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
P1b official interaction, save/reopen and normal quit now have scoped Retroid
evidence. P2 includes shared input, settings and native cartridge controller
routing with injected-event evidence. P3 storage and recovery have a real document-picker workflow.**
Consult [the experiment plan](RUNTIME_SPIKE.md), [roadmap](ROADMAP.md) and [acceptance](ACCEPTANCE.md).

## Status vocabulary

- **Planned:** no implementation or test evidence.
- **Experimented:** a reproducible probe exists, with recorded observations.
- **Device-verified:** demonstrated on real Android hardware with the user's official runtime.
- **Product-ready:** passes end-to-end acceptance, migration/recovery and distribution review.

Do not upgrade a status because code compiles, ADB starts an activity, a process exists, or a screenshot is mocked. Record tested device, Android version, Picotron version, archive hash (not archive contents), build hash, command, observed result and remaining unknowns.

## Next action

Collect beta 2 tester results for sound, physical controllers, phone ergonomics
and network transitions. The suspected Splore fullscreen layout defect was
resolved as expected native behavior by the follow-up check. Retroid was released
by the owner for the scoped checks recorded above; deep acceptance remains with
the owner/testers. Enforcing ARM64 Android execution remains an explicit
compatibility gap; Retroid evidence does not close it.

References: [official manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html), [roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap), [PikoNest](https://github.com/EriArk/PikoNest).

## 2026-10-14 — owner archive inspected (metadata only)

A user-supplied combined PICO-8/Picotron ZIP was inspected outside the repository. Its embedded `picotron_0.3.0d2_raspi.zip` is 7,605,966 bytes (SHA-256 `ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75`), and contains a little-endian ELF64 AArch64 `picotron/picotron` plus `picotron.dat`. Details: [OWNER_RUNTIME_INVENTORY](OWNER_RUNTIME_INVENTORY.md). **The commercial archive has not been committed, and the ELF has not been launched or dependency-tested.** This establishes a concrete candidate for P0 without upgrading P0 or P1 acceptance.
