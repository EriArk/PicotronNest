# Integrated laboratory input bundle

2026-11-02. This increment assembles keyboard, IME, pointer controls, editor keys
and session commands around one ordered input transport. It is shared by the
official-runtime lab and the owned emulator fixture. It is not official Picotron
input acceptance or an end-user beta. Retroid remains reserved for the owner.

## Shared session behavior

`--ez session_controls true` opts into the complete laboratory panel: pointer
selection, Show keyboard, Editor keys and Session menu. A validated
`cursor_profile` enables the existing controller/touchpad choices described in
[SESSION_INPUT](SESSION_INPUT.md). `keyboard_controls` also uses the new ordered
transport, without requiring the session menu. Pointer-only sessions retain PNI2.
All options remain off by default and do not provision the owner's runtime.

Editor keys provide Esc, Tab, Ctrl, Shift, Alt, arrows, Backspace and Enter.
Modifiers toggle a touch-owned hold; releasing it cannot release the same key
held by a hardware keyboard. Other keys support touch hold/repeat, cancellation
and accessibility clicks. Focus loss, pause and recreation release inputs and
revoke old IME connections. IME deletion accepts at most 32 preceding/following
key operations per call; it does not synchronize Picotron's editor selection or
surrounding text with Android.

The host menu supports resume, keyboard and confirmed exit. Controller keys are
bounded Activity extras `menu_key`, `menu_accept_key`, `menu_cancel_key`, defaulting
to Android Start/A/B codes 108/96/97. Duplicate bindings, navigation conflicts
and a menu opener overlapping the supplied cursor profile are rejected before
launch. These lab parameters are not a product remapping screen or universal
handheld defaults.

Exit queues releases before SDL_QUIT, fences later input, and waits for the
observed process result and matching native/host delivery receipts. Completion
state survives Activity recreation. Failed or timed-out completion cannot claim
the process ended. The existing lab watchdog remains; this is not autosave.

DesktopView handles root IME geometry itself. System panning is disabled so the
host panel stays visible. The compact panel and complete frame are checked above
the docked keyboard after recreation. Other sizes/orientations remain acceptance
work, not inferred from one portrait emulator.

## PNO1 ordering contract

The authenticated input socket still requires the app UID and the live frame
peer PID. In ordered mode the frame connection carries video only. Key, text,
pointer, reset and quit records share one bounded queue, monotonically contiguous
sequence and native reader. There is no timing-delay coordination between sockets.

Each record is 64 little-endian bytes:

| Offset | Meaning |
| --- | --- |
| 0 | Magic `0x504e4f31` |
| 4 | Sequence, starting at 1 and bounded by INT32_MAX |
| 8 | Type: key 1, text 2, reset 3, pointer 4, quit 5 |
| 12 onward, types 1-3 | Existing validated PNK1 payload |
| 12/16/20/24/28, type 4 | x/y/buttons/wheel-x/wheel-y, remaining bytes zero |
| 12 onward, type 5 | All zero; held keys/buttons must already be released |

After draining the record's SDL events, native code sends a 16-byte reply:
`0x504e4f41`, sequence, cumulative delivered SDL event count, status (zero for
success). Android sends the next record only after validating that reply.
SDL event delivery is not proof that Picotron handled the event or saved data.
Inactive SDL text input produces a nonzero status instead of false success.

Pointer releases precede modifier releases on reset/disconnect. NULL event peeks
do not advance sampled state or acknowledge pending events. Partial records,
invalid sequences, malformed/truncated acknowledgements and acknowledgement
write failures are errors. Queue overflow latches failure, discards queued work
and queues a reset; lost accepted records remain visible in accounting. IO,
drain and shutdown waits are bounded. Paired APK/observer builds are required;
PNO1 is a private lab protocol, not compatible with a legacy PNK1 receiver.

The observer uses `PICONEST_INPUT_SOCKET`; its old `PICONEST_KEYBOARD_SOCKET`
endpoint remains available only for explicit legacy experiments. Both variables
are cleared from inherited launcher environment before explicit configuration.
The report validator requires matching packet/acknowledgement/event totals and
clean native state. Event payloads are not added to official-session diagnostics.

## Validation

Host tests cover mixed FIFO traffic, byte-fragmented acknowledgements, invalid
and truncated replies, exit fencing and queue overflow under backpressure.
Shared native self-tests cover all 63 record split points, no early ACK, pointer
release before modifiers, maximum reset, invalid/held quit and ACK-send failure.

`bundle` dispatches eight Ctrl+click sequences in a single UI turn and checks
the exact native event order. It also checks shared Shift ownership through a
drag, accessible Ctrl+Tab, Unicode, bounded deletion, focus/recreation cleanup,
stale control rejection, editor-key repeat/cancel, visible panel/IME and real
process exit through the shared menu. `bundle-disconnect` closes the authenticated
socket during a Ctrl-drag, requires native release order and the expected failed
process exit (71). This expected failure is not a successful Picotron session.

The first bundle collection `a3007820-56af-4006-b76c-4cbae0f09956` passed event
assertions but its screenshot exposed system IME panning that hid the toolbar.
The fix disables panning and checks panel bounds, rather than accepting the
original assertions. Collection `d05ec93d-a4b7-4e2a-b80b-c9fc1a7482ce` passed
the strengthened scenario; `fd54afe5-c314-42ff-a371-c4d0a417f37c` passed disconnect.
Review also corrected the fixture label attachment after recreation. These
intermediate collections are retained, not represented as final-build evidence.

The final collection `6a80cc2d-49d4-489d-be39-e9c21b092aa0` passes all 17 scenarios
on `PicotronNest_Mouse_API35`, Android 15/API 35 x86_64, SELinux Enforcing,
target SDK 36. All 38 lab host tests and nine archive tests pass. Both APKs build
and their recorded source hashes match the final source tree:

| Artifact | SHA-256 |
| --- | --- |
| Owned bridge APK | `e3a4b774e4ee3dcfefb0b68307f74d3740b84e495127291ba8a414b75ba566bd` |
| Official-runtime lab APK, build only | `75cc0b2da1179a351edd5bceabb6a3a730a40886fbd50e8b7ddef7b04f18508c` |

The final bundle child PID 6051 exits 0: 68 accepted/sent/acknowledged records,
90 delivered SDL events, 44 keyboard/text records, 23 pointer records and one
quit. It ends with zero held inputs, pending events or native transport failures.
The disconnect child PID 6162 exits with the expected 71. The collector's UID
census finds no surviving native children after any scenario. Repeat counts are
timing-dependent; these totals describe this run, not a fixed performance target.

The final unedited `bundle.png` was inspected and shown in chat. It contains the
owned-fixture label, UTF-8 receipt, shared panel/editor keys, complete red/blue
frame and docked Gboard. After recreation the view starts at y=748, with height
1406 and usable viewport 650 in a 2220-pixel window with IME bottom inset 822.
The receipt precedes quit (67/67); final process accounting above includes quit.
The earlier full collection `b82692b0-4851-468b-8163-4f062af279f5` also passed,
before the fixture-label correction. No Retroid launch/install, official Picotron
interaction or public APK/Release publication was performed.

## Reproduction

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -m unittest discover -s tools -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
```

The default collector includes 17 scenarios. Its guard permits only an x86_64
emulator and checks source/APK hashes, fresh report UUIDs and surviving child
processes. Private reports and unedited screenshots remain under ignored
`.local/bridge-tests/evidence/<UUID>/`. No proprietary runtime is in this fixture.

## Remaining work

The native observer still hooks SDL_PollEvent. Actual Picotron input consumption,
other SDL polling paths, physical keyboards/controllers, layout/dead keys/AltGr
and lock keys require explicit validation. Synthetic Android events and Gboard
visibility do not establish physical typing in the official editor.

The [beta gate](BETA_READINESS.md) remains open: product setup/remapping, runtime
SAF import/activation, persistent native home/projects, audio and integrated
recovery are unfinished. Next, prepare persistent home and transactional runtime
import together, with preservation/failure tests before requesting hardware.
