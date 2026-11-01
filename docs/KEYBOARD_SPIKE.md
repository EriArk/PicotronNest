# Keyboard and Unicode adapter preparation

The owner accepted the cross-device interaction direction and requested continued
work without occupying Retroid. This is a bounded keyboard experiment alongside
the still-open official-runtime P1b input check. It does not establish keyboard,
IME or Unicode support in official Picotron.

## Host contract

`KeyboardState` tracks each input source separately. Two keyboards holding the
same key produce one aggregate down/up pair. Releasing or removing one source
does not release another source's held key. Repeated non-modifier keys preserve
repeat semantics. Focus cleanup releases ordinary keys before modifiers.

`KeyboardProtocol` separates physical key transitions from committed text.
Its experimental 64-byte little-endian packets contain seven 32-bit fields:
magic `0x504e4b31`, sequence, type (key/text/reset), scan, flags (down/repeat),
aggregate modifiers (Shift=1, Ctrl=2, Alt=4, Meta=8), and UTF-8 byte length.
Text follows at offset 28, at most 31 bytes, with zero padding. These modifier
bits are host protocol values, not SDL modifier constants. The existing pointer
protocol is unchanged; a separate channel avoids interleaved socket writers.

Text chunks preserve complete Unicode scalar values. NUL, isolated UTF-16
surrogates and oversized commits are rejected before any packet is queued.
A commit is bounded to 1024 UTF-16 units / 2048 UTF-8 bytes. The bounded writer
accepts an entire commit's packets or reports overflow, queues a keyboard reset
and rejects later input. Closing with accepted but unsent input is a failure.

Host validation: 30 lab tests pass, including two-source modifier ownership,
repeat and release order, Cyrillic/CJK/emoji UTF-8 round trips across chunk
boundaries, malformed Unicode, ordered transmission and overflow reset.
These are synthetic, owned test strings; no user's typed text is collected.

## Integration boundary

The later [D-pad/IME fixture](DPAD_AND_IME.md) exercises the keyboard and cursor
simultaneously, with controller-source filtering and an IME-aware viewport.

Android key mapping, a limited IME connection and an owned native decoder now
have emulator evidence below. The official Picotron launcher does not attach a keyboard channel. A
passing fixture must not be relabeled as successful SDL or Picotron consumption.
The [native keyboard extension](NATIVE_KEYBOARD.md) now provides shared SDL
event construction, text-input gating and a bounded socket reader, with an
optional endpoint compiled into the observer. The Android channel owner and
official-runtime consumption still need integration and validation.

`KeyboardInput` maps Android letter, digit, punctuation, navigation, function
and left/right modifier keys. Committed physical-key text is separate from
key transitions; Ctrl/Alt/Meta combinations do not emit text. Source removal
releases that source, while focus loss releases all keys and invalidates old
IME connections. Disconnect unregisters the Android device listener.

The limited `BaseInputConnection` buffers composition until commit/finish and
discards pending composition on focus loss or close. It accepts cursor position
1, single-codepoint backspace in pending composition, and single backspace or
forward-delete key pairs otherwise. It rejects unsupported selection/context
operations and multi-character deletion. The native fixture validates packet
shape, UTF-8, key transitions and modifier consistency before changing state.

This is not a complete editor connection: no document shadow, cursor/selection
synchronization, displayed preedit, autocorrection, dead-key/AltGr composition,
lock keys or numeric-keypad mapping. Physical keyboards, hot-plug and real IME
candidate selection still need device tests. The fixture calls composition and
commit methods on the system-created connection while Gboard is visible; it
does **not** automate typing those Unicode strings through Gboard's keys.

## Reproduce the owned emulator check

Use the [bridge harness build/install procedure](P1_BRIDGE_TESTS.md), then:

```powershell
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode keyboard
```

The default run also includes this scenario after normal, abrupt-exit and
partial-frame checks. Only the owned test Activity attaches `KeyboardInput`;
the production launcher leaves it disabled. The test injects key events into
the real View and simulates device-removal callbacks. Its labeled screenshot
shows the actual emulator and text reconstructed from the native receiver's
UTF-8 byte receipts. Only owned fixture strings are logged. Screenshots and
reports stay in ignored `.local/bridge-tests/evidence/<session>/`.

## Observed result — 2026-10-23

Android 15/API 35 x86_64 emulator, SELinux Enforcing, test host target 36;
Gboard package `com.google.android.inputmethod.latin`. Evidence UUID:
`13b83922-c197-4c96-9c4e-55dd1b8fe770`.
APK SHA-256: `a509cabe148fcb26607aaffcc3cc15f5cba297b99c4d2533064ea9dabcd87e78`.
Native fixture SHA-256: `444a8fe81c259ccd72fd1c48ebd975014457c71f2de32b6a03bc1a8682d9f817`.

All nine keyboard check groups pass. Native PID 10870 matches both authenticated
socket peers; host PID 10847, UID 10228. One launch, one Activity creation, exit 0
and no surviving same-UID child. The native receiver consumed 15 packets, five
balanced down/up pairs, one repeat and 45 UTF-8 bytes; 151 complete frames reached
Android and sequence 151 was drawn. Checks cover:

- Two simulated keyboard sources sharing Ctrl, repeat and removal without
  premature release; Ctrl+C emits no text.
- A physical-key event path emits `a` once; composition replacement then commit
  sends `print("Привет, 世界 👋")` once, across two complete UTF-8 chunks.
- A real dialog releases held Shift; the old IME connection rejects later
  commit/finish. A new connection finishes `новый` and emits a backspace pair.
- Malformed UTF-16 and unsupported deletion are rejected. Pending composition
  is never sent after focus loss. Receiver cancellation remains bounded.

`keyboard.png` in that private evidence directory was captured from the emulator
and displayed in the owner conversation. The leading `a` is the separate key
test, not duplicated composition. Visible glyphs belong to the Android receipt
label; they do not establish Picotron font or editor support.

An earlier run (`445334d4-d829-4e40-a6ad-1901d909a645`) failed the visible-IME
assertion. The harness now awaits a new system connection and active editor
before requesting display, with focus/request diagnostics on failure. The
focused rerun and final four-scenario run pass; this is not a claim of broad
IME compatibility. Real IME key/candidate interactions remain untested.

The official desktop lab also builds with these optional host hooks, APK SHA-256
`0f0b6a3e749fcbf2072f8665cc8f05bed20ccbf90f6789b4c34fcfa9c6f454b3`.
It was not installed on Retroid and does not attach this keyboard adapter.

References: Android [BaseInputConnection](https://developer.android.com/reference/android/view/inputmethod/BaseInputConnection)
defines composition versus commit and connection lifecycle. SDL2
[text input events](https://wiki.libsdl.org/SDL2/SDL_TextInputEvent) use UTF-8 and
split long text at codepoint boundaries. Neither reference proves the owner's
Picotron build handles a given IME or glyph.
