# Host commands: owned Android experiment

The optional `HostMenu` supplies Resume, Show keyboard and Exit session, with
an explicit Keep working / End session confirmation. It is attached only by
the owned test Activity. This is not a product launcher or an implementation of
Picotron's own desktop/menu. The official-runtime launcher remains unchanged.

## Routing and lifecycle contract

The host injects the controller menu, accept and cancel key codes; conflicting
bindings are rejected. The fixture uses Start/A/B as examples, not product
defaults or a Retroid-specific mapping. D-pad up/down selects a menu item.
Controller actions execute on a matching device/key release, once per press;
repeat, canceled and foreign-device releases do not trigger a command. This
prototype uses key events; HAT-only menu navigation is not implemented.

A real Android button and list rows provide the touch path. Opening the menu
releases pointer capture and keyboard modifiers, invalidates the old IME
connection and requests keyboard hiding. Closing a dialog returns focus
asynchronously. The keyboard command waits for that focus, restarts the input
connection, and requests IME display. Tests check actual IME insets and native
UTF-8 receipt rather than treating `showSoftInput`'s return value as visibility
proof; see [Android's contract](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager).
Pending keyboard callbacks are canceled on pause, destruction or another menu
request. Closing an Activity also removes menu windows and pending callbacks;
an old menu cannot reopen after recreation.

`SessionExit` belongs to the session, not its Activity. Only the first request
starts its worker. It remains EXITING until the backend verifies shutdown,
then becomes ENDED; an exception becomes FAILED with a diagnostic reason.
Failure is not relabeled success and does not finish the Activity. A new menu
can observe the same exit state without issuing another request. Backend
implementations must bound their work and verify their own process contract.

The **owned fixture backend only** snapshots accepted pointer/keyboard counts
after release, waits up to five seconds for matching native receipts and balanced
held state, then sends its existing `Q` command. It waits another five seconds
for exit code 0, a reaped child and a complete error-free transport report.
Only then does the UI finish the Activity. This is not a Picotron shutdown
protocol: official SDL quit/cancellation, unsaved work and timeout/recovery
semantics still require integration and hardware acceptance. No forced kill is
presented as a normal successful exit.

## Reproduce

Use the [owned harness build/install procedure](P1_BRIDGE_TESTS.md), then:

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode menu
```

The default runner now has nine scenarios. The focused menu test uses real
Android button/list dispatch and synthetic controller events delivered to the
Activity or its actual dialog. It exercises shared release, source ownership,
repeat/canceled shortcut suppression, controller and touch keyboard actions,
native Unicode receipt, stale-IME rejection, touch cancellation of exit,
Activity recreation while a menu is open, and controller confirmation of a
single verified exit. It does not prove hardware input routing or physical
typing through Gboard keys. An actual `menu.png`, native transcript, fresh UUID,
source/APK hashes and UID census are retained privately.

The host suite additionally checks the session state remains EXITING while
its backend is blocked, that repeated requests cannot invoke it twice, and
that a failed backend never reports ENDED. Rotation during the exit itself and
the failed-exit dialog are not device-tested in this increment.

## Validation — 2026-10-24

34 lab tests and nine archive tests pass. The owned x86_64 fixture uses Android
15/API 35, SELinux Enforcing, target 36. Retroid was not installed, launched or
interacted with. No proprietary content or public APK is part of this increment.

The first emulator attempt (`7365b38b-ceca-4886-a199-777fd86d3f03`) exposed a stale
IME connection: the keyboard opened but Unicode commit was rejected. The menu
now restarts input after regaining focus. The original UI-thread assertion
crashed the test host before its report/cleanup path; the subsequent owned UID
census was empty. The harness now catches UI action failures on the UI thread
and rethrows them on the instrumentation thread, allowing its normal failure
report and child cleanup. A deliberate failing UI action verifies this path.
The collector also reports missing/invalid JSON using current instrumentation
output instead of only a JSON parser exception.

The corrected focused run `4da4492d-4ddd-445c-bff6-400abcd5f6e7` passes ten groups
with native PID 3658 and exit 0. Its APK is
`950d0aa42c9c6caa58794f2c7e58d6e919c93260793a80f87ccfa1fd705434e0`.
The final harness build corrects the diagnostic cursor label and has SHA-256
`1d3ecc316e15611e8c244ac66f8d4a89575a24df6bf1126e4499c63bac86c662`.
The native fixture is unchanged:
`444a8fe81c259ccd72fd1c48ebd975014457c71f2de32b6a03bc1a8682d9f817`.

The official desktop lab also builds, SHA-256
`76ee2bf651b0156be968f6a37054e40e64248f9363ea18b3a438a6716cf4c851`.
It does not attach the new menu and was not installed on Retroid.

Final-build checks:

| Scenario / evidence UUID | Observed result |
| --- | --- |
| Menu — `5cda3bd1-7ee5-4a5e-859f-8fead00b3a60` | Ten groups; host PID 3839/native PID 3864; two Activity creations, one native launch; exit 0 |
| Normal input/lifecycle — `cdd0e75a-e173-4b65-88dc-65926225d66e` | 15 groups; native PID 3978; 61 pointer packets; 77 frames received/drawn; exit 0 |
| D-pad/IME — `0e3647b3-efa5-4ff7-8a23-9bc9e478d30b` | 12 groups; native PID 4090; 42 pointer and seven keyboard packets; 116 frames received/drawn; exit 0 |

The menu receiver consumed two pointer packets (one balanced pair), three
keyboard packets (one balanced modifier pair plus eight UTF-8 bytes) and exactly
`menu ✓`. Both authenticated socket peers match PID 3864. There was one exit
request and one Activity finish, after verified process completion. Android
received 273 complete frames and drew sequence 271 before the session ended.
All three final UID censuses are empty, and both build manifests match current
source hashes. The actual 1080x2340 menu screenshot was inspected at original
resolution and shown in chat. Counts are not performance measurements.

These are the three focused Android scenarios rerun for this increment; the
other six retain their earlier evidence rather than being claimed as rerun on
this APK.

## Remaining integration

The [beta readiness gates](BETA_READINESS.md) distinguish a functional candidate
from a hardware-validated beta. Menu settings/remapping UI, HAT/keyboard-only
navigation, recovery after failed shutdown, official-runtime event consumption,
secondary click/wheel, audio, persistent home and end-user runtime import remain
open. The next input increment is secondary click and wheel with bounded native
receipts; it must retain the existing release and coordinate contracts.
