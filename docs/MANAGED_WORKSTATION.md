# Managed workstation tools and Retroid workflow

2026-11-02. This is an integrated development build, not a published beta.
Subsequent native controller and SDL presentation work supersedes the relevant
remaining-work statements below: [Gamepad and display](GAMEPAD_AND_DISPLAY.md).
The owner released Retroid for this check. No other app was installed, cleared
or stopped. The existing PicotronNest private files were backed up locally
before updating the lab APK with `adb install -r`.

## User experience

After the supported runtime has been imported, the launcher icon opens official
Picotron directly. The Android viewport belongs to the workstation; two small
translucent icons open controls or the keyboard. Additional pointer/editor
controls live in a dismissible overlay drawer. Android Back opens the host menu;
quit requires a deliberate confirmation and observed process completion. Closing
the drawer or a menu restores desktop focus. There is no permanent toolbar or
live-session diagnostic label in managed mode.

This does not yet eliminate the native SDL dummy-window padding. The observed
SDL frame is 1024x768 while the official desktop texture is 480x270. Improve
window sizing and presentation together with input transforms; do not crop by
assuming every Picotron mode has one fixed resolution.

Input settings provide off, D-pad buttons, analog stick and D-pad axes profiles,
axis selection, speed/dead-zone/precision values and captured pointer/menu
bindings. Settings persist across launches and reject conflicting bindings.
Select the saved controller profile in Pointer mode. This controls the pointer;
native cartridge joystick/gamepad routing remains a separate unfinished feature.

Projects and backups use Android document pickers, without broad storage access:

- Import `.p64` or `.p64.png` into the observed native `home/drive/desktop`.
  Same-name imports preserve the original and receive a numbered suffix.
- Export a selected cartridge or back up the complete private Picotron home.
- Restore a backup into a new `drive/Restored-...` folder. Current projects and
  active settings are not overwritten; users can copy their desired files.
- Recover an interrupted launch only under the store lease and after checking
  for other processes owned by the app. Retain the pending journal and cached
  environment, rebuild the environment on next launch, and preserve the home.

Transfers require an idle home. Staging and atomic activation, path/link checks,
bounded ZIP validation, cancellation and size limits guard imports. Limits are
128 MiB expanded/exported content and 4096 collected entries; source backup ZIPs
also have the runtime archive bound. Failed stages and recovered environments
are retained; automatic cleanup is not implemented. A provider-side partial
export can remain after a write failure. No runtime objects are exported by the
home-backup action.

## Actual device evidence

Retroid Pocket Classic, Android 14/API 34, ARM64; development APK targets API 36.
Official Raspberry Pi package: Picotron 0.3.0d2, archive SHA-256
`ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75`.
All archives, runtime trees, screenshots and device receipts remain ignored under
`.local/evidence/retroid-managed-20261102/` or in app-private device storage.

The initial baseline APK (`443464ad...`) passed actual SAF import, official menu
and terminal interaction, `print(6*7)` returning 42, and saving an owned `.p64`
with the native `save /desktop/pn_check_20261102.p64` command. Home/launcher return
retained native PID 16344. Normal quit in session
`b67118b3-0a84-4f05-8565-85a015f12f3c` recorded COMPLETE, exit 0,
`managed_session_closed=true`, ordered accepted/sent/ack 307 and no native child.

The intermediate tools APK (`b6af9580...`) loaded and ran that same saved cart
after relaunch. Actual Android pickers passed home backup, separate-folder
restore, cartridge export and same-name reimport. Original, restored and exported
cart SHA-256 matched:
`1c2a44829c46c5f1e1bfa7e8504e812459e46c1d7719d38e6603c6afaa6d3785`.
The earlier duplicate/restore names used UUIDs; final code uses readable names,
covered by the focused host check. Existing device copies were retained.

Force-stop of that owned test session retained an incomplete report; recovery
allowed relaunch without replacing projects. Session
`1139bf04-dc06-4e9a-aee1-9758d53ee776` subsequently completed normally.
The settings screen captured injected BUTTON_X as a scroll binding and saved a
D-pad profile. The device reports its built-in input as Xbox Wireless Controller;
no model-specific mapping is used.

The final APK below was installed and launched using the MAIN/LAUNCHER intent.
Visual review verified the immersive overlay. Injected gamepad D-pad movement
worked immediately after menu closure, fixing the focus regression found during
review. A second scoped force-stop/recovery retained environment
`b5417859-69eb-497a-9265-89c93fc8cd1b` and reopened the official desktop.
The recovered session `f4416725-2e11-443d-8975-4f412534e87b` then quit normally:
native PID 18483, exit 0, COMPLETE, `managed_session_closed=true`, ordered quit
acknowledged. The saved cart hash still matched and no native child remained.
Injected events establish dispatch behavior, not physical button acceptance.

The initial session received 62,651,360 PCM bytes and wrote 62,464,208 bytes to
Android AudioTrack without a reported audio error. AudioFlinger identified the
host track. The device media volume was initially zero; a short tone check
temporarily raised it and restored it to zero. No human listening response was
received: audible sound is still unconfirmed.

## Build and focused checks

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p test_project_files.py
py -3 -B -m unittest discover -s experiments/execution-model -p test_runtime_store.py
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
py -3 -B experiments/execution-model/build_bridge_tests.py
```

Both focused checks pass. They cover project byte preservation, same-name
imports, backup/restore, cancellation, unsafe/truncated archives, and recovery
refusal while a lease/process is active. Both APKs build; recorded source hashes
match the final source. No full emulator suite was rerun in this increment.

| Artifact (private, not published) | SHA-256 |
| --- | --- |
| Execution lab APK | `f68128ed66ad4cda50d001d669e0388b0757fe74f3d74009e920aa2a18ad9c91` |
| Bridge-test APK | `53dbb1e06169c2bfb3c8ef810cd22547ff8d7956ea3ea483b6d1df4f269a5536` |

To reproduce the user path: install the development APK without clearing data,
open its icon, import the supported owned ZIP, start Picotron, save a small owned
cart, quit through the floating menu, and reopen it. Open Settings and projects
for the transfer/recovery workflows. Use only a saved owned test cart for an
interruption check. Retain reports and compare exported/restored bytes.

## Remaining functional work

Native cartridge controller routing is still missing; the current minimal SDL
reports joystick support disabled. SDL display sizing needs to remove redundant
padding. Final product packaging, diagnostics and matching tester instructions
remain. Physical controllers, audible sound, broad phone/IME/orientation usage,
long sessions and enforcing ARM64 Android compatibility are not established by
this scoped Retroid run. See [beta readiness](BETA_READINESS.md).
