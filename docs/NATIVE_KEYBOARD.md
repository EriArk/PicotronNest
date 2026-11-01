# Native keyboard and Unicode bridge

## Implemented boundary

The SDL observer now has an optional native keyboard endpoint alongside its
existing frame/pointer socket. This increment implements and tests the shared
record reader and SDL event construction. The Android official-runtime launcher
does **not** open this channel yet. No keyboard, IME or text-entry acceptance in
Picotron is claimed, and Retroid remains reserved.

`keyboard_sdl.h` translates the existing 64-byte PNK1 records into bounded
`SDL_KEYDOWN`, `SDL_KEYUP` and `SDL_TEXTINPUT` structures using the pinned SDL2
2.32.10 headers. It preserves repeat, timestamp, window ID, scan code and all
eight left/right Shift/Ctrl/Alt/GUI bits. The observer obtains key symbols from
the real `SDL_GetKeyFromScancode`; the owned fixture supplies a documented test
keymap callback, not an alternative production keyboard layout.

UTF-8 text remains separate from key transitions. A validated chunk is at most
31 bytes with a terminating zero; Unicode scalars cannot be split by the host
encoder. Reset releases each held key, updating modifiers as releases are
delivered. At most 511 release events fit in a fixed 512-event buffer. A new
record cannot overwrite pending events. Malformed records leave protocol and
event state unchanged until the channel deliberately performs failure cleanup.

`keyboard_channel.h` reads one record without blocking, preserves partial bytes
across calls and bounds read attempts. EOF, truncated EOF, I/O failure and invalid
records have distinct sticky errors. Failure discards unconsumed input and queues
release events for keys already delivered. The caller owns the descriptor and
closes it once. A non-consuming peek leaves both event index and sampled key
state unchanged. Text is suppressed when SDL text input is inactive, including
when it becomes inactive after a peek; suppression is counted separately.

The observer enables this endpoint only when its process environment explicitly
contains `PICONEST_KEYBOARD_SOCKET`. It requires the frame socket, validates the
abstract socket name and checks the server's UID. The host launch currently sets
no such environment entry. No runtime filename, archive layout or Picotron code
has been changed. Neither text payloads nor typed strings are logged by the
observer; fixture test strings remain synthetic.

With the endpoint enabled, `SDL_PollEvent` alternates available keyboard and
pointer events, preserving order within each channel. `SDL_GetKeyboardState`
and `SDL_GetModState` expose the keyboard state already delivered by that poll
path. Without the endpoint, those calls delegate to SDL as before. Startup,
packet/event/text totals, suppression, pending records and failure are recorded.
The existing 20-second quit request and outer process deadlines are unchanged.

## Acceptance guard

The desktop build manifest records `desktop_keyboard_bridge: true` and hashes
all three keyboard headers. This denotes compiled capability, not enabled input.
`check_report.py` rejects native keyboard errors. If any native keyboard marker or host
keyboard receipt appears, it additionally requires:

- one connection and one complete native termination record;
- a `desktop_keyboard` host receipt with the observed native peer PID, integer
  `accepted` and `packets` totals, no error, and exact sender/receiver agreement;
- balanced downs/ups, no held keys, pending events or buffered bytes;
- consistent repeat/text event counts and no suppressed text.

The current official host does not yet produce that receipt, so simply setting
the native environment variable cannot produce a passing keyboard acceptance
report. Unit tests use synthetic reports to exercise rejection of mismatched
peers, loss, pending input, inactive text and native errors. Zero-input success
would still not prove usable keyboard or text entry.

## Reproduction

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -m unittest discover -s tools -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode keyboard
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
```

The owned native fixture runs translation and real `socketpair` checks before
emitting its READY marker. Every split position 1..63 must retain an incomplete
record, then deliver exactly one event when completed; every corresponding
partial EOF must release the already delivered modifier exactly once. Additional
checks cover empty EOF, I/O failure, malformed input, 31-byte UTF-8 text, repeats,
all eight modifiers, peeking, inactive text and maximum-size reset. Live fixture
keyboard packets also pass through the shared event builder and are drained
before their receipt counters are printed. The keyboard instrumentation compares
sent packet and constructed-event totals and final released state.

## Evidence (2026-11-01)

The final fixture APK is SHA-256
`8ec6c66deeb571e7cac0797d9c7fc3e4fa19aa095e3fb27dbf01ea58867f2bdb`,
with native fixture
`ae7eb38c7f8e5cf665ccba5a49b7047fcebb4b9c2cc1c895df3416733dd67a5f`.
The ARM64 desktop lab builds as
`6a356a832f21902300c5a05fdb7291417226d35bc956964618626d1f93ce3ce0`.
Both manifests match the current source hashes. No new bundled dependency was
introduced; the pinned SDL/glibc provenance and existing closure are unchanged.
36 lab host tests and nine archive tests pass.

Focused keyboard collection `5e32ae5b-cc38-4f44-b38c-5ad31f05fda2` passes all ten
check groups on `emulator-5554`, AVD `PicotronNest_Mouse_API35`, Android 15/API 35,
x86_64, SELinux Enforcing; host target API 36. Its host/native PIDs are 2424/2563.
Both shared native self-test markers pass. The authenticated keyboard receiver
records 15 packets and 15 constructed SDL events, five balanced key pairs, one
repeat and four text events containing 45 UTF-8 bytes. Final held/modifier state
is zero, exit is 0, and the final UID census contains no surviving child.

All fourteen scenarios pass in one subsequent final-APK collection
`65b8ffaf-a3f8-4b44-92b3-b0cacbee730c`: normal, abrupt, partial, keyboard, cursor,
combined D-pad/IME, profile, fresh-process profile read, menu, pointer, mouse,
actions, fresh-process actions read and shared session. Each scenario executes
both shared native keyboard self-tests and ends with no surviving child. The
intentional abrupt/partial cases retain their expected exits 23/24; other cases
exit 0. There were no instrumentation failures in these two collections.

The unedited `keyboard.png` was inspected at original resolution and shown in
chat. It captures the intermediate ten-event/three-text-event stage, synthetic
Cyrillic/CJK/emoji receipt, owned red/blue frame and docked Gboard. It is not a
Picotron screenshot or proof of official editor text entry. Reports, installed
APK/source identity checks, screenshot and census remain under ignored
`.local/bridge-tests/evidence/<UUID>/`. No APK or proprietary material was published.

### Emulator setup correction

The initial retained AVD launch stayed offline; its restart booted but APK
installation stalled during heavy guest I/O wait. The generated
`hardware-qemu.ini` showed that `-datadir` had not overridden the AVD's writable
partition paths: they still pointed into the original D: checkout. That disk
also had a high queue at inspection. The installer was stopped and the owned
emulator was shut down through its console, without deleting either data tree.

A separate `%TEMP%/picotronnest-mouse-20261101/key-sdl-avd` AVD registry now points
`PicotronNest_Mouse_API35.ini` directly at the retained SSD `data` directory.
Only this launch's `ANDROID_AVD_HOME` uses that registry. The generated hardware
configuration was checked to place system, vendor, cache, userdata and encryption
images on C:. This corrects the earlier assumption that `-datadir` alone selected
all writable SSD images. Boot completion was checked before installation; no
test deadline, host virtualization setting or physical-device state was changed.
Launch logs remain private under `.local/desktop/key-sdl-emulator*.log`.
After collection, the named AVD was stopped through its console and its owned
QEMU process exit was verified. Both retained data trees remain intact.

## Remaining integration

The actual ARM64 observer hooks, SDL keymap and Picotron event consumption are
build-tested only. Owned tests use the same reader/event construction but do not
load official Picotron or execute the full SDL observer. Event-filter behavior,
`SDL_WaitEvent`/`SDL_PeepEvents`, state sampling driven solely by `SDL_PumpEvents`,
lock keys, layout/AltGr handling and physical devices remain unproven. There is
no global ordering guarantee across the two sockets; modifier-plus-pointer
workflows need an explicit host coordination policy and acceptance checks.

Next, share an authenticated Android keyboard-channel owner between the fixture
and official session, attach/detach the adapter through the same lifecycle,
produce the required receipt and provide explicit keyboard/IME access. Test
peer mismatch, cancellation before connect, failed writes, focus release and
modifier-plus-pointer ordering before enabling it for a hardware candidate.
Audio, persistent projects, SAF import and product setup still keep the
[functional beta gate](BETA_READINESS.md) open.
