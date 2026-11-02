# First beta tester checklist

Candidate: **0.1.0-beta.1**, ARM64, package `art.picotronnest`, target API 36.
Supported import: your own **Picotron 0.3.0d2 Raspberry Pi 64-bit ZIP**.
Retroid Pocket Classic / Android 14 has scoped development evidence; this is
not a promise of compatibility with other Android execution policies.

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

## Report results

Use **Settings and projects → About and diagnostics → Export diagnostic report**.
Include device model, Android version, the app version and a short sequence of
actions. Say which inputs were physically used and whether sound was heard.
An installation or screenshot alone does not establish a playable session.

Please do not upload purchased runtime archives or personal carts. Screenshots
are optional; check them for private content before posting.

## Known limits

- Only the pinned 0.3.0d2 Raspberry Pi 64-bit runtime is accepted.
- Online Picotron features are unavailable in this candidate (no network permission).
- Other ARM64 Android devices, especially stricter execution policies, need testing.
- Audio listening, physical analog ranges, Bluetooth reconnect, multiple players,
  long sessions and phone ergonomics have not received broad acceptance.
- Restoring a backup creates a separate folder; it does not overwrite your home.
- Uninstalling removes private data. Back up first.

For deeper checks, see [acceptance](ACCEPTANCE.md). Publishing a public APK is a
separate owner decision; these instructions do not imply a public release exists.
