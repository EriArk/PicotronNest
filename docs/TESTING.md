# Beta tester checklist

Candidate: **0.1.0-beta.2**, ARM64, package `art.picotronnest`, target API 36.
Supported import: your own **Picotron 0.3.0d2 Raspberry Pi 64-bit ZIP**.
Retroid Pocket Classic / Android 14 has scoped development evidence; this is
not a promise of compatibility with other Android execution policies.

## Beta 3 development candidate

Beta 3 (version code 1003) is a local candidate, not the published download.
Use the session checks below and also check:

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
   sound after leaving and returning to the app. This listening check is still open.
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

For deeper checks, see [acceptance](ACCEPTANCE.md). Publishing a public APK is a
separate owner decision; these instructions do not imply a public release exists.
