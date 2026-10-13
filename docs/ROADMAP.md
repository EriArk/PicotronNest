# Roadmap — evidence-first

This is a delivery sequence, **not a list of features already built**. Status starts as PLANNED for everything. Do not skip feasibility or call an APK working because it opens a splash screen.

## P0 — Runtime feasibility (blocking)

**Goal:** establish whether the owner's official ARM64 Picotron package can execute in an app-owned Android/Linux compatibility environment, and record what prevents it if not.

- Identify Picotron package version, ELF ABI, interpreter, shared libraries, SDL backend and data-file expectations.
- Pin and inspect the PikoNest runtime bootstrap + upstream licensing. Run a minimal independent probe; no editor UI.
- Test headless launch where supported (the manual documents `-x`) and interactive launch separately.
- Prove dependency/loader resolution, runtime home isolation, process ownership and diagnostic capture.
- Do not assume Raspberry Pi binary paths or flags from PICO-8, and never substitute an x86 build.
- Record concrete blockers and decide whether ARM64/native execution is viable before pursuing a fallback.

**Exit:** reproducible launch or a technically specific blocker report. See [runtime spike](RUNTIME_SPIKE.md).

## P1 — Video: see the real Picotron desktop (blocking)

- Trace the actual SDL renderer and window/surface/texture presentation path.
- Prototype a bridge that is resolution-aware and format/stride-aware; **do not reuse 128x128 assumptions**.
- Display live complete desktop frames; correct color channels, aspect ratio, orientation and pointer coordinate transforms.
- Verify ordinary 480x270 mode and an alternative supported mode, with window resize if exposed.
- Quantify frame pacing, copying, input-to-photon delay and app CPU/battery overhead.

**Exit:** human-visible, responsive original Picotron desktop on real Android hardware. A stub image or black frame does not pass.

## P2 — Input and sound

- Route touch taps, drag, long press/right-click, wheel if available, mouse motion/buttons and keyboard text.
- Provide optional handheld cursor mode (e.g. right stick moves pointer; buttons can click), with remapping and accessible exit/keyboard commands.
- Establish SDL keyboard codes, modifiers, Unicode/IME input, key-up/focus-loss recovery.
- Verify SDL audio output, underrun behavior, pause/resume and device routing.

**Exit:** user can navigate the desktop, enter code, use the gfx editor and hear a known sound.

## P3 — Storage and lifecycle

- Run with a persistent `-home` owned by PicotronNest; distinguish staging, installed runtime and user data.
- Safely import/export using Android SAF; no globally writable runtime root or unbounded extraction.
- Verify saving/reopening .p64 projects, replacing user runtime versions without deleting projects, disk-full behavior and app process recreation.
- Reliable exit, relaunch, sleep/wake, Home/resume and session journal.

**Exit:** saved creative work survives restart and ordinary Android interruptions.

## P4 — Self-contained Android APK

- Integrate host UI, compatibility environment and bridge into one APK; no second helper app or independent Termux installation.
- First-run archive picker, validation, progress, useful failures and retry; one launcher icon.
- Test an installation with no pre-existing PikoNest wrapper, bootstrap or Picotron home.
- Audit modern Android execution restrictions (target SDK 29+) rather than relying permanently on legacy target 28.
- Verify bundled third-party licenses, notices and rebuild instructions.

**Exit:** clean install to working Picotron without root/manual shell procedures.

## P5 — Usability, compatibility, readiness

- Test small/wide/portrait/landscape screens, touch-only and controller-only paths.
- Profile performance and verify lifecycle on more than one Android hardware/configuration when possible.
- Improve overlays/controls only from observed usability problems.
- Regression suite; privacy/storage review; reproducible build; user guide; owner review.

**Release gate:** owner must explicitly authorize a public APK Release. A successful local test or source commit is not release permission.

## Ordering rules

- Evidence for P0/P1 is more valuable than a polished mock launcher.
- Reuse PikoNest **by extracting/adapting specific proven modules**, not copying the full app.
- Track unknowns and regression checks; maintain this roadmap and [status](STATUS.md) together.
- If a gate fails, document a smaller experiment or a viable alternative before expanding scope.
