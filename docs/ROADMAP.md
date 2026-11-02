# Roadmap — evidence-first

This is a delivery sequence, **not a list of features already built**. As of
2026-11-02, scoped official execution, display, interaction and save/reopen have
Retroid evidence. Input configuration, project transfers and recovery are
implemented. Native cartridge controller routing and texture-sized display now
have [scoped Retroid evidence](GAMEPAD_AND_DISPLAY.md). Configurable
[phone game touch controls](TOUCH_GAMEPAD.md) are integrated with focused Retroid
checks. [Product packaging and matching tester instructions](RELEASE_CANDIDATE.md)
now prepare beta 2 with [native Splore networking](NETWORK.md), Android DNS and
HTTPS trust. The catalog, downloads and two games have Retroid evidence. Account
operations, network transitions and broad hardware acceptance remain open.
Beta 2 was published with owner authorization on 2026-11-02.

## Delivery contract

The owner released Retroid for scoped checks on 2026-11-02.
[Beta readiness](BETA_READINESS.md) tracks that handoff separately from hardware
acceptance and publication approval. A scoped local-project candidate is prepared;
this is not general device or full online-workstation acceptance.

Owner direction (2026-11-02): deliver complete functional bundles with builds and
short critical checks. Deep regressions and physical acceptance belong to the
owner/testers. Those pending checks do not block independent implementation.

PicotronNest supplies an Android host and reviewed compatibility components.
The user separately obtains and imports their own official Picotron copy.
Picotron's executable, data, desktop and tools stay official; no source port or
replacement workstation is planned. No purchased files are bundled in our APK.
Runtime updates and the persistent user home have separate lifetimes.

## First development increments

| Increment | Deliverable and exit evidence | State |
| --- | --- | --- |
| P0a — Inspect owner package | Reproducible static inspector; exact hashes, ELF/loader/dependencies, import hints; hostile ZIP regression fixtures | Experimented: [inspection record](P0_STATIC_INSPECTION.md) |
| P0b — Prepare isolated execution | Pin and review open-source loader/userland/SDL dependencies; resolve transitive closure; create a separate PicotronNest lab namespace and disposable home; document modern-target strategy | Scoped headless closure verified: PRoot + glibc + minimal SDL2; enforcing ARM64 compatibility remains open |
| P0c — Prove process execution | Owned headless probe using the package's documented syntax; capture actual Picotron PID, stdout/stderr, exit and repeat-launch evidence on ARM64 Android | Device-verified on Retroid: [P0c evidence](P0_HEADLESS.md) |
| P1a — Trace actual presentation | Observe which SDL calls present frames and record dimensions, stride, format and ownership | Verified on Retroid: [trace and captured desktop](P1_DESKTOP.md) |
| P1b — Show and interact | Original live desktop on Android, one correctly mapped pointer click, shutdown and restart | Scoped device evidence: official menu/terminal interaction, save/reopen, exit and relaunch; see [managed workstation](MANAGED_WORKSTATION.md) |

P0a is not completion of P0. P0b must not borrow an installed PikoNest app's
private runtime or replace its package. Inspect modern-target constraints during
P0b; defer only product integration to P4. Each increment records its commands,
evidence and unresolved questions before expanding the implementation.

P0b.1 has [execution-policy observations](P0_EXECUTION_MODEL.md); P0b.2 adds
[packaged PRoot and an owned Linux guest](P0_LINUX_LOADER.md) on physical ARM64
Android and the enforcing x86_64 emulator. P0c adds the minimal glibc/SDL2
closure and two official Picotron headless launches on Retroid. This satisfies
the scoped P0 process gate. P1a and P1b.1 subsequently prove original desktop
presentation and Android display. Official pointer interaction and native save/reopen are now recorded in the managed-workstation evidence.

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

The owner accepted the [cross-device input direction](INPUT_DESIGN.md):
capability-based profiles and shared release/coordinate rules. Exact default
mappings remain undecided. [Keyboard/Unicode fixture work](KEYBOARD_SPIKE.md)
and [relative touchpad/stick checks](CURSOR_SPIKE.md), extended by
[D-pad with simultaneous IME](DPAD_AND_IME.md) and
[saved profiles/Activity restoration](CURSOR_PROFILES.md) and
[secondary/wheel action bindings](CURSOR_ACTIONS.md), prepare limited adapters
while hardware is reserved; they do not close the
[official-runtime input check](RETROID_INPUT_CHECK.md).
The [shared session controls](SESSION_INPUT.md) now expose opt-in pointer modes
in the official-runtime lab. The [native keyboard endpoint](NATIVE_KEYBOARD.md)
prepares event construction and record reading; the [shared keyboard owner](SESSION_KEYBOARD.md)
now integrates opt-in Android channel/lifecycle and IME access. The
[integrated input bundle](INPUT_BUNDLE.md) adds a single acknowledged event
order, touch editor keys and shared menu/verified process exit. Official SDL
consumption still requires hardware evidence; product input configuration remains open.
The [audio/session bundle](AUDIO_AND_SESSIONS.md) implements callback PCM output
through Android AudioTrack and focus/background handling. It is build-checked
with a focused transport test; listening and interruption acceptance are pending.

- Route touch taps, drag, long press/right-click, wheel if available, mouse motion/buttons and keyboard text.
- Provide optional handheld cursor mode (e.g. right stick moves pointer; buttons can click), with remapping and accessible exit/keyboard commands.
- Establish SDL keyboard codes, modifiers, Unicode/IME input, key-up/focus-loss recovery.
- Verify SDL audio output, underrun behavior, pause/resume and device routing.

**Exit:** user can navigate the desktop, enter code, use the gfx editor and hear a known sound.

## P3 — Storage and lifecycle

The [storage bundle](STORAGE_BUNDLE.md) now provides pinned runtime document
import, staged activation/rollback, persistent desktop-home binding and an exclusive
session lease. Owned Android preservation/failure checks pass. Real Picotron
save/reopen, project export and reviewed recovery remain open. Managed sessions
now have no diagnostic duration or launch quota, reuse their verified environment
and run with a foreground service; long-session behavior awaits tester acceptance.

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
