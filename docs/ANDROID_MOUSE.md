# Android absolute mouse adapter

## Scope and API boundary

`MouseInput` is an explicitly selected adapter in the owned bridge fixture. It
routes absolute mouse MotionEvents through the existing PNI2 sender/decoder.
The official-runtime launcher does not select it. There is no physical mouse,
Bluetooth/USB hotplug or official Picotron input acceptance in this increment.

Android delivers mouse drag/down/up to `onTouchEvent`, and hover, scroll and
button changes to `onGenericMotionEvent`. Both View paths feed the same state.
The adapter uses the full `getButtonState()` mask, so DOWN/BUTTON_PRESS and
UP/BUTTON_RELEASE duplicates do not fabricate extra button edges. Unsupported
button bits are ignored; only primary and secondary are forwarded. Only absolute
`SOURCE_MOUSE` with a single mouse-tool pointer is supported. Captured relative
mouse, stylus, trackball and accessibility gesture translation are out of scope.
See the [Android MotionEvent contract](https://developer.android.com/reference/android/view/MotionEvent).

## Ownership, geometry and cleanup

- One device owns a mouse drag. Another mouse cannot move, scroll or release it.
  Direct finger touch and mouse capture exclude each other until the owner
  releases. Unheld mouse hover resumes correctly after touch moves the pointer.
- Frame coordinates use the same aspect fit and IME-adjusted viewport as drawing.
  Uncaptured events outside the frame are rejected; captured drags clamp to its
  edges. Non-finite coordinates and wheel values are rejected. An invalid
  release coordinate still releases at the last valid position.
- Horizontal/vertical wheel axes retain their signs. Sub-1/1024-step fractions
  accumulate until representable on the wire. Residues are cleared on device
  changes or cancellation; they never transfer between devices. Axis magnitudes
  over 32 steps per event are rejected rather than silently clipped.
- Focus loss, cancel, frame/viewport changes, Activity replacement, selected
  adapter changes and owner-device removal/change release both buttons. Foreign
  device removal leaves an active drag intact. Hover exit alone is not focus
  loss. Motion with held bits cannot acquire a new drag after cancellation;
  a fresh press is required.
- The adapter registers an Android InputManager listener and unregisters on
  close. A disconnected native session discards local state without claiming
  a delivered release. Stale Views/listeners cannot restart input.

Selecting mouse mode releases/closes the relative cursor adapter. Selecting a
relative cursor/profile releases/closes the mouse adapter. Mouse selection is
session-local and restored across Activity recreation without held state; it is
cleared at disconnect. No saved profile format or product defaults change.
Direct touch remains available when the mouse is not dragging. Keyboard/IME
routing remains separate.

## Reproduce and evidence boundary

Use [the bridge build and runner](P1_BRIDGE_TESTS.md), with `--mode mouse` for the
focused scenario. The default run now includes eleven scenarios. Its input is
synthetic MotionEvents dispatched through the real View, not raw PNI2 injection.
The scenario invokes device-removal callbacks directly; that checks cleanup
logic, not hardware disconnection delivery. The screenshot is a real emulator
capture of the clearly labeled owned fixture.

The scenario exercises opt-in selection, hover, drag clamping, simultaneous
buttons, duplicate edges, foreign-device rejection, wheel sign/fractions and
sub-wire-unit accumulation. It checks malformed/unsupported input, actual dialog
focus loss, mutual touch/mouse ownership, frame resizing, Activity recreation,
switching to the relative adapter, native receipt counts and process exit.

## Validated focused check — 2026-11-01

35 execution-model and nine archive host tests pass. Both target-36 APK builds
pass strict Java/native compilation, payload/signature checks and current-source
hash verification. The official-runtime lab was built but not installed.

| Artifact | SHA-256 |
| --- | --- |
| Owned bridge APK | `18ba9615018b8871dd8f1292e33993d3ed7c8aacd60433d746c42c7b9ffc6a5e` |
| Native fixture (unchanged) | `c8b58dda8732120582aa3c3999d0e683a9cbfa5d54cb0c44bef07d9ab993f685` |
| Desktop-lab APK | `7d5b2d8bfb4ece280957289b07278a5e804a2962b376c0090a7f894148c9fa11` |

Focused session `53a53d64-ce4f-4f9f-b560-1ed174238167` passes 15 check groups.
Host PID 2679/native PID 2713, two Activity creations, one native launch, exit 0.
The authenticated socket peer matches PID 2713. Native receipts contain exactly
31 packets, 11 down/up pairs, final buttons zero and wheel totals (1026, -1024)
wire units. All 262 frames were received/drawn, and the final same-UID census is
empty. Frame counts are accounting, not performance measurements. The actual
1080x2220 screenshot was inspected at original resolution and shown in chat.

The final full collection `95b5d635-0d07-47ae-9ffb-86ed17b4de02` passes all eleven
scenarios on the same APK: normal, abrupt, partial, keyboard, cursor, combined,
profile, profile-read, menu, pointer and mouse. Every final same-UID census is
empty. The final mouse scenario again consumes 31 packets and 11 balanced button
pairs, with one native launch and exit 0 (PID 5632).

An earlier full-run attempt, `5dbcf508-fefc-4f32-9ccd-295ab79eb065`, passed normal,
abrupt and partial before keyboard failed its seven-second visible-IME wait.
The report records `requested=true viewFocus=true windowFocus=true active=true`;
it does not prove why Gboard had not appeared. The owned child was forcibly
cleaned up by instrumentation. A focused keyboard rerun,
`e4586fc8-812d-4108-94c8-da050fa746b2`, passed all nine groups (PID 4006, exit 0),
followed by the complete passing run above. No source or deadline changes were
made between these runs. The passing reruns' final UID censuses confirm no surviving children.

## Test environment recovery

The previous emulator executable and `J2ME_Portable_Preview` image/AVD were absent.
A separate `PicotronNest_Mouse_API35` AVD was created, without changing the other
existing AVD or Retroid. It uses Android 15/API 35 Google APIs x86_64 revision 9,
1080x2220, 2 GiB RAM, four virtual CPUs, hardware keyboard disabled, SELinux
Enforcing and Windows WHPX. Android Emulator 37.2.13 cold starts did not provide
a usable test session within the observed startup windows. They are not APK
test failures or successful input evidence. HDD-backed images and diagnostic
boot attempts were retained privately; no host virtualization settings changed.

The successful setup used Android Emulator 36.6.11 from the
[official archive](https://developer.android.com/studio/emulator_archive),
`emulator-windows_x64-15507667.zip`, checksum
`7dfb2c47291db7e0fd7ce45e541706d45827bc9875f5f736bf785585b549e0e4`.
The executable, read-only images and writable data were copied to an owned
temporary SSD directory after the earlier emulator stopped. Launch flags were
`-no-window -no-audio -no-snapshot -gpu off -no-boot-anim`, with explicit
`-sysdir` and `-datadir` paths and serial `emulator-5554`. The process-local
`ANDROID_HOME`, `ANDROID_SDK_ROOT` and `ANDROID_AVD_HOME` pointed at the isolated
SDK/AVD. Existing SDK license acknowledgements were reused; no new terms were
accepted. Require `sys.boot_completed=1` before installing/running. Existing
application/native test deadlines were not enlarged for this recovery.

The owned emulator was stopped after collection and logs were copied back under
`.local/desktop`. Automatic approval review rejected the combined cleanup command
with `blocked by policy`; deletion was not retried through another mechanism.
The temporary SSD tree remains at `%TEMP%/picotronnest-mouse-20261101`. The isolated
SDK, original AVD, reports and screenshots remain under ignored `.local` paths.

## Next integration

Add configurable secondary-click and wheel actions to the touch/controller
adapters, with compatible saved bindings and the same release/source rules.
Actual mouse reconnection, native pointer visibility, right-click behavior,
double-click behavior and official workstation workflows still need hardware
evidence. The [functional beta gate](BETA_READINESS.md) remains open.
