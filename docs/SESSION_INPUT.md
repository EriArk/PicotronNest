# Shared lab pointer controls

Subsequent integration: [INPUT_BUNDLE](INPUT_BUNDLE.md) adds ordered keyboard,
IME and pointer delivery, editor keys and shared session commands to this panel.
The evidence below records the earlier pointer-selection milestone.

## What is integrated

`MainActivity` now has an explicit `input_controls` opt-in for the official-runtime
desktop lab. Its `LabInputControls` component is also exercised by the owned
Android fixture; the fixture does not replace this component or its selection
callbacks. The ordinary launch remains direct touch with no new controls.

The opt-in provides a mode dialog for direct touch and mouse with direct touch.
Supplying a validated cursor profile also exposes that controller profile and
touchpad mode, with explicit primary/secondary hold and four scroll buttons.
Profiles are explicit settings, never inferred from a handheld name. Merely
supplying a profile does not activate it: the session initially uses direct touch.
This is a diagnostic integration, not a finished settings or controller-only UI.

The controls sit above the actual DesktopView, so its existing frame/input fit
uses the remaining viewport. Opening the selector and changing mode release
held input. Selection survives Activity recreation within the same live process;
the recreated View starts without held buttons. Old controls reject actions,
paused/destroyed controls stop their readiness callbacks, and disconnected
sessions disable selection and reject touch actions. A new process/session does
not automatically restore the previous mode. No runtime, home or project file is
written by this selection flow.
Readiness and mode selection share the transport lock, so EOF between a UI click
and selection cannot attach a new adapter to a disconnected session.

The optional `cursor_profile` Intent string is Base64 for an existing v1/v2
CursorProfile record. Input is bounded to 128 characters and exactly 72/92 decoded
bytes, then validated by the shared codec. Only stick or D-pad profiles are
accepted here; touchpad derives the same validated tuning/bindings with its mode
changed. Invalid configuration shows a diagnostic before attaching input or
starting a runtime. Without a supplied profile, controller/touchpad choices are
absent; product profile editing and touch-only defaults remain open.

## Reproduction

Build and exercise the owned emulator fixture:

```powershell
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554 --mode session
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
```

The new `session` scenario is the fourteenth default scenario. It supplies a
known D-pad profile, checks an invalid launch before the valid launch, drives
the real mode dialog, and dispatches synthetic mouse/gamepad/touch events. It
checks release when switching between direct touch, mouse, controller and
touchpad; a two-button chord and wheel step; recreation with one native child;
rejection by stale controls; restored direct-touch coordinates; and disabled
controls after native exit. The extra rejected Activity explains the total of
three Activity creations with only one native launch.
This integration scenario uses the key-based D-pad profile. Stick/HAT profiles
retain earlier codec/adapter coverage; physical controls and other screen layouts
have not gained new acceptance evidence from this scenario.

For a future authorized ARM64 hardware check, first follow the private import,
build/hash and process requirements in [P1 desktop](P1_DESKTOP.md). The additional
launch option is `--ez input_controls true`. To supply a chosen existing settings
record, encode it locally and pass `--es cursor_profile BASE64_RECORD`. It contains
settings only, not runtime bytes. Example launch shape (not executed on Retroid):

```text
adb -s ARM64_SERIAL shell am start -n art.picotronnest.executionlab/.MainActivity --ez input_controls true --es cursor_profile BASE64_RECORD
```

This does not change the lab's 20-second SDL_QUIT request or outer timeout bounds.
It is not an end-user installation procedure or approval to occupy Retroid.

## Evidence â€” 2026-11-01

Android 15/API 35 x86_64, SELinux Enforcing, target/compile SDK 36; owned AVD
`PicotronNest_Mouse_API35`, emulator 36.6.11, explicit serial `emulator-5554`.
The retained isolated SSD emulator setup was reused; Retroid was not touched.

The initial fixture APK SHA-256 was
`9ee26be27179103c7232837bf1b8b670d98676ff094f65ce345cb655cf2eedfa`.
Focused run `4953ad8b-81f8-4c90-9e4c-93c7f18006a2` passes nine check groups:
host PID 2237/native PID 2322, one native launch, exactly 16 pointer packets,
eight balanced down/up pairs, one upward wheel step, final held mask 0, 96 frames
received/drawn, exit 0 and no surviving child in the final UID census.

An initial full collection (`a6744503-07f7-44ee-bd1d-abe9ea07ce3c`) stopped on
a 15-second ADB timeout while fetching `bridge-normal.json`. Instrumentation had
reported PASS (native PID 2964, 61 packets, exit 0). A subsequent bounded read
recovered the report with the same UUID, and the UID census had no child. The
instrumentation output, recovered report and collection error remain private;
that attempt is not counted as a complete regression run. No timeout was enlarged.

The next collection (`ee75ecb4-7442-4ead-b9b2-c347ed0c45da`) failed the initial
Activity-focus wait before input checks. The native child was reaped. The retained
system log also contains ANRs in Chrome and Health Connect; these do not establish
the cause of the fixture focus failure. A complete fourteen-scenario rerun passed
on this initial APK in `11c25720-3d15-436f-8a55-dc2adecce696`.

Review then removed a readiness/selection race with transport disconnect and
added a closed-session selection assertion. This fixture APK SHA-256 was
`2dc0e66f4c5506d41ea9a6ac31c90e50bbbd81e389a5b028d962a1ccaea51f6f`.
All fourteen scenarios passed in `ec36ff20-7335-4ceb-b86a-8a12e47bc773`.
The frame-commit capture variant also passed all fourteen scenarios in
`b684351c-eb10-4ce7-9993-fa01772c3e04` (APK
`deb0559423af6d6f56dcc90dca5de87350acb60b006100fca39f917a841cafde`).

An apparent omission of static controls in chat previews prompted further
inspection. Pixel comparison of the original PNG control region (x=0..1079,
y=310..729) found it identical to the initial correct screenshot in runs
`ec36ff20-7335-4ceb-b86a-8a12e47bc773`,
`b1dbc161-9d9e-4d99-b34d-41abbef4cb2f`,
`b684351c-eb10-4ce7-9993-fa01772c3e04` and
`a5a8979e-f5da-45f6-a85c-4dd31d74a504`.
No missing-controls defect was established in the app or saved images.

The final session capture explicitly waits for a hardware frame commit, copies
the actual window surface with PixelCopy, checks success and a light background
pixel inside the mode button, then saves that unedited bitmap. Each callback has
a two-second bound. This is a window screenshot (system overlays are outside its
scope), not a reconstructed View image. It adds capture validation, not a product
rendering fix or physical display-latency claim. The focused final run
`a5a8979e-f5da-45f6-a85c-4dd31d74a504` passes all ten groups. Final fixture APK:
`ad3dd75d57df9871dd5630aba61d8d71f280bd04097beb43acf892a5ad8dbbe9`.

All fourteen scenarios have passing evidence on this final APK across the
following collections; this is not one uninterrupted full pass:

| Scenarios | Collection UUID |
| --- | --- |
| Normal, abrupt, partial, keyboard, cursor | `e1dcd8e0-d36e-4e87-8c8c-f461e74b60d4` |
| Profile and fresh-process read | `887bbe9d-bf64-4767-8cb3-d16196b9c0b2` |
| Menu | `1ca035ed-6d09-4462-9edd-21f90cb8dad3` |
| Pointer | `29c197c5-a697-4393-a7e6-0ee5b4aa9351` |
| Mouse | `24bb9d17-a418-43d7-805a-f6d1fe0ff2d0` |
| Actions and fresh-process read | `87940a41-75e7-4e5d-8a02-4d472cc2e34f` |
| Shared session | `1d68c57b-0651-4253-a1e7-385f3e1c9996` |
| Combined D-pad/IME | `ad42daf9-dbeb-43e5-86bb-81d056baf684` |

The shared session run has host/native PIDs 4453/4485, ten check groups, 16 pointer
packets, eight balanced button pairs, 43 frames received/drawn and exit 0. Each
passing scenario ends with no surviving child in its UID census. The full attempt
above stopped at the combined case's seven-second Gboard visibility wait; a
focused attempt (`8a6fd1c0-8a8b-481a-8819-7881c3960c7e`) also timed out before
the final passing rerun. Both failures, screenshots and subsequent system
diagnostics remain private. Their exact cause is unproven; existing deadlines
were not changed. Functional passing evidence does not establish timing stability.

35 lab host tests and nine archive tests pass. The desktop lab builds with
APK SHA-256 `8437c242fdf0b1f5b0f6777235fedcc58a267668b6fa12ad8fb01eed35217622`.
It was not installed or run on Retroid. Both build manifests record exact sources;
the collector checks the installed fixture APK, source hashes and current UUID.

Actual `session.png` was inspected at original resolution and shown in chat.
It shows the owned red/blue frame and the shared controls, with a fixture-only
receipt label. Reports, screenshot and censuses are under ignored
`.local/bridge-tests/evidence/<UUID>/`. No APK or proprietary material was published.

## Remaining work

Selection is now reachable in the official-runtime lab, but no new official
Picotron input acceptance is claimed. The owned Bionic receiver proves transport
receipt, not how Picotron consumes SDL events. Physical controls and real editor
workflows remain [hardware checks](RETROID_INPUT_CHECK.md).

The subsequent [native keyboard bridge](NATIVE_KEYBOARD.md) adds an optional
observer endpoint and shared SDL keyboard/text event construction. The official
Android session can now open that channel via the [shared keyboard opt-in](SESSION_KEYBOARD.md),
with authenticated lifecycle and IME controls. Next, establish and verify
modifier-plus-pointer ordering across the input channels. Host-menu exit,
audio, persistent projects, SAF import and product setup remain under
[beta readiness](BETA_READINESS.md); the hardware candidate gate is not met.
