# Saved cursor profile and Activity restoration experiment

This extends the owned [D-pad fixture](DPAD_AND_IME.md). The subsequent
[shared session controls](SESSION_INPUT.md) accept an explicitly supplied profile
in the official-runtime lab; they do not load a profile automatically. Retroid remains reserved; no official
Picotron input or physical controller acceptance is claimed.

## Contract

`CursorProfile` is an immutable settings record: disabled/touchpad/stick/D-pad
mode; stick, direction-key or HAT bindings; speed, deadzone and precision scale.
Bindings are explicit, not inferred from a device model. Device IDs, held keys,
directions, drag ownership, pointer coordinates, IME composition and the fixture's
synthetic-axis capability are never saved. A restored cursor starts at the
framebuffer center with no held input. Product defaults remain undecided.

Schema v1 is exactly 72 bytes, big-endian: magic `0x504e4350`, version 1, mode
(`0=disabled, 1=touchpad, 2=stick, 3=D-pad`), binding kind
(`0=stick, 1=direction keys, 2=HAT`), eight integer controls, then three doubles
(speed/deadzone/precision). Controls are X/Y axes, left/right/up/down key codes,
primary key and precision key. Key profiles use axes -1/-1; axis profiles use
zero for unused direction keys. These numeric codes are part of the v1 contract;
reordering modes requires an explicit schema change.

Schema v2 appends five integers (secondary, wheel left/right/up/down), for exactly
92 bytes. Zero leaves an action unassigned. Saving a profile without these actions
still writes v1; loading either version does not rewrite it. Nonzero action keys
must be distinct from each other and all reserved direction/primary/precision keys.
See the [action extension and device evidence](CURSOR_ACTIONS.md).

The parser reads at most 73 bytes for v1 or 93 for v2. Truncation, trailing data, unknown versions,
unknown/incompatible modes, conflicting keys, invalid axes and non-finite or
out-of-range tuning values fail explicitly. It does not silently install defaults
or rewrite an unsupported file. Pure-Java tests cover every truncated length,
all supported mode/binding combinations and representative invalid fields.

`CursorProfileStore` operates on app-owned files, off the UI thread. It validates
before staging, syncs and commits with Android
[`AtomicFile`](https://developer.android.com/reference/android/util/AtomicFile),
and verifies the committed record by reading it back. A shared lock serializes
all store instances within this host process; this is not cross-process locking.
Write failure rolls back the staged replacement. No storage permission, runtime
archive or user home is involved. Tests use a UUID-specific file in the owned
harness package, leaving earlier evidence untouched.

`DesktopView.selectCursorProfile` is an explicit UI-thread opt-in after the caller
has loaded/saved settings. The immutable selection survives Activity recreation
within the live session. A new View creates a new cursor once frames and a
pointer connection are available; it never starts a process. Closing the old
adapter releases capture and removes animation/device callbacks. Detached Views
reject stale input and IME-connection creation. Disconnect clears the session
selection; starting another session requires explicit selection again.

## Reproduce

Build/install the [owned harness](P1_BRIDGE_TESTS.md), then:

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode profile
```

This runs `profile` followed by `profile-read`, sharing a fresh evidence UUID.
The first scenario writes, replaces and reads the record, checks rollback and
unknown-version rejection, then recreates and rotates the Activity during a drag
and held direction. The second starts a different host process and loads the same
UUID-specific file from disk. The runner checks distinct host PIDs, one native
launch per scenario, installed/source hashes and final same-UID process censuses.
Neither application data nor the imported runtime is cleared.

The default runner includes these two scenarios after the six existing bridge
scenarios. Reports, native receipts, build/device metadata and actual
`profile.png` / `profile-read.png` screenshots remain in ignored
`.local/bridge-tests/evidence/<session>/`. Screenshots use diagnostic owned
red/blue frames, not a Picotron desktop or a product UI mockup. The diagnostic
header overlays part of the landscape frame; this is not landscape usability
acceptance. Inspect the PNGs at original resolution.

## Observed result — 2026-10-23

Android 15/API 35 `sdk_gphone64_x86_64`, SELinux Enforcing, target 36.
Focused evidence UUID: `203fc480-c94f-49a1-bba5-ddf554bf24cb`.
Test APK SHA-256:
`cf70fcf518b85ff002060dab36a1af712391ebaafd8089a1e267f2771d43e7d5`.
Native fixture SHA-256 (unchanged):
`444a8fe81c259ccd72fd1c48ebd975014457c71f2de32b6a03bc1a8682d9f817`.

The profile scenario passes ten check groups: three Activity creations, one
native launch, host PID 13750/native PID 13774, 66 pointer packets, three balanced
down/up pairs, 277 complete frames received/drawn and exit 0. Stale adapters
reject new presses, and no held direction resumes after recreation/rotation.
The saved primary X mapping works after both changes. Interrupted staging and
explicit rollback preserve the committed record; an unsupported record fails
without overwriting it, and UI-thread disk access is rejected.

The independent read scenario passes five groups: new host PID 13833/native PID
13860, one launch, 23 pointer packets, one balanced pair, 137 frames
received/drawn and exit 0. Both final UID censuses have no surviving child.
The actual landscape screenshot was inspected at original resolution and shown
in chat. Packet/frame counts reflect scheduling, not performance measurements.

33 lab tests and nine archive tests pass. The official desktop lab also builds,
APK SHA-256 `d2453e25fdd64c8faa45e1b17c3a1cf5602387ff280c4d39076ad62fb6cf488f`;
it was not installed on Retroid. No APK or proprietary material was published.

An initial full run (`5d608041-bdda-4dd4-801f-39f3422098ec`) failed the older
normal-scenario resize assertion: after Home/return, release packet 57 retained
old coordinates (46,25), while the assertion expected the resized bound (26,25).
The child was cleaned up and the failure is retained. The log cannot identify
the exact release callback. Native resize is now checked before the separate
Home/return transition, and readiness requires the actual new framebuffer size,
not only an advancing sequence. Exact coordinate and 61-packet assertions remain.

A subsequent attempt (`a06fecf2-de71-4a09-88a1-72bb2a1eb07e`) did not enter
instrumentation: Android reported `failed to attach`. The collector rejected
the leftover report by UUID. It now reports the current instrumentation startup
failure explicitly instead of echoing the older report's assertion text.

## Final-build regression — 2026-10-24

The final test APK is
`2317fd88dfc276736b30e1c432702bf1d5c3bc1aaadebc02d26b8b5f731e67c6`.
The normal, abrupt and partial scenarios pass in session
`7b0b2c47-01bd-403d-a78b-6f594eaa4089`: native PIDs 14172/14256/14315,
15/5/5 check groups, exits 0/23/24. Normal retains exactly 61 pointer packets;
partial EOF is still reported explicitly. All three final censuses are empty.

That collection's keyboard case failed its seven-second IME visibility wait.
The saved screenshot has no visible keyboard; system logs show multi-second
rendering delays and Gboard's view appearing after the wait. The timeout was not
increased and the failure remains recorded. The separate keyboard rerun passes
all nine groups in `84808792-5dcb-4543-973d-3cbf27d6bc78`: native PID 14505,
15 keyboard packets, 321 frames received/drawn, exit 0 and an empty UID census.
This is functional evidence with a timing failure retained, not a latency claim.

The cursor rerun passes 12 groups in `a9daaad7-4f4f-4d05-8104-d3d3a761daf6`:
native PID 14602, 25 pointer packets, 176 frames received/drawn and exit 0.
The combined D-pad/IME rerun passes 12 groups in
`d60a6348-ab5c-42b9-8b69-09dfc640bfe0`, native PID 14701, exit 0.
Both final UID censuses contain no surviving child.

The final profile pair passes in `e336d6a9-e2d9-4843-8e63-c34e1fead5a1`:

| Scenario | Observed evidence |
| --- | --- |
| Profile recreation/rotation | Ten groups; host 14769/native 14793; three Activity creations, one launch; 54 pointer packets, three balanced pairs; 286 frames received/285 drawn; exit 0 |
| Fresh-process disk read | Five groups; host 14839/native 14865; one launch; 22 pointer packets, one balanced pair; 207 frames received/drawn; exit 0 |

Both final UID censuses are empty. All eight scenarios have passing evidence on
the final APK across these collections; this was not one uninterrupted full pass.
The official-lab and harness source hashes match the final checkout. The final
profile screenshot was inspected at original resolution. The host/codec count
remains 33 lab tests plus nine archive tests.

## Limits and next work

The Android restoration proof uses the key-based D-pad profile. Other serialized
profile types have host codec coverage and earlier adapter evidence, not a new
physical-device or persistence acceptance claim. The interruption check stages
an unfinished file; it does not simulate device power loss or real disk exhaustion.
Fresh-process loading creates a new fixture session, not resurrection of a killed
native process. This does not establish persistence of Picotron projects/home.

A product settings UI, per-device profile selection, hardware reconnection and
official SDL event consumption remain open. Subsequent owned fixtures cover
[host commands](HOST_MENU.md) and [saved secondary/wheel actions](CURSOR_ACTIONS.md).
Session-level selection is now available through [shared controls](SESSION_INPUT.md);
the hardware gate remains [real Picotron pointer input](RETROID_INPUT_CHECK.md).
