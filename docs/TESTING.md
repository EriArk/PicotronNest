# Beta tester checklist

Target of this checklist: **0.1.0-beta.4**, ARM64, package `art.picotronnest`, target API 36.
Import: your own compatible **Linux / Raspberry Pi 64-bit ARM64 ZIP**. Device
checks used Picotron **0.3.0d2**; other versions need individual validation.
Retroid Pocket Classic / Android 14 has scoped development evidence; this is
not a promise of compatibility with other Android execution policies.

## Beta 4: transfers, controls and recovery

Beta 4 has version code 1004 and the same product signing identity. Save and quit
Picotron fully before installing over beta 3. Do not uninstall or clear data.
A fresh beta-4 hardware upgrade/session/recovery cycle is still unverified.

- Open settings from the running session and return. Rotate input settings with
  unsaved edits; confirm they remain drafts until Save is pressed.
- Queue a cartridge while running, cancel another selection, and return. Save and
  quit, then finish imports explicitly. Confirm the original projects remain.
- Use mixed-case extensions, spaces and Unicode; PICO-8 files should explain the
  format mismatch. After interrupted application, review before retrying.
- Import/export a small nested collection. Verify counts, separate destinations,
  cancellation and retained completed files. Loose assets are intentionally skipped.
- Save a screenshot, cancel the next picker and capture again. Check PNG pixels,
  orientation and that overlays are absent.
- Save work before Restart Picotron. Confirm one runtime after a normal restart;
  do not test hung-session failure with valuable unsaved edits.
- Open Help/community, return and confirm the original session is usable.
- If Resume safely appears after an interruption/update, export diagnostics and
  use recovery. Record whether the saved project reopens and normal quit completes.
  A disappearing native PID alone does not prove completed host teardown.

## Setup and upgrade regression checks

Beta 3 has version code 1003. Use the session checks below and also check:

- Fresh install: welcome, ZIP selection and ready steps; no automatic launch
  before pressing Start. Rotate or leave/return during each step.
- Cancel the picker/import, choose an unsuitable ZIP and retry with a valid one.
  Rejections should explain the reason and keep existing projects/selection.
- Update an existing beta 2 installation without clearing data. Confirm it skips
  onboarding, opens the existing home and preserves saved projects.
- Import an older/newer legally obtained ARM64 build, note the exact Picotron
  version separately, and check launch/save/reopen. Static admission is not
  runtime compatibility proof. Back up first; test with disposable projects.
- Close Picotron, change the runtime, use previous runtime and reopen the saved
  project. Confirm the selected version actually runs and cached older executable
  files are not reused. Check interrupted-session recovery as well.

See [onboarding evidence and limits](ONBOARDING.md).

## A short first session

1. Install the supplied APK. Confirm there is one PicotronNest product icon and
   no root, Termux or helper setup is requested.
2. Import the official supported ZIP. Confirm the actual desktop opens.
3. Open the native code editor, enter a small program, save a new `.p64`, run it,
   quit normally, relaunch and reopen that file. Keep the test project separate
   from important work.
4. Check touch, Android keyboard, gamepad buttons/sticks or mouse/keyboard as
   available. Try a movement-plus-button chord, pause/resume and the host menu.
5. Show the touch gamepad, change its size/opacity, switch compact/full layouts,
   then hide it. Check both screen orientations and keyboard show/hide.
6. Play a known sound in Picotron. Confirm audible output, volume control and
   sound after leaving and returning to the app. The owner reported good Phoenix
   playback on Retroid; interruption listening and other devices remain open.
7. Export a cartridge and a home backup. Restore into the separate Restored
   folder and open the copied project. Confirm the original still exists.
8. Save, leave the app, return, then quit normally. Report frozen frames,
   duplicated instances, stuck inputs, crashes or unexpected data loss.

## Splore and networking

1. Open native Splore, browse New/Featured, download a cartridge and play it.
2. Search for a term with the Android keyboard, press Enter and open a result.
3. Return to Splore from the game's native pause menu. Try another cartridge.
   Toggle Splore's Fullscreen menu item, double-tap its title bar to maximize
   and restore the list, then return to windowed mode. Check category selection
   with the Android keyboard both shown and hidden.
4. Test a failed request and retry after reconnection. Keep local projects intact.
5. If applicable, check your VPN/Private DNS and Wi-Fi/mobile transitions. Account
   login and uploads are optional owner checks; never put credentials in reports.

## Beta 3: library and sound

- Open **Your games** beside the hamburger. Check both imported and Splore-downloaded
  cartridges, scroll the cover grid and launch one of each. Confirm actual gameplay,
  not just a successful launch notification. Close the popup and check input resumes.
- In **Picotron controls > Sound buffer**, compare Auto, Low latency and More stable.
  Note crackling, delay, game/load conditions, speaker/headphone/Bluetooth route and
  device model. Leave and return to the app; verify the preference survives relaunch.
- Export diagnostics after a normal session exit. Underrun counters help investigation
  but do not replace listening. No audible acceptance is claimed by automated tests.

## Report results

Use **Settings and projects → About and diagnostics → Export diagnostic report**.
Include device model, Android version, the app version and a short sequence of
actions. Say which inputs were physically used and whether sound was heard.
An installation or screenshot alone does not establish a playable session.

Please do not upload purchased runtime archives or personal carts. Screenshots
are optional; check them for private content before posting.

## Published beta 2 limits

- Only the pinned 0.3.0d2 Raspberry Pi 64-bit runtime is accepted.
- Native Splore browsing/download/play and HTTPS have scoped Retroid evidence.
  Account operations, VPN/Private DNS, network changes and raw TCP/UDP need acceptance.
- Splore Fullscreen preserves the list size; double-tap the title bar to maximize.
  The earlier incorrect-layout warning was withdrawn after the [device check](SPLORE_FULLSCREEN.md).
- Other ARM64 Android devices, especially stricter execution policies, need testing.
- Audio listening, physical analog ranges, Bluetooth reconnect, multiple players,
  long sessions and phone ergonomics have not received broad acceptance.
- Restoring a backup creates a separate folder; it does not overwrite your home.
- Uninstalling removes private data. Back up first.

For deeper checks, see [acceptance](ACCEPTANCE.md). Beta 3 is published with owner
authorization; unresolved checks above remain tester work.
