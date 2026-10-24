# Secondary button and wheel transport

## Scope

The owned Android/native fixture now carries independent primary/secondary
button state and fractional two-axis wheel deltas. It shares the decoder and
SDL event builder with the ARM64 desktop observer. This is transport and event
construction evidence, not physical mouse support or Picotron event consumption.
The new scenario injects session transport records directly; it does not test
Android mouse MotionEvents, touch gestures or controller bindings.

## Wire and lifecycle contract

`PNI2` is a fixed 28-byte little-endian record:

| Offset | Value |
| --- | --- |
| 0 | Magic `0x504e4932` |
| 4 | Strictly increasing sequence, 1 through `INT32_MAX` |
| 8, 12 | Absolute frame x/y, each 0 through 2047 |
| 16 | Full button mask: primary=1, secondary=2, both=3 |
| 20, 24 | Signed horizontal/vertical wheel deltas, 1024 units per step, each bounded to +/-32768 |

Positive wheel x means right; positive y means up/away from the user, matching
SDL's normal wheel direction. Deltas are impulses, never held state. Non-wheel
records always carry zero deltas. The FIFO preserves every accepted record;
overflow is an explicit failure and queues a zero-button, zero-wheel emergency
release. Invalid producer values are rejected before enqueueing, preserving the
writer thread. Sequence exhaustion is a transport failure, not wraparound.

The native decoder validates the entire record before mutation. Unknown button
bits, old magic, invalid sequences, coordinates and wheel limits are rejected.
Presses and scrolling require coordinates inside the current frame. A zero-wheel
release can clamp coordinates from the previous frame size so resizing cannot
prevent both buttons from being released. Down/up counters count each button
edge, including simultaneous transitions. `PointerState.release()` is idempotent
for either button or both. Source ownership remains the responsibility of the
input adapter; the transport itself does not arbitrate devices.

This deliberately changes the private experimental protocol from 20-byte PNI1.
There is no version negotiation or mixed-version support. Rebuild/install the
paired Java sender and native observer together; do not reuse an older observer
with the new sender. The verified headless base contains no pointer observer;
the desktop build recompiles it and records the shared header hashes. This
does not change the saved cursor-profile schema or any user data format.

## SDL translation and dependency boundary

`pointer_sdl.h` constructs at most four events per record: motion, the changed
button edges in primary/secondary order, then wheel. It maps secondary to SDL's
right button and `SDL_BUTTON_RMASK` (4), rather than treating wire bit 2 as SDL's
middle button. `SDL_GetMouseState` uses the same mapping. Precise wheel fields
retain each fractional delta; integer wheel fields accumulate signed remainders
instead of dropping small deltas. Ordinary motion and release do not replay
wheel events. Timestamp, window ID and wheel pointer coordinates are populated.

The fixture compiles against checksum-verified SDL 2.32.10 headers from the
existing `headless.lock.json`; it does not link or bundle the SDL library.
The APK includes the SDL zlib license and NDK notices. ELF dependencies are
allowlisted to libc/libdl, the APK payload is exactly allowlisted, and the build
manifest records the SDL source pin, ELF dependencies and source hashes. The
official observer continues to link the previously audited SDL/glibc closure.

## Reproduce

Use the prerequisites and isolated emulator in [the bridge guide](P1_BRIDGE_TESTS.md).

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode pointer
```

The default runner includes this as its tenth scenario. The native selftest
checks malformed records without mutation, wheel bounds, fractional accumulation
and actual SDL event fields (including four-event capacity and independent
button release). The Android scenario checks fragmented socket delivery, right
drag/chords, wheel bursts, resize cleanup, authenticated native PID, clean exit
and rejection after the session ends. The captured `pointer.png` explicitly
labels transport injection and absence of a physical-mouse test.

## Validated evidence — 2026-10-24

- Host checks: 35 execution-model tests and nine archive tests pass.
- Final fixture APK SHA-256: `3880533c0de441d710fc04a94e6974cdef50d08f3083cb082dd2783cbd869db3`.
- Native fixture SHA-256: `c8b58dda8732120582aa3c3999d0e683a9cbfa5d54cb0c44bef07d9ab993f685`.
- Matching target-36 ARM64 desktop-lab APK SHA-256: `4767c90ba83a2ffc69a9baed73388b5630f3657bf9bedae8c2e6d52277f14735`; built, not installed.
- Final collection: `1bce38c1-eade-4aae-acad-00b8deff4c11`, Android 15/API 35,
  x86_64 `J2ME_Portable_Preview`, SELinux Enforcing. All ten scenarios passed
  in one uninterrupted run on the final APK. Every final same-UID census is empty.

| Scenario | Check groups | Native PID | Exit |
| --- | ---: | ---: | ---: |
| normal | 15 | 3754 | 0 |
| abrupt | 5 | 3842 | 23 (expected) |
| partial | 5 | 3895 | 24 (expected) |
| keyboard | 9 | 3945 | 0 |
| cursor | 12 | 4032 | 0 |
| combined | 12 | 4094 | 0 |
| profile | 10 | 4161 | 0 |
| profile-read | 5 | 4225 | 0 |
| menu | 10 | 4282 | 0 |
| pointer | 10 | 4360 | 0 |

The pointer scenario used host PID 4337, one Activity creation and one native
launch. Its authenticated socket peer was PID 4360. The native receiver consumed
24 pointer packets, four down/up pairs and final button mask zero. Wheel receipts
totalled (-3072, 1024) units and (-3, 1) whole steps. It produced 28 frames, with
sequence 28 drawn. These are accounting receipts, not performance measurements.
The actual 1080x2340 screenshot was inspected at original resolution and shown
in chat; it depicts the owned red/blue fixture, not the official desktop.

Both final build manifests match the source hashes. The fixture's only native
dependencies are libc/libdl, and both APK builds passed payload/signature checks.
An earlier protocol-only build also passed its nine-group pointer scenario in
collection `1515ebc2-7cbe-4658-9611-693a0ef82da4`; the final collection above adds
the shared SDL event-builder check and supersedes it for current evidence.
Retroid was not installed to, launched or interacted with.

## Remaining integration

Implement source-aware Android mouse routing and configurable secondary/wheel
actions for touch/controller adapters, including focus loss, device removal and
mode switches. Existing primary-only cursor profiles need an explicit compatible
extension before saving new bindings. Then verify official Picotron consumption
and real editor/file workflows on the owner's freed device. This increment does
not meet [the functional beta gate](BETA_READINESS.md); Retroid remains reserved.
