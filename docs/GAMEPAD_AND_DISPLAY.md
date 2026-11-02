# Native game controls and display sizing

2026-11-02. Integrated into the managed development APK; no public release.

## Behavior

The official runtime now receives Android controller snapshots through SDL's
virtual game-controller API. Four source-owned slots carry 15 logical buttons
and six axes. SDL sees four stable logical controllers from initialization;
Android device removal clears its slot instead of recreating the SDL device.
This preserves the first press after connection or menu return. D-pad keys/HAT
axes, both sticks, shoulders and analog/digital
triggers use Android capabilities. Game button mappings can be captured and
saved in Input settings -> Game controls; changes apply next launch. The mapping
is shared across controllers, not selected by a handheld model name.

The floating controls switch between games and pointer input. Selecting the
configured controller pointer also switches out of games. In games, Start goes
to Picotron and Guide opens the host menu. Android Back and the overlay remain
available. Menu entry, focus loss, mode changes, device removal and session loss
release controller state. Opening the overlay blocks underlying controller input.
The ordered authenticated channel carries complete snapshots and reset/quit
ordering; invalid slots, buttons, axes and reserved bytes fail closed.

SDL is rebuilt from the same pinned 2.32.10 source with joystick and virtual
joystick support. Its CMake virtual driver requires the HIDAPI subsystem;
HIDAPI joystick discovery stays disabled. The ELF closure remains libc, libm,
libdl and libpthread; source locks and bundled license/embedded notices remain.
The rebuilt SDL SHA-256 is
`0954cc1f4e65ca549a68636d59024c3d05b23d500e4bc377ee78867c8d909adb`.
The dummy SDL window never receives OS keyboard focus, so its controller
background-event hint is enabled. Android still owns focus and input release;
this does not route Android background input into Picotron.

The managed SDL window follows the actual streaming texture dimensions instead
of the dummy driver's 1024x768 fullscreen mode. Android fits that complete frame
once, preserving aspect ratio. Managed capture now follows each SDL presentation
instead of the diagnostic 100 ms throttle. The diagnostic path retains its
previous timing. There is no fixed 480x270 crop and no proprietary pixel change.
Aspect-ratio bars can still exist on differently shaped physical screens.

## Scoped Retroid evidence

Retroid Pocket Classic, Android 14/API 34, ARM64, APK target 36, official imported
Picotron 0.3.0d2. Existing runtime and home were retained through `install -r`.

Full workflow session `1ede3dd6-3de9-404e-9a34-bbca8199c9c6`, native PID 19088,
on the intermediate `e4438e7c...` APK:

- SDL joystick and game-controller initialization both returned 0. A virtual
  device was recognized as a game controller; detach/reconnect also occurred.
  The final implementation retains logical SDL devices, as described above.
- Official desktop/menu/terminal input remained correctly mapped after the
  SDL frame changed to 480x270. The image filled the device width at its native
  aspect ratio, without the former internal padding.
- The owned test cart used `_update()` and `btn(button, 0, 1)`, explicitly excluding
  keyboard input. Injected Android controller events produced official receipts
  `PN_GAME=4,`, `1,`, `5,`, `13,`, `14,`, `15,`, `6,`, each followed by an empty
  release state. Native Picotron determines the final logical button assignment.
- Start opened Picotron's own pause menu; Guide opened the Android host menu.
  Actual unedited screenshots are retained privately. This is injected-event
  evidence, not physical button or analog-stick acceptance.
- 24,385 frames were captured from 24,386 presentations over 393,478 ms (including
  startup). This proves removal of the 10 Hz capture throttle, not Android display
  frame timing, low latency, or a universal performance guarantee.
- Ordered accepted/sent/acknowledged: 762/762/762, including 26 gamepad snapshots.
  Normal quit returned 0, COMPLETE, `managed_session_closed=true`; no native child
  remained. Keyboard/pointer held counts and pending input were zero.

The first test cart mistakenly used `_update60()` (a PICO-8 convention) and was
not valid game-input evidence. It was corrected to Picotron's documented
`_update()` before the passing run. Earlier reports and owned test carts remain
private for traceability. The checked-in [manual test source](../experiments/execution-model/tests/gamepad_acceptance.lua)
uses the corrected callback. The device ran an equivalent one-line version.

A subsequent fresh-launch check exposed a lost first press during virtual-device
creation. Stable logical slots initialized before Picotron enumerates controllers
replace that lazy creation. Guide can also close the host menu, trigger endpoints
retain the full signed SDL range, and unassigned game buttons cannot fall through
into pointer input.

Final APK session `954c9241-ff56-48d4-8e88-8203d5184a29`, native PID 20028,
verified all four logical slots initialized successfully and the first A press
reached `btn(4,0,1)` after startup and again after Guide open/close. D-pad right
reached `btn(1,0,1)`; each release returned to empty state. Normal quit returned
0, COMPLETE, `managed_session_closed=true`, ordered accepted/sent/ack 127/127/127,
eight controller snapshots and no native child. It captured 10,214 of 10,215 SDL
presentations over 165,122 ms. This closes the observed first-press regression.

Evidence lives in ignored `.local/evidence/retroid-managed-20261102/`:
`gamepad-final.json` (intermediate workflow), `gamepad-stable-slots.json` (final),
`gamepad-final-screen.png`, `gamepad-consumed.png`, `native-pause.png`. No official
runtime, archive or device screenshot is committed.

## Validation and reproduction

Ten focused Java input/transport checks pass, including controller packet bounds,
source slots, immutable snapshots and ordered releases. Headless/desktop host
checks and both APK builds pass. A fresh ARM64 build of the owned native fixture
passed `--self-test` on Retroid's ADB shell: pointer, keyboard, ordered records,
controller source isolation, invalid-state rejection, held quit refusal and EOF
release. This shell test is not Android app execution-policy evidence. The full
emulator suite was not rerun.

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p test_frame_protocol.py
py -3 -B -m unittest discover -s experiments/execution-model -p test_headless.py
py -3 -B -m unittest discover -s experiments/execution-model -p test_desktop.py
py -3 -B experiments/execution-model/build.py --headless
# Retain this APK and sibling build.json as a private headless base, then:
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/gamepad-base/picotronnest-execution-lab.apk
py -3 -B experiments/execution-model/build_bridge_tests.py
```

| Private artifact | SHA-256 |
| --- | --- |
| Headless base | `7549959961a8e41d6d038b23249deca74193b498419dacf46014bbe8c0900fe0` |
| Intermediate workflow APK | `e4438e7ccea6b16c5b8dbd358f99deb5c37e7fe7e90075dc14bf06701f7cef16` |
| Final managed APK, installed/tested | `eb863fa72364b93d9837816008fcec4adc503da1753bfb79c2194d1ab94e1219` |
| Final bridge-test APK, build-checked | `f3179f855f4914b359b02bf6e5630472d70c876d84a0421a171c3edd992d83fe` |

All recorded source hashes match the corresponding artifact. APK payload
allowlists pass. The headless base must be rebuilt after changing its inputs;
the older base without joystick support is deliberately rejected.

## Remaining work

Phone game touch controls, finished product packaging and matching tester
instructions remain. Physical controllers/analog ranges, simultaneous players,
Bluetooth reconnect, alternative official video modes, audible output and
enforcing ARM64 Android compatibility still need scoped acceptance. Hardware
uncertainty does not block independent functionality work.
