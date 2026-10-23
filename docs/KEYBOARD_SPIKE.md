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

Android key mapping, IME connection behavior and the native fixture are the next
checks. The official Picotron launcher does not attach a keyboard channel. A
passing fixture must not be relabeled as successful SDL or Picotron consumption.
The future SDL adapter needs actual text-input enablement, key-code/modifier
translation and runtime observations before it can be enabled.

References: Android [BaseInputConnection](https://developer.android.com/reference/android/view/inputmethod/BaseInputConnection)
defines composition versus commit and connection lifecycle. SDL2
[text input events](https://wiki.libsdl.org/SDL2/SDL_TextInputEvent) use UTF-8 and
split long text at codepoint boundaries. Neither reference proves the owner's
Picotron build handles a given IME or glyph.
