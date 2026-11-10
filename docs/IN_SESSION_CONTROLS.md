# Discoverable in-session controls

Issue: [#7](https://github.com/EriArk/PicotronNest/issues/7). This describes the
unreleased development build, not the published beta 3 APK.

## Navigation and behavior

The existing floating **Picotron controls** (hamburger), **Your games**, and
keyboard icons remain small shortcuts. The first controls visit has a tappable
hint; opening the drawer dismisses it persistently. Long-press tooltips identify
the icons afterward. No edge-swipe recognizer was added: a gesture starting at the
Picotron canvas edge remains available for drawing and dragging.

The controller host menu now includes **Picotron controls**, opening the same
drawer as touch. Guide opens the host menu in game mode; the configured menu key
opens it in pointer mode, and Android Back remains available. The drawer adds a
**Your games** shortcut to the existing library and retains **Settings and projects**.
It does not create a second set of settings or another game library.

Previously, `MainActivity` swallowed controller events whenever the drawer was
open; only hardcoded B/Guide could close it. The shared drawer now handles:

- D-pad up/down moves focus through available actions and scrolls them into view.
- The configured host-menu confirm key activates buttons and switches.
- Left/right changes the selected slider, applying and saving its value immediately.
- Configured cancel/menu or Guide closes the drawer; Android Back also closes it.

Commands require a matching device/key down/up pair. Repeats, unpaired releases,
cancelled events and foreign-device releases do not activate actions. Focus loss
clears held-key state. Drawer key/joystick events are consumed before gameplay;
opening/closing releases existing input and restores desktop focus on return.
Touch-only hold controls remain touch-only, rather than exposing inert controller
focus targets. Nested Android dialogs retain their standard Android navigation;
custom drawer bindings are not a global remap of all Android screens. HAT-only
joystick navigation is still not implemented by the host menu/drawer.

The transition also handles Android touch mode: controller focus is explicitly
requested after using touch. A delayed host-menu dismiss callback no longer takes
focus away from a newly opened drawer. Focused buttons have a visible highlight.

## What needs a closed session

The drawer, running-workstation management screen and project-transfer screen
explain the distinction:

- Pointer/touch-gamepad and sound-buffer controls apply live.
- Saved input bindings apply on the next Picotron launch.
- Queuing/reviewing cartridge imports, help and diagnostics are available while running.
- Runtime replacement/rollback, applying queued imports, folder transfer, export
  and backup require closing Picotron. Existing storage/process guards still enforce this.

**Projects and backups** is now reachable from management during a session so its
queue/review actions are usable. Opening management itself does not request shutdown.

## Validation — 2026-11-10

Build the Java-only owned fixture as in [SCREENSHOTS.md](SCREENSHOTS.md), then:

```sh
python3 -B experiments/execution-model/run_screenshot_tests.py \
  --adb /path/to/adb --serial emulator-5582 --mode controls
```

Fresh collection `be44f0b6-430e-4490-9d45-1edb0b27ce86` passes on a dedicated
Android 11/API 30 x86_64 emulator (`sdk_gphone_x86_64`). Three fixture groups cover:

1. First-use hint and touch hamburger reach the shared drawer. A left-edge drag
   produces ordered pointer press/move/release packets and does not open controls.
2. Guide/host-menu navigation reaches the same drawer after touch. Remapped X/Y
   confirm/cancel, D-pad navigation, switches and slider persistence work. Unpaired,
   cancelled and wrong-device releases do not activate an action; hardcoded B does
   not close a remapped drawer. Drawer navigation emits no pressed gamepad state.
3. A real dialog focus transition invalidates a held cancel key. Settings dispatches
   the existing management intent without requesting session shutdown. Back closes
   the drawer and restores desktop focus. The real drawer screenshot was inspected.

The first fixture runs exposed missing focus after switching from touch; the final
run includes the corrected focus handling. The same APK passes six screenshot groups
(`02c71fa6-7363-4dc4-9f82-3c257af93cab`) and three community groups
(`f3b07e6f-dc40-433a-bd9b-e55fe60696c5`) after the menu changes.

All 57 production Java sources compile/dex against API 36 (minimum API 26); 20 Android
test sources compile. Candidate-manifest and session-exit host regressions are recorded
with the increment. Private reports, source/APK hashes and owned preview remain under
`.local/screenshot-tests`.

These are synthetic View/controller events and owned Java transport acknowledgements,
not proof of SDL consumption, a physical controller, or a live official Picotron
session. The fixture directly attaches a session-menu stand-in and intercepts management
navigation; real `LabSession` attachment, management lifecycle, landscape/cutout layout,
IME, accessibility and physical-device return remain acceptance work. The USB device
was still ADB-unauthorized; no official-runtime device run or product APK release is
claimed.

## Settings and queue Activity recreation — 2026-11-10

Input settings now retain their unsaved pointer kind, axes, numeric text and all
pointer/menu bindings in Android saved instance state. Raw temporary input such
as `-` survives recreation; validation and persistence still happen only on
**Save input settings**. Leaving the Activity without saving does not commit the
new draft. Opening **Game controls** and returning keeps the parent's draft.

The owned fixture reproduced lost pointer selections before the fix, collection
`1c7c1135-d01d-44ca-aca0-012259562dac`. With the fix, collection
`bbe4d9b8-db75-463f-ac55-62a92e679108` passes four groups on the dedicated Android
11/API 30 x86-64 emulator, SELinux Enforcing, target 36:

1. Real `InputSettingsActivity.recreate()` restores unsaved choices, raw invalid
   text and remapped bindings without writing preferences.
2. The actual nested `GamepadSettingsActivity` retains its draft on recreation;
   Android Back returns to the input draft without implicitly saving either.
3. An explicit save persists the exact selected values. Android Back returns to
   the same owned workstation Activity, whose session-exit state stays RUNNING.
4. The real overlay opens `ProjectFilesActivity` and one intercepted document
   picker. Recreating before the picker result does not launch another picker.
   Recreating during a blocked owned content-URI read retains the worker; after
   release exactly one queued cartridge contains the expected bytes. Recreation
   after completion, picker cancellation and Back preserve that queue and return
   to the same owned workstation without a second provider open. The fixture
   discards only its own queued copy after verification.

Build the Java-only fixture as above, then run:

```sh
python3 -B experiments/execution-model/run_screenshot_tests.py \
  --adb /path/to/adb --serial emulator-5582 --mode management
```

Fixture APK SHA-256:
`0d43b49a69379345ab6f7b13d72e6a2758b15a7f6170dc004307139123f90a67`.
The fixture starts from its own empty queue and uses only its own preferences,
bytes and Java input acknowledgements. Picker results are injected; settings and
project Activities and their recreation/Back navigation are real. This does not
prove a running native/official session, process-death draft restoration, external
DocumentsUI behavior, export-source selection, or the live `RuntimeSetupActivity`
management screen. Those boundaries remain acceptance work; no device-proven
status is inferred. Retroid testing remains deferred by the owner.

The private ARM64 candidate is
`.local/management-tests/candidate/PicotronNest-0.1.0-beta.3-arm64.apk`, SHA-256
`0ce306a24747da76459fd2299c0338a7f5e76b6da6b2aea499540e068efa957e`.
Its 93 source hashes, 44 payload entries and original signing identity verify;
both candidate-manifest tests pass. The version remains beta 3 but this is a new
local development artifact, not the published beta-3 APK. No product install on
Retroid or APK/Release publication was performed.

The same APK passes all three existing controls groups in collection
`9cadf4ea-06de-45bc-a451-1804c1d27ede`. An earlier collection
`9a12657b-9ff4-4cfc-8a2f-05fdaeed112c` stopped at screenshot collection with Android's
UiAutomation service-registration error after two groups passed. Soft-reboot retry
attempts lost ADB transport; a full restart of only the owned emulator produced the
passing collection. No controls behavior was changed to obtain that result. The
runner now retains instrumentation stdout/stderr when Android returns a failure.
