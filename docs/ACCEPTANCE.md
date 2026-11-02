# End-to-end acceptance

This checklist defines **proof needed**, not features currently present. Start every item as NOT TESTED. Status values: PASS (with evidence), FAIL (reproducible), BLOCKED (dependency not available), NOT TESTED. Code review, mockups or compile success alone never satisfy physical-device acceptance.

## A0 — Environment / official runtime

- [ ] A0.1 Imported archive is the user's legally obtained current compatible Picotron build; version and ELF ABI are recorded.
- [x] A0.2 Scoped P0c lab: proprietary files are separate private imports; APK allowlist and reviewed Git changes contain no runtime/data. [Evidence](P0_HEADLESS.md). Repeat this gate for future artifacts.
- [ ] A0.3 Package import validates contents and rejects traversal/duplicates/oversize, and a rejected import leaves previous setup untouched.
- [x] A0.4 Scoped P0c headless PASS on Retroid Android 14, target 36, no root/Termux. Other Android configurations remain untested for official Picotron; desktop proof is separately scoped under A1.1. [Evidence](P0_HEADLESS.md).
- [x] A0.5 Scoped headless closure, exec-preserved PID/UID, Lua output, two normal exits and final host-only census observed. Interactive lifecycle remains untested. [Evidence](P0_HEADLESS.md).

## A1 — Actual desktop output

- [x] A1.1 Scoped P1b.1 PASS: original desktop displayed by the lab on physical Retroid, verified via private ADB screenshot and frame/process evidence. Owner review remains open. [Evidence](P1_DESKTOP.md).
- [ ] A1.2 Frame output is correct (colors, aspect, crop, orientation, no tearing/stale overlays).
- [ ] A1.3 Normal 480x270 and another supported mode are tested; changing mode or resizing does not corrupt frame mapping.
- [ ] A1.4 Pixel-perfect/integer and fit scaling, where implemented, do not stretch the original ratio.
- [ ] A1.5 The screen remains responsive after repeated navigation, animation, app switching and returning.

## A2 — Real usable input and audio

The lab implements single-touch left-button input and lifecycle release paths.
Host tests and [owned Android emulator fixtures](P1_BRIDGE_TESTS.md) pass.
The updated official-runtime lab was not installed or screen-tested on Retroid
because the owner requested no further screen tests. A2.1 and A2.5 remain NOT
TESTED with official Picotron on hardware. A limited keyboard/text/IME adapter
has [owned emulator-fixture evidence](KEYBOARD_SPIKE.md), including simulated
key/device events and direct calls on the system-created IME connection; it is
disabled in the official launcher. A [relative touchpad/stick fixture](CURSOR_SPIKE.md)
also exercises simulated controller events and explicit primary-button drag;
the [D-pad/IME extension](DPAD_AND_IME.md) checks their simultaneous operation.
The [saved-profile fixture](CURSOR_PROFILES.md) checks settings restoration and
release across Activity recreation/rotation, plus disk loading in a fresh host
process. This does not prove persistence of the Picotron runtime or user home.
The [host-menu fixture](HOST_MENU.md) exercises keyboard/exit access from touch
and synthetic controller input, with native receipt and process completion checks.
The [secondary-button/wheel fixture](POINTER_WHEEL.md) verifies direct transport
delivery and shared SDL event construction. The [Android mouse fixture](ANDROID_MOUSE.md)
adds synthetic View-event routing, source ownership and lifecycle cleanup;
the [cursor action fixture](CURSOR_ACTIONS.md) adds saved secondary/wheel bindings
and explicit touch controls. [Shared session controls](SESSION_INPUT.md) now expose
pointer adapters through an opt-in in the official-runtime lab; their integration
is exercised with an owned process only. The [native keyboard bridge](NATIVE_KEYBOARD.md)
adds shared event/reader tests; the [shared keyboard integration](SESSION_KEYBOARD.md)
adds opt-in channel ownership and IME controls. The
[input bundle](INPUT_BUNDLE.md) checks ordered mixed input, editor keys and
shared session exit in the emulator. Official input consumption, a complete
controller workflow and audio remain open.
No physical mouse, controller, hardware keyboard
or real IME typing acceptance is claimed.

- [ ] A2.1 Touch taps, cursor movement, drag, secondary click and pointer boundaries land at expected Picotron coordinates.
- [ ] A2.2 Hardware mouse and keyboard work, including modifiers, key release and text entry.
- [ ] A2.3 Android soft keyboard/IME permits entering and editing Lua, symbols and filenames; limitations explicitly documented.
- [ ] A2.4 Handheld gamepad can navigate, position the pointer, select/edit objects, summon keyboard and exit; a controller-only path is testable.
- [ ] A2.5 Switching focus, dialogs, Home/return and sleep never leave stuck inputs.
- [ ] A2.6 Music/SFX are audibly heard on hardware; audio remains functional after background/foreground and short sleep/wake.

## A3 — Picotron workflows and persistence

The [storage bundle](STORAGE_BUNDLE.md) adds managed persistent-home binding,
document import and atomic runtime selection. Owned files survive native child
write/read, version replacement and rollback in separate emulator host processes.
These checks do not close the official Picotron workflow items below.

- [ ] A3.1 Open the **real** Picotron code editor; create a small ordinary cartridge and run it.
- [ ] A3.2 Use the real graphics editor; save a native `.p64` project, exit Picotron and re-open it unchanged.
- [ ] A3.3 `.p64.png` opening is checked in the official runtime without rewriting source.
- [ ] A3.4 Picotron settings, desktop and `/appdata` persist through regular app upgrades and process recreation.
- [ ] A3.5 Storage permission loss/import cancellation/disk full do not destroy existing files or silently activate incomplete homes.
- [ ] A3.6 An explicit Android document export/import path is verified if supplied in the current milestone.

## A4 — Lifecycle and clean device

- [ ] A4.1 Fresh Android installation with **only PicotronNest APK plus the user's runtime archive** reaches Picotron (no preinstalled PikoNest/wrapper/helper/Termux).
- [ ] A4.2 One app icon; multiple internal processes are allowed but no second user-installed APK.
- [ ] A4.3 Cold start, repeated launch, graceful exit and return to host all work without duplicate instances.
- [ ] A4.4 Home/background/resume, rotation/insets and screen sleep/wake are exercised.
- [ ] A4.5 Unknown/crashed/interrupted sessions are recovered deliberately without deleting unverified data.
- [ ] A4.6 Modern-target Android executable-loading approach is implemented and verified; legacy target SDK cannot be the final hidden workaround.
- [ ] A4.7 At least two meaningfully different Android device/screen/input combinations are tested before claiming broad compatibility.

## A5 — Distribution and quality

- [ ] A5.1 Reproducible build with pinned upstream dependencies and verified dependency licenses/notice files.
- [ ] A5.2 Security review of archive import, command arguments, SAF, process boundaries and redacted logs.
- [ ] A5.3 Release README describes actual supported devices, requirements and known limitations, rather than aspirational claims.
- [ ] A5.4 Owner has inspected an actual build and **explicitly authorized** an APK GitHub Release.

## Evidence template

```text
Check: A?.?
Outcome: PASS / FAIL / BLOCKED / NOT TESTED
Date:
Device / Android version / ABI:
Picotron version + archive SHA-256:
Host commit + APK SHA-256:
Procedure:
Observed result (automated vs user-verified):
Regression risk / remaining unknowns:
Evidence locations (non-proprietary):
```

See [STATUS](STATUS.md) for current completion, [ROADMAP](ROADMAP.md) for delivery order, and [RUNTIME_SPIKE](RUNTIME_SPIKE.md) for the initial experimental checks.
