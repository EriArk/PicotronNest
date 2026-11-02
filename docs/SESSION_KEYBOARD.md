# Shared session keyboard and IME

## Implemented boundary

The official-runtime lab can now explicitly enable the existing native keyboard
endpoint with the boolean Activity extra `keyboard_controls`. It defaults off.
The option also exposes the shared input panel and its **Show keyboard** button;
it does not require a cursor profile. This remains a developer lab, not product
setup or official Picotron input acceptance.

`KeyboardChannel` owns the abstract server socket, connected client, bounded
writer and `KeyboardInput` adapter. The fixture and official launcher use the
same implementation. Before exposing input it checks the client's app UID and
requires its PID to match the live, authenticated frame peer. Accept/frame-peer
readiness has a five-second bound. Closing before connection is idempotent and
does not require a guest to unblock `accept`.

The owner revokes the adapter when the frame session ends, writing fails or the
channel is closed. It closes the socket to unblock writes and confirms both
workers have ended before returning its receipt. That receipt contains the peer
PID, accepted/sent packet counts and any failure. It contains no typed text.
Removing an older owner cannot detach a newer owner's adapter. Disconnecting
does not fabricate successful key-up delivery after the peer has gone away.

The lab passes `PICONEST_KEYBOARD_SOCKET` only for this explicit opt-in and stores
the final `desktop_keyboard` receipt beside frame/process evidence. The existing
report checker compares it to native connection, delivery and released-state
counters. The private runtime files, native home semantics, dependencies and
20-second desktop probe duration are unchanged. No proprietary payload is added.

## Reachability and lifecycle

The shared button is available only for a live view and keyboard adapter. It
releases held input, focuses the desktop, renews the IME connection and requests
IME visibility through the window insets controller on API 30+. A posted request
checks its generation, owning adapter, attached view and focus before showing.
Pause/focus release invalidates queued requests. Older Android versions retain
the legacy InputMethodManager request and are not covered by this emulator run.

Activity recreation retains the session channel and native process. Focus loss
and pause release held keys and invalidate old composition. Old views, buttons
and InputConnections cannot reactivate input. Ended sessions disable the button.
The IME-aware frame viewport remains owned by `DesktopView`. It uses the window's
IME boundary and the view's actual window coordinates, including any height
already removed by a parent. Consumed child insets after recreation cannot hide
the keyboard overlap. Both the shared button and the experimental host menu use
the same visibility request.

## Evidence (2026-11-01)

Final fixture APK SHA-256:
`ecc0eefd25b9a32eaeb8b4e28f92489b051f8ee64bf5a180191be78f5633dc6b`.
Native fixture is unchanged from the preceding keyboard increment:
`ae7eb38c7f8e5cf665ccba5a49b7047fcebb4b9c2cc1c895df3416733dd67a5f`.
The ARM64 desktop APK builds as
`72e0ad5a1c3fc0f1d8170a875d8a162d6e36efffeb32b441eef1ced0aee5523c`.
36 lab host tests (including new IME geometry cases) and nine archive tests pass.

Focused collection `425c6a43-1f8c-480f-bb57-c5b4f6ff9e6d` passes eleven groups on
`emulator-5554`, AVD `PicotronNest_Mouse_API35`, Android 15/API 35 x86_64,
SELinux Enforcing; target API 36. Host/native PIDs are 5103/5143. A single native
process survives recreation; the three Activity creations include the initial
invalid-configuration rejection. The common receipt confirms seven accepted and
sent packets, with the same frame/keyboard PID, seven native SDL events, two
balanced key pairs, three text events and 37 UTF-8 bytes. Final held/modifier
state is zero, exit is 0 and the UID census contains no surviving child.

All fifteen scenarios pass in one final-APK collection
`09e033a4-1aa1-4a55-b659-a07ad065f881`, including the existing combined D-pad/IME
and host-menu scenarios and the new shared keyboard session. Every final UID
census contains no surviving child. Intentional abrupt/partial exits remain
23/24; all other native exits are 0. Both build manifests match the current
sources. The isolated SSD AVD setup from the preceding increment was reused;
no Retroid operation or host virtualization change was made.
After collection the named AVD was stopped through its console and the owned
QEMU process exit was confirmed. Both retained emulator data trees remain intact.

Channel fault tests reject a same-UID peer with the wrong expected PID before
creating an adapter, close twice before connection, and observe a real failed
socket write with adapter revocation and a failure receipt. No foreign-UID app
or physical input device was used. Key and text delivery uses synthetic Android
dispatch and InputConnection calls, not automated typing on Gboard keys.

### Visual regression found during validation

The initial collection `370b39e1-ec6a-4821-a730-55493dcfc043` and full intermediate
collection `84d220e0-324c-4181-9911-e5e52afbb3e4` passed their original assertions,
but the first screenshot did not retain a visible IME after recreation. The
private ImeTracker log showed the old hide animation cancelling the new show.
The visibility request now uses the insets controller after input rebinding.

Stronger checks then failed in `2627a24c-dfe0-4706-b3dd-bc7eff1e9d21` and
`3667c084-9480-45c2-9965-67dde9f0f9b8`: Gboard was visible, but the recreated view
retained its full 1532-pixel viewport despite an 822-pixel root IME inset. The
window-coordinate overlap fix yields a 776-pixel viewport for a view starting
at y=622 in a 2220-pixel window. Host checks cover a full window, nested view,
already resized parent, hidden keyboard, fully occluded view and large offsets.
Instrumentation requires visible IME and the entire frame above its boundary
continuously for 750 ms within the existing seven-second deadline, including
after the restored connection commits text. The failed collections are retained.

The final unedited `keysession.png` was inspected at original resolution and
shown in chat. It includes the shared buttons, Unicode receipt, complete owned
red/blue frame and docked Gboard. Original-PNG pixel comparisons confirmed that
the static button region matches the initial image and the IME region matches
the prior visible-keyboard image; omitted repeated pixels in chat previews were
not treated as new app defects. Evidence remains under ignored
`.local/bridge-tests/evidence/<UUID>/`; no APK or proprietary material was published.

## Reproduction

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -m unittest discover -s tools -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode keysession
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
```

For an owner-approved, already provisioned ARM64 lab check, the optional Activity
extra is `--ez keyboard_controls true`. It does not provision a runtime or reset
the lab's retained-session quota. No Retroid launch was performed in this increment.

## Remaining work

Historical boundary at this increment: keyboard and pointer packets used separate sockets. Queue/write completion
does not establish their relative SDL consumption order. Ctrl+click, Shift+drag
and rapid cross-device chords remained explicitly unaccepted. The next input step
was an ordered protocol or acknowledged coordination boundary shared by native
and Android adapters, with native event-order checks under fragmentation and
backpressure. Arbitrary delays are not an ordering contract.

The subsequent [input bundle](INPUT_BUNDLE.md) implements that boundary and
checks native event ordering, touch editor keys and shared session exit.

Official SDL/Picotron event consumption, physical keyboards/controllers, AltGr,
layout and lock keys, complete IME editing, audio, persistent projects, SAF import
and product setup remain open. The [functional beta gate](BETA_READINESS.md) is
not met. Owned synthetic key/IME dispatch and SDL structures must not be described
as physical typing or successful text entry in the real Picotron editor.
