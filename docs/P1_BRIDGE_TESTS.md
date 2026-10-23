# Owned bridge tests without Retroid

## Scope

These tests exercise the actual Android `DesktopView`, `FrameReceiver`,
`FrameStream`, `PointerSender` and Activity launch gate. A small owned Bionic
process supplies diagnostic red/blue frames and consumes input using the exact
`pointer_protocol.h` decoder included by the SDL observer. It is not Picotron,
does not link SDL, and cannot prove SDL event consumption or editor behavior.

The separate `art.picotronnest.bridgetest` APK is developer test infrastructure,
not a product dependency or second required end-user app. It contains original
test code, the shared host sources, an owned x86_64 native fixture and NDK notices.
It has no permissions, proprietary payload, imported runtime or persistent home.
Packaging uses an exact payload allowlist. No APK is published.

## Reproduce

Prerequisites match the execution lab: Windows/Python, Android Studio JDK,
SDK/build-tools 36 and NDK 28.2.13676358. The existing private lab signing key is
used. Build sources and installed APK hashes must match at collection time.

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 shell pm list packages art.picotronnest.bridgetest
# Install only into an absent namespace, or update this previously verified test APK.
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
```

The runner refuses physical devices and non-x86_64 emulators. It does not install
or clear application data. Ensure the emulator is awake and available; an
unrelated dialog must not be dismissed blindly. Instrumentation requires real
window focus before input tests. Its Home/return and orientation changes affect
only the emulator session. Touch events are injected into the real View; they
are not physical finger/controller evidence.

Each collection gets a new UUID and preserves build/device metadata, individual
reports and instrumentation output under ignored `.local/bridge-tests/evidence/`.
The collector verifies installed APK hash, source hashes, fresh session IDs,
one process launch, and final numeric-UID process census. No unexpected child
may survive. Native fixtures have a 90-second alarm and instrumentation is
bounded by a 120-second collection timeout. A timeout is a failure, not PASS.

## Observed result — 2026-10-23

Android emulator `sdk_gphone64_x86_64`, Android 15/API 35, SELinux Enforcing;
test host targets API 36. APK SHA-256:
`bb12b7292d4bb0b6480148a24cb823617bdcd83a80a3227db357db7bac291916`.
Native fixture SHA-256:
`aecd1b2490a2c0a17ef17585d074a47c80b32b84d280ac8027adf3c3c26110ae`.
Evidence UUID: `cea6f806-388d-4a91-ad38-82be21b2b7d3`.

| Scenario | Observed evidence |
| --- | --- |
| Normal input and lifecycle | 15 check groups pass; 61 input packets, 30 down/up pairs, held=0; 311 complete frames received and sequence 311 drawn; native peer PID 9068; exit 0 |
| Activity lifecycle | Three Activity creations, one native launch; same process survives explicit recreation, rotation, dialog focus loss and Home/return; each held button releases |
| Native dimensions change during drag | 48x27 becomes 27x48; old capture is cancelled, release clamps into the new bounds, subsequent touch maps correctly |
| Host viewport resize during drag | Changing the Android View's height releases capture even without pausing the Activity |
| Abrupt native termination while held | Exit 23 is recorded; frame-boundary EOF is distinguished from successful process completion; input is disabled and child reaped |
| Partial frame then termination while held | Exit 24 plus `EOFException: partial frame`; incomplete data is not published, input is disabled and child reaped |
| Receiver cancellation | Repeated close terminates before connection and during a connected partial-header read |

Initial attempts were retained as failures: an old execution-lab ANR dialog
prevented the test Activity from acquiring focus. The old lab's UID had only
its host process left; diagnostics were retained and that disposable host was
stopped without clearing data. Retroid and unrelated apps were not manipulated.

## Fixes and limits

The later keyboard-fixture build reran all four scenarios successfully in session
`13b83922-c197-4c96-9c4e-55dd1b8fe770`, APK SHA-256
`a509cabe148fcb26607aaffcc3cc15f5cba297b99c4d2533064ea9dabcd87e78`.
Normal: 15 groups, 61 pointer packets, 152 frames/drawn sequence, PID 10634,
three Activity creations and one launch, exit 0. Abrupt and partial: five
groups each, PIDs 10727/10811, expected exits 23/24; partial EOF remains explicit.
Keyboard: nine groups, PID 10870, exit 0. All final UID censuses contain no
surviving child. [Keyboard details and screenshot provenance](KEYBOARD_SPIKE.md).

An initial rerun (`f0da35a9-3382-400c-b80d-2269f3d4fcec`) exposed test-fixture
starvation: it sent a fragmented frame after every seven-byte input read.
The fixture now schedules frames independently, at most once per 50 ms, and
uses seven-byte header / 257-byte pixel writes while retaining fragmented input
reads. This bounds test traffic amplification; it is not a Picotron performance
measurement. The rapid-click regression passes with the existing timeout.

- A bounded readiness poll makes a waiting server cancellable. Socket shutdown
  wakes reads/writes; thread joins confirm completion instead of relying solely
  on Java interruption or descriptor close.
- Resizing cancels a captured gesture. The common decoder clamps a valid release
  from the previous viewport while keeping press bounds strict; malformed packets
  do not mutate pointer state. This prevents resize from trapping a held button.
- The Activity test subclass replaces only process creation. The production
  Activity's launch gate, pause handler and desktop View remain in use.
- 29 host lab tests pass, including fragmented/invalid frames and real localhost
  duplex transport with 40 rapid click pairs. Archive tests remain separate.
- The ARM64 desktop adapter was rebuilt against its pinned SDL/glibc closure,
  APK SHA-256 `4af7e0e06bf215667fddf255a33170170fc57507e197ffca1239a889f16d57c6`.
  This artifact was **not installed on Retroid**.

Official Picotron pointer behavior, physical input, alternative Picotron video
modes, audio, persistent homes and enforcing ARM64 compatibility remain open.
In particular, a decoder observing rapid down/up edges does not prove that
Picotron's SDL polling/state-sampling loop will consume both edges as intended.
