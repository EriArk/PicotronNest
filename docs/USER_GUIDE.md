# Using PicotronNest

## Beta 4: in-session controls

Beta 4 adds a first-use hint beside the floating controls.
Tap **Picotron controls** (☰), or open the host menu with Guide in game mode / Android
Back and choose **Picotron controls**. Pointer mode uses your configured menu key.
D-pad up/down moves between drawer options; your configured confirm/cancel buttons
select/close, and left/right adjusts sliders. There is no edge-swipe shortcut.

Open **Your games** or **Settings and projects** from this same drawer without
quitting. Pointer/touch-gamepad and sound changes apply live; saved input bindings
apply next launch. Queue/review imports while running, then close Picotron before
applying them, transferring folders, exporting/backing up or replacing the runtime.
See [validation and limits](IN_SESSION_CONTROLS.md).

## First launch

These instructions describe **beta 4**. The older beta 2 has an earlier
import screen and accepts only the Picotron 0.3.0d2 Raspberry Pi 64-bit ZIP.

1. Open PicotronNest and choose **Get started**. PicotronNest runs the real
   Picotron desktop, tools and games; your legally obtained Picotron is not included.
2. Download your **Linux / Raspberry Pi 64-bit (ARM64) ZIP** from your Picotron
   purchase. Choose **Import your runtime** and select the ZIP as downloaded.
   You do not need root, Termux, a helper app or manual extraction.
3. Read the short controls/save reminder, then choose **Start Picotron**.
   Importing alone does not start the desktop. Later app launches go straight
   to your installed Picotron, unless an interrupted session needs recovery.

Since beta 3, imports check architecture, package structure and required libraries instead of
allowlisting a Picotron version. Older/newer ARM64 builds can be imported if they
pass those checks. Import success does not guarantee that an untested version
will run correctly. Windows, macOS, 32-bit and PICO-8 downloads are unsuitable.

A rejected or cancelled import keeps your existing selection and saved projects.
Close Picotron before replacing it through **Settings and projects > Import your
runtime**. Back up your home first: Picotron itself may change files between
versions. **Use previous runtime** switches back to the prior imported build;
it does not undo changes a runtime made to project files. Your home is shared
across runtime versions and preserved across app updates.

## Desktop and games

- The four-square **Your games** button beside the hamburger opens
  covers for downloaded and local cartridges. Tap one to launch it. Download
  new games in native Splore; import local carts under Settings and projects.
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
  Input drafts survive screen recreation; press **Save input settings** to persist them.

The touch gamepad supplements player one. Press different controls with different
fingers for combinations such as movement plus jump. Each control tracks one
finger; it is not a multi-finger gesture surface. Desktop editing still uses the
original Picotron tools.

**Sound buffer** in the floating menu or management offers Auto, Low latency
(40 ms) and More stable (160 ms). Changes apply immediately. Auto starts at
80 ms and grows when Android reports underruns, up to 240 ms. Larger buffers
can reduce interruptions at the cost of more delay; hardware/Bluetooth latency
is additional. See [audio and library checks](AUDIO_AND_LIBRARY.md).

## Find games online

Open **Splore** from Picotron's own menu in the upper-left corner. Choose **New**
or **Featured** to browse. Double-tap a cartridge to download and open it. If an
old empty list is cached, activate its **Refresh** entry. The catalog, downloads
and game launcher are all provided by your official Picotron copy.

Use Search to enter a term, then press Enter. You can move or resize the native
Splore window by its title bar and lower-right corner. **Fullscreen** in Splore's
own menu (Ctrl+1) switches to a dedicated workspace with a dark background and
selected-cartridge artwork. It deliberately keeps the list's size and position.
**Double-tap the list's title bar to maximize it**; double-tap again to restore
its previous size. Toggle Fullscreen off to return to the desktop window.
Both modes were checked in the published beta 2 on Retroid; see
[the fullscreen check](SPLORE_FULLSCREEN.md).

If a request fails, check your Android connection and device date, then retry.
Local projects remain available without internet. PicotronNest uses Android's
DNS resolver and system certificate roots; it does not disable HTTPS verification.
Native account login and uploads have not been tested with an owner's account.

## Save, close and return

Save your work **inside Picotron** first. Use **Picotron → Quit Picotron** from the
floating controls/host menu, then confirm. The app waits for the session to close.
Leaving for another Android app mutes sound while Picotron is in the background.
Android can still stop background apps; unsaved work is not a backup.

**Restart Picotron** is also available in the host menu. Save first: this closes
and relaunches the workstation; it is not a save-state operation. Restart waits
for confirmed teardown and does not relaunch after a failed shutdown.

**Save screenshot** in Picotron controls/the host menu opens Android's save picker.
The PNG contains the original frame without host overlays. Cancel to save nothing;
files are never uploaded automatically.

If Android interrupted a session, setup offers **Recover interrupted launch**.
This checks that the old process is gone and preserves saved files. It cannot
recover changes that Picotron never saved.

## Projects and backups

You can open **Settings and projects → Projects and backups** while Picotron runs.
**Import cartridge (queue)** in the floating drawer opens the same document picker.
Choose `.p64` or `.p64.png`; the checked copy waits outside the live home. Save and
quit Picotron, then tap **Finish queued imports**. Reopen Your games after launching
Picotron again. **Review queued imports** lets you keep/discard waiting copies or
explicitly retry an interrupted application. Check your projects first: a copy may
already have been imported. Discard affects only the queued copy. PICO-8 `.p8`
files are unsupported; names may contain spaces, Unicode and mixed-case extensions.

Other transfers require a closed Picotron session:

- Import/export a cartridge folder through the Android folder picker. Only
  cartridges transfer; loose assets are skipped. Nested names remain intact in
  a new, uniquely named folder, with no overwrites. Limits are 4096 entries,
  32 directory levels and 128 MiB. Import cancellation activates nothing;
  export cancellation may retain completed files. Read the result and any
  incomplete-export warning before retrying.
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

**Help and community** links to the AbyssTail website, its maintained Discord
community entry and GitHub reporting. Opening an external app does not request
Picotron shutdown; use Android Back to return.

**About and diagnostics** shows the app version, bundled notices and an **Export
diagnostic report** button. Reports contain the device model, Android/app versions
and session counters. They omit runtime output, typed text, file paths and project
contents. Exporting saves a local document; it does not submit anything online.

The wrapper has no analytics or separate account. It requests internet access so
Picotron can contact online services directly. Splore requests go to Picotron's
services; cartridge network requests go to their requested destinations. Native
Picotron account features remain under your control. The DNS adapter does not log
names, URLs or response bodies. App private storage contains your runtime, projects
and local session logs; use backups rather than copying internal runtime folders.

For a bug report, include the app version, device/Android version, reproduction
steps and an exported diagnostic file. Review screenshots before attaching them.
Do not attach your purchased runtime ZIP, private projects or signing files.
