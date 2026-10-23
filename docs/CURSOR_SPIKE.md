# Relative touchpad and configurable stick cursor experiment

This bounded preparation implements the owner's accepted phone/handheld input
direction while Retroid remains reserved. Only the owned emulator fixture
attaches it. The official launcher retains its existing direct-touch path;
official Picotron clicks, drag, controller behavior and usability remain unproven.

## Contract

`RelativePointer` stores fractional framebuffer coordinates and publishes integer
positions through the unchanged primary-button pointer protocol. It initializes
from actual frame dimensions, clamps at all edges and discards excess edge travel
so reversing direction responds immediately. Small deltas accumulate without
sending duplicate integer coordinates. Resize releases capture before clamping
into the new bounds; there is no 480x270 or device-name dependency.

Touchpad contact anchors the finger without jumping the cursor or clicking.
Movement uses the same aspect-fit scale as video. Lifting and touching elsewhere
starts a new anchor. A separate primary-button control permits explicit dragging;
there are no implicit tap, long-press or secondary-click gestures. Cancellation,
additional touch contacts, viewport changes and focus loss discard the anchor
and release held input.

Stick mode has caller-selected axes, primary/precision buttons, speed in frame
pixels per second, radial deadzone and precision multiplier. It uses Android
animation callbacks and elapsed time, capped at 50 ms per tick to prevent a
large jump after a stalled UI frame. Neutral input stops scheduling. Diagonals
are normalized; only the current driving source's precision action affects speed.
The latest axis sample defines current velocity; historical samples are not
replayed as a movement trajectory. Defaults require physical-controller tuning.

A held primary button gives one source ownership of the drag. Other sources
cannot move, press or release it. Removing an unrelated controller preserves
the owner; removing the owner releases the button and stops its motion. Changing
mode or bindings, losing focus, resizing or detaching releases and clears pending
motion. Stream disconnect closes the adapter, unregisters its device listener,
rejects stale overlay controls and forgets capture without claiming a delivered
release. Disabled mode does not consume mapped gamepad events. This does not yet
implement passing gamepad events into Picotron cartridges.

`CursorInput` obtains centered axis ranges/flat regions from Android's reported
device capabilities, not a handheld model name. Missing or uncentered ranges are
rejected. Only an explicitly enabled fixture flag permits normalized synthetic
events without an actual registered input device. The fixture exercises X/Y
with A/L1, then Z/RZ with X/R1; these are test bindings, not shipping defaults.
No profile persistence or remapping settings screen is implemented.

The Android event and capability contract follows the primary documentation for
[controller actions](https://developer.android.com/games/sdk/game-controller/controller-input)
and [View animation callbacks](https://developer.android.com/reference/android/view/View).
Implementation is original; no upstream source code or new dependency is copied.

## Reproduce

Use the [owned bridge harness procedure](P1_BRIDGE_TESTS.md), then:

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode cursor
```

The default runner includes this scenario alongside normal, abrupt-exit,
partial-frame and keyboard checks. It verifies source/build/installed APK hashes,
fresh session reports and final same-UID process census. Only the existing owned
test package is updated; no Retroid, imported runtime or persistent home is touched.

The native fixture receives actual pointer packets. A diagnostic label displays
its reported coordinates/buttons; the white cross is a host cursor overlay on
owned red/blue frames. `cursor.png` is an actual emulator screenshot retained in
the ignored evidence directory and shown in the owner conversation. Touch and
gamepad events are injected into the real Android View; this is not physical
finger/stick testing. Device-removal notifications are simulated.

## Observed result — 2026-10-23

Android 15/API 35 `sdk_gphone64_x86_64` emulator, SELinux Enforcing, target 36.
Evidence UUID: `b36014f4-71e7-41a3-8958-a96a279c7d3d`.
APK SHA-256: `251537b9923785eff7b0cb3532bb621f3adde235ed17131b8ebad4b5547abebe`.
Native fixture SHA-256: `444a8fe81c259ccd72fd1c48ebd975014457c71f2de32b6a03bc1a8682d9f817`.
The native fixture and pointer decoder are unchanged from the keyboard increment.

All 12 cursor check groups pass. Host PID 12687, native PID/authenticated peer
12711, UID 10228; one launch, one Activity creation, exit 0 and no surviving
same-UID child. The native receiver consumed 28 pointer packets with seven
balanced down/up pairs, final position (12,10), held=0. Android received 201
complete frames and drew sequence 199; final-frame drawing is not claimed.

| Check | Observed result |
| --- | --- |
| Relative touch/recontact | Delta reaches native coordinates; a distant new contact causes no jump or click |
| Explicit drag/mode change | Separate touch control holds primary while moving; mode switch releases it |
| Stick/ownership | Real animation callbacks move the cursor; deadzone is quiet; a second simulated device cannot hijack a drag |
| Removal/remapping | Unrelated removal preserves capture; owner removal and changing bindings release it |
| Precision/focus | Remapped Z/RZ and X/R1 work; dialog focus loss during nonzero stick motion releases and stops; focus return does not resume old motion |
| Bounds/viewport | Edge clamping permits immediate reverse; resizing the Android View releases a held button |
| Disabled/exit | Mapped gamepad events are not consumed; disconnected overlay controls reject new presses; native exit and sent/received packet counts agree |

This APK also passes normal input/lifecycle (15 groups, PID 12446, exit 0),
abrupt native exit (five groups, PID 12534, expected exit 23), partial-frame exit
(five groups, PID 12591, expected exit 24 and partial EOF), and keyboard/IME
(nine groups, PID 12646, exit 0). All final UID censuses contain no surviving
child. The initial focused cursor run also passed; its private UUID is
`0c1a965d-c20a-442e-9cc6-1fae732cf1de` and predates the additional focus/resize checks.

31 lab tests and nine archive tests pass. The new Java suite covers fractional
motion, bounds, finite-value validation, source ownership, deadzone, elapsed-time
cap, precision, mode cancellation and resize. The official desktop lab builds
with the optional hooks but does not attach the cursor adapter, APK SHA-256
`d846005034da14606924b99cb50bbbd3ab19aa4ab6a18efd4ac836b1c0a3ae98`.
It was not installed on Retroid. No APK or screenshot is published in the repository.

Code review after the first full run identified that the overlay could still
address a disconnected adapter. Disconnect now cancels callbacks, unregisters
the device listener and rejects subsequent input; the final native-exit test
explicitly attempts a stale overlay press. The previous passing full-run record
`0ac6679c-4177-4f5e-9b7a-961129b45b01` predates this additional check.

The final test-only change explicitly waits for frames drawn after viewport
restoration before taking the screenshot. Its focused cursor rerun passes all
12 groups in session `5f99fbc9-f84d-4262-bcc4-7da1a5da7f39`, APK SHA-256
`07b684874a9b37f0d7959b3e029d7c0d4f70fd83c6abd0e023721e393ec3c302`:
host PID 12812, native PID 12841, exit 0, 24 pointer packets, seven down/up pairs,
177 received frames and sequence 177 drawn. The shared adapter sources are
unchanged from the five-scenario run above. The 1080x2340 PNG was inspected at
original resolution and shown in chat. An incomplete viewer preview initially
looked like missing controls, but pixel inspection of the saved PNGs and the
original-resolution view confirmed that the controls and cursor were present.

## Remaining limits

The later [D-pad and simultaneous IME experiment](DPAD_AND_IME.md) adds a bounded
fallback and shared-viewport check; it does not upgrade physical-device acceptance.

No secondary button, wheel, acceleration curve, profile storage,
automatic profile selection, Android hardware mouse adapter or controller-only
keyboard/menu/exit workflow. The fixture's diagnostic controls are not product UI.
The adapter is scoped to its attached View and closes on detach; restoration of
user-selected modes/profiles across Activity recreation is still product work.
Neither the screenshot nor the native counters demonstrate official SDL/Picotron
consumption, physical-controller latency or comfortable phone/handheld operation.
