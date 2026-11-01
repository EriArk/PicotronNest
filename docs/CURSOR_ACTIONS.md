# Secondary-button and wheel actions

## Scope and behavior

The owned cursor fixture now accepts an optional secondary button and four wheel
key bindings. Touchpad mode exposes explicit primary/secondary hold controls and
four scroll buttons in the test Activity. These are diagnostic controls, not a
finished phone or handheld interface. No product default mapping is selected.

`RelativePointer` preserves independent primary/secondary bits under one drag
owner. Releasing one button retains the other; foreign devices cannot move,
release or scroll an owned drag. Cancel, removal and focus/recreation cleanup
release the complete mask without replaying wheel impulses. The five-field sink
uses the existing PNI2 transport; the wire format and native decoder are unchanged.

`CursorInput` sends one wheel step (1024 fixed-point units) per fresh mapped key
press, suppressing Android repeats and duplicate downs until that device releases
the key. Wheel holds are source-specific and bounded to 16 devices. Repeated
keydown events cannot recreate a button hold immediately after cancellation.
Touch scroll buttons use click dispatch; explicit button holds support secondary
drag and a two-button chord. Continuous scroll and gesture defaults are deferred.

Saved profiles retain the 72-byte v1 format when no optional actions are assigned.
V2 adds five integers for secondary, left, right, up and down, totaling 92 bytes.
Zero means unassigned. Conflicting, invalid, truncated, oversized and unknown
records fail explicitly. Reading v1 does not rewrite it. Explicit v2 saving uses
the existing staged AtomicFile write/readback contract; interrupted staging keeps
the committed record. This is settings persistence, not Picotron home persistence.

## Reproduction and evidence — 2026-11-01

Use an explicitly selected disposable x86_64 emulator, never the reserved Retroid:

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -m unittest discover -s tools -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode actions
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
```

Environment: Android 15/API 35, `sdk_gphone64_x86_64`, SELinux Enforcing,
target/compile SDK 36; owned AVD `PicotronNest_Mouse_API35`, emulator 36.6.11.
The isolated SSD setup documented in [Android mouse evidence](ANDROID_MOUSE.md)
was reused. No Retroid command, runtime import or proprietary asset was needed.

Final fixture APK SHA-256:
`83a05696b9fe8c0d3d623f13354bda26c6892c8bb7543e26e4fa7f8d20a25d48`.
The collector verifies installed APK and recorded source hashes, current-session
UUIDs, native peer identity and an empty child-process census after each scenario.

The focused pair passes in `e8aac91c-5f24-403c-85f6-3b1a280a4ece`.
All thirteen scenarios pass in one final collection,
`e0181a7f-2f67-4ece-a596-becd871f0929`. Normal/keyboard/cursor/combined/profile/
profile-read/menu/pointer/mouse/actions/actions-read exit 0; abrupt/partial exit
with the expected 23/24. Each final UID census has no surviving child.

| New scenario | Native receipts and lifecycle |
| --- | --- |
| Actions | 11 check groups; native PID 4688; exactly 28 pointer packets, ten balanced down/up pairs, final mask 0; four wheel directions from controller and touch; Activity recreation retains one child |
| Actions read | Six groups; native PID 4753; three packets, one balanced pair and one upward wheel step; a distinct host process reads the persisted v2 record |

The action scenario checks source ownership, duplicate/repeat suppression,
device removal, a real focus-stealing dialog, touch Button dispatch, secondary
drag/chording, recreation release and restored bindings. Device keys, touch
events and removal callbacks are injected. The fresh-process check uses a new
owned native session; it does not restore a killed workstation.

35 lab host tests and nine archive tests pass, including expanded profile codec
and relative-pointer assertions. The ARM64 desktop lab also builds, SHA-256
`d19c29ace4f2cdce9c2d16f09df7c7d27ca193764a898b0fde0c73d6a414c7c4`;
it was not installed. No APK was published.

Private JSON reports, process censuses and actual `actions.png`/`actions-read.png`
screenshots are under `.local/bridge-tests/evidence/<session>/`. The focused
`actions.png` was inspected at original resolution and shown in chat: it displays
the owned red/blue frame, controls and native receipts, not Picotron. Frame counts
depend on scheduling and are not performance measurements.

## Remaining integration

The official launcher still does not select the experimental cursor/keyboard/
mouse adapters. Next, add explicit session-level input selection to the official
runtime lab and exercise its shared setup/release path with the owned fixture.
Actual SDL consumption, physical controller/mouse behavior and phone usability
remain hardware gates. Product profile editing, device selection/reconnection,
game-control routing, audio, import and persistent projects remain open under
[beta readiness](BETA_READINESS.md).
