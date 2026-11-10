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
