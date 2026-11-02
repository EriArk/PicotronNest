# Touch game controls

2026-11-02. Integrated into the managed development APK; no public release.

## User behavior

Open the floating controls icon to configure **Touch gamepad**:

- **Auto** (default): visible in controller-games mode when Android reports no
  physical gamepad/joystick. **Show** and **Hide** override device discovery.
- Compact layout: sliding eight-way D-pad, A/B/X/Y, Select and native game pause.
- **Full controller** adds two analog sticks, L/R, L2/R2 and L3/R3.
- Button size (60–140%) and opacity (15–80%) persist across launches. Layout fits
  available width/height and respects the host's display-cutout inset.

Controls overlay the complete Picotron image; they reserve no toolbar or video
area. Empty space still routes to the workstation pointer. The keyboard and
controls drawer hide the touch gamepad; controller-pointer mode also hides it.
Menus, focus loss, detachment, layout changes and input reset clear held state.
Each control captures one finger; simultaneous fingers on different controls
support directional/button/stick/trigger chords. Moving outside a button releases
it; moving back inside during that gesture presses it again. Sticks clamp to a
unit circle and return to neutral when released.

Touch controls supplement **player one**, without occupying one of the four
physical-source slots. Buttons merge by union; axes use the greatest magnitude
(positive wins an equal-magnitude tie). Releasing touch does not release a held
physical button, and removing a physical controller does not release touch.
The existing authenticated, ordered SDL snapshot channel remains authoritative.
Labels describe SDL controls; Picotron determines their final `btn()` indices.
These controls do not emulate keyboard events or replace any Picotron UI.

All code and vector drawing are original project code; no new dependencies or
third-party assets were added.

## Focused validation

- Eleven Java host checks pass, including independent touch owners, chords,
  stick/trigger release, cancellation, immutable inputs and merge bounds.
- Both production and ARM64 fixture APK builds pass their payload allowlists and
  source-hash checks. The fixture builder now accepts `--abi arm64-v8a`; its
  default remains x86_64. No official files are included in either APK.
- A dedicated Java/View instrumentation test passes on Retroid Pocket Classic,
  Android 14/API 34, target 36. It dispatches real multi-pointer MotionEvents
  through the overlay and inspects ordered packets: A + D-pad, independent lift,
  touch/physical A ownership, physical-source removal while touch is held,
  analog-stick plus trigger endpoints, cancel, actual dialog focus loss,
  menu/mode hiding and saved settings. Its acknowledgements are owned test data,
  not native SDL evidence. The test launches no native child or Picotron runtime.

During test development, a stick press exactly outside the View's exclusive
right boundary was corrected to an interior point. The focus check now waits
for the dialog to actually receive focus before checking release; showing the
dialog alone is not that event.

## Official Picotron check

Retroid Pocket Classic, Android 14/API 34, ARM64, target 36, existing user-imported
Picotron 0.3.0d2. APK updated with `install -r`; runtime and projects retained.
Session `b754fba6-18ac-4027-b83a-6f0640ef1b6f`, native PID 20924:

- Auto hid the overlay for the built-in controller. Show enabled the compact
  layout; the full layout was also inspected on the actual screen. Switching
  to the Android IME hid controls and dismissing it restored them.
- The existing owned `/desktop/pn_gamepad_20261102_v2.p64` check cart received
  A, D-pad right, L and L2 via ADB touchscreen gestures on the actual controls.
  Official `btn(button,0,1)` reported `4`, `1`, `14`, `6`, each followed by empty
  release state. That flag excludes keyboard input. See the checked-in
  [owned test source](../experiments/execution-model/tests/gamepad_acceptance.lua).
- Touch pause opened Picotron's native pause menu. Resume returned to an empty
  held state. Menu opening and settings changes left no stuck control.
- Normal host quit: exit 0, COMPLETE, `managed_session_closed=true`, ordered
  accepted/sent/acknowledged **109/109/109**, including 12 controller snapshots.
  No native Picotron/PRoot child remained. The retained frame was 480x270.
- The earlier `pn_check_20261102.p64` project still hashes to
  `1c2a44829c46c5f1e1bfa7e8504e812459e46c1d7719d38e6603c6afaa6d3785`.
  Device touch settings were returned to Auto, compact, 100% size, 40% opacity.

An initial explicit Activity launch without the launcher action entered the
existing bounded diagnostic path and ended before the managed check above.
It is not counted as managed-session evidence.

Private evidence under `.local/evidence/retroid-managed-20261102/`:
`touch-final.json`, `touch-compact-desktop.png`, `touch-compact-consumed.png`,
`touch-full-consumed.png`, `touch-native-pause.png`, `touch-final.png`.
Screenshots show actual device pixels; none are committed.

| Artifact | SHA-256 |
| --- | --- |
| Installed/tested managed APK | `ba1c62b9dd7bde70b7beb0cf3aa27e5115efa91c56c38ff7e519125cc95326f5` |
| ARM64 fixture APK, focused instrumentation passed | `2e82108a853e55f1260f3eee0d3542be3984e4ca72d1bff5f8741218a6c9f32c` |

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p test_frame_protocol.py
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/gamepad-base/picotronnest-execution-lab.apk
py -3 -B experiments/execution-model/build_bridge_tests.py --abi arm64-v8a
# Set the intended test-device serial explicitly:
adb -s <serial> install -r .local/bridge-tests/artifacts/bridge-tests.apk
adb -s <serial> shell am instrument -w art.picotronnest.bridgetest/art.picotronnest.executionlab.TouchGamepadInstrumentation
```

## Remaining work

Final product packaging and matching tester instructions are next. Phone/tablet
ergonomics, physical multitouch acceptance, Bluetooth transitions, broader
orientation/IME combinations and official analog gameplay remain for testers.
The focused Retroid evidence is not a general compatibility claim or an enforcing
ARM64 execution-policy check. Audible sound acceptance remains open separately.
