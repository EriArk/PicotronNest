# D-pad cursor and simultaneous IME experiment

This extends the [relative cursor fixture](CURSOR_SPIKE.md) and the
[keyboard fixture](KEYBOARD_SPIKE.md) while the owner keeps Retroid available
for other work. Both adapters remain disabled in the official Picotron launcher.
It is preparation for devices without analog sticks, not hardware or product
acceptance.

## Direction and routing rules

`RelativePointer.Mode.DPAD` moves through held direction state and elapsed time,
not Android key-repeat frequency. Opposite directions cancel; releasing one
restores the remaining direction. Diagonals are normalized. Existing speed,
precision, fractional accumulation, clamping and 50 ms tick cap still apply.
Focus, mode/profile changes, resize and source removal clear held directions.

One source owns an active drag. Other sources cannot move or release it. Outside
a drag, a newly pressed direction may select a different driver; releases from
the displaced source cannot stop the new driver or restore old movement. A new
drag owner clears another source's movement. There is no automatic resurrection
of an old hold after focus returns.

A profile explicitly selects either four direction key codes or two HAT axes,
plus primary and precision buttons. Keys and HAT events representing the same
directions are not combined: the unselected representation is consumed without
moving or forwarding editor keys. Direction/primary/precision conflicts are
rejected. Hat values use normalized device ranges and a 0.5 threshold; the
existing explicit synthetic-device flag is used only by tests.

Controller sources (`SOURCE_DPAD`, `SOURCE_GAMEPAD`, `SOURCE_JOYSTICK`) go to the
active cursor adapter, while physical keyboard arrows still go to the keyboard
adapter. With cursor mode disabled, controller events are not silently converted
to editor arrows. This does not implement native cartridge gamepad forwarding.
Android documents both [D-pad event representations and source checks](https://developer.android.com/games/sdk/game-controller/controller-input).

## Keyboard-visible viewport

With the experimental keyboard attached, the API 30+ host uses the bottom IME
inset to reduce the drawable viewport. Video fit, direct-pointer inverse mapping
and the relative cursor all use that same height. An inset change cancels the
active pointer gesture and direction hold; it does not invalidate the active
text connection solely because the visible viewport changed. Focus loss still
releases both adapters and invalidates stale IME connections.

The tested case is the docked keyboard on the full-screen API 35 emulator.
Floating/split keyboards, side insets, multi-window, landscape usability and
older Android keyboard layouts need separate checks. No animation-smoothness
or latency claim is made. See Android's [keyboard inset guidance](https://developer.android.com/develop/ui/views/layout/sw-keyboard).

## Reproduce

Follow the [owned harness build/install procedure](P1_BRIDGE_TESTS.md), then:

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode combined
```

The default runner now executes six scenarios, including `combined`. It retains
`combined.json`, a native transcript, build/device hashes, UID census and an actual
`combined.png` under ignored `.local/bridge-tests/evidence/<session>/`. Inspect
screenshots at original resolution when a reduced viewer preview looks incomplete.
Only owned strings and diagnostic red/blue frames are used; no user runtime or
project content is included.

The combined scenario injects key and motion events into the actual Android View,
opens the real system IME, calls commit on its system-created input connection,
and checks both byte streams at the same authenticated native process. It tests
keyboard-arrow/controller separation, both D-pad representations, profile conflict
rejection, IME open/close during drag, frame fit, shared focus loss, stale text
rejection and balanced shutdown. It does not simulate typing through Gboard's
on-screen keys or prove that a physical controller's events bypass the IME.

## Observed result — 2026-10-23

Android 15/API 35 `sdk_gphone64_x86_64` emulator, SELinux Enforcing, target 36.
Evidence UUID: `7245e501-c9da-4198-b8ae-35a60199bd11`.
Test APK SHA-256: `f94bf63852a181dafe5f0c56b3f3da60da372364b2fb32aaf5c901267721f7aa`.
Native fixture SHA-256: `444a8fe81c259ccd72fd1c48ebd975014457c71f2de32b6a03bc1a8682d9f817`;
the native decoder and both wire protocols are unchanged.

All 12 combined check groups pass. Host PID 13564, native PID 13591, UID 10228;
both socket peer identities match the native process. One launch, one Activity
creation, exit 0 and no surviving same-UID child. The receiver consumed 74 pointer
packets with five paired down/up edges, and seven keyboard packets with three
paired keys plus 24 UTF-8 bytes. It received `print("D-pad + IME ✓")` exactly once.
502 complete frames reached Android and sequence 502 was drawn. Counts reflect
this run's scheduling, not a frame-rate or input-latency benchmark.

The full View remained 2340 pixels high. Opening docked Gboard reported an
833-pixel bottom inset and reduced the frame/cursor viewport to 1507; hiding it
restored 2340. Pointer capture released on both changes. The original-resolution
1080x2340 screenshot was visually inspected and shown in chat: the complete
owned frame and cursor sit above Gboard, with the native text receipt visible.

The same APK passes the other five scenarios:

| Scenario | Evidence |
| --- | --- |
| Normal input/lifecycle | 15 groups; PID 13162; exit 0; 61 pointer packets; 154 frames received and drawn |
| Abrupt native exit | Five groups; PID 13244; expected exit 23; input disabled and child gone |
| Partial-frame exit | Five groups; PID 13303; expected exit 24 plus partial EOF |
| Keyboard/IME | Nine groups; PID 13364; exit 0; 15 keyboard packets; balanced cleanup |
| Relative touchpad/stick | 12 groups; PID 13521; exit 0; 26 pointer packets; balanced cleanup |

All six UID censuses contain no surviving child. The initial focused combined
run also passed (UUID `3ed38eb0-a643-450a-8b0c-2e7b9f7d4121`); the final run adds
binding-conflict rejection and key-profile duplicate-HAT coverage.
32 lab tests and nine archive tests pass, including the new pure-Java D-pad
state suite. Existing pointer, transport and keyboard suites remain passing.

The official desktop lab builds with the optional host hooks, APK SHA-256
`5decd098e28dc945633195cd7aeda52084b96cc751b882a63324641f9203a788`.
It was not installed on Retroid; it attaches neither the experimental keyboard
nor the relative cursor. No APK release or proprietary material was published.

## Remaining work

Physical controllers (including real key-vs-HAT reporting), controller navigation
of the IME itself, configurable acceleration, saved profiles, host menu/exit,
secondary click/wheel and real Picotron event consumption remain open. The next
hardware gate is still the [official-runtime pointer procedure](RETROID_INPUT_CHECK.md).
