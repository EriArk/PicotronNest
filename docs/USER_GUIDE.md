# Using PicotronNest

## First launch

Install the APK, then open PicotronNest. Choose **Import your runtime** and select
your legally obtained Picotron **0.3.0d2 Raspberry Pi 64-bit ZIP**. Keep the ZIP
intact; you do not need to unpack it yourself. When validation finishes, choose
**Start Picotron**. Later app launches go straight to Picotron.

Other versions and Windows/macOS/PICO-8 downloads are not accepted by this first
candidate. A rejected import does not replace your working runtime or projects.

## Desktop and games

- Tap the original Picotron screen to point and click. Open the floating menu
  to choose direct touch, mouse or a controller pointer profile.
- Use the keyboard icon to open Android's keyboard. Hide it with Android Back.
- In **Controller: games**, controller buttons go to Picotron games. Start is
  the native pause button. Guide or Android Back opens the host menu.
- **Controller: pointer** uses your configured cursor bindings instead.
- **Touch gamepad: Auto** shows controls when no physical controller is detected.
  Show/Hide overrides that decision. Full controller adds sticks, shoulder
  buttons, stick clicks and triggers. Size and opacity are saved immediately
  when you finish moving their sliders.
- Controller button assignments are under **Settings and projects → Input
  settings → Game controls**. Saved assignments apply at the next launch.

The touch gamepad supplements player one. Press different controls with different
fingers for combinations such as movement plus jump. Each control tracks one
finger; it is not a multi-finger gesture surface. Desktop editing still uses the
original Picotron tools.

## Save, close and return

Save your work **inside Picotron** first. Use **Picotron → Quit Picotron** from the
floating controls/host menu, then confirm. The app waits for the session to close.
Leaving for another Android app mutes sound while Picotron is in the background.
Android can still stop background apps; unsaved work is not a backup.

If Android interrupted a session, setup offers **Recover interrupted launch**.
This checks that the old process is gone and preserves saved files. It cannot
recover changes that Picotron never saved.

## Projects and backups

Close Picotron, then open **Settings and projects → Projects and backups**:

- Import a `.p64` or `.p64.png` cartridge through Android's document picker.
- Export a saved cartridge to a folder you choose.
- Back up the whole Picotron home to ZIP.
- Restore a backup into a **new, separate Restored folder** on Picotron Drive.
  Existing files are not overwritten; copy the projects you need in Picotron.

Updating the APK with the same app identity/signature preserves its private home.
Uninstalling or clearing app data does not. Export a backup before doing either.

The product beta uses `art.picotronnest`; early development builds used
`art.picotronnest.executionlab`. They have separate storage. To move from a lab
build, export its home backup, install the beta, import your runtime again, then
restore that backup and copy your projects from Restored. Keep the old app until
you have checked the copied projects. No automatic or destructive migration runs.

## Help and privacy

**About and diagnostics** shows the app version, bundled notices and an **Export
diagnostic report** button. Reports contain the device model, Android/app versions
and session counters. They omit runtime output, typed text, file paths and project
contents. Exporting saves a local document; it does not submit anything online.

The wrapper has no analytics, account or network permission in this candidate.
Picotron's online features therefore are unavailable. Transfer local cartridges
through the document picker. App private storage contains your runtime, projects
and local session logs; use backups rather than copying internal runtime folders.

For a bug report, include the app version, device/Android version, reproduction
steps and an exported diagnostic file. Review screenshots before attaching them.
Do not attach your purchased runtime ZIP, private projects or signing files.
