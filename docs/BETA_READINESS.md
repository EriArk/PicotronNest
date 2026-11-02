# Beta readiness and the Retroid handoff

The earlier Retroid reservation was lifted by the owner on 2026-11-02. Scoped
hardware checks are now recorded in [Managed workstation](MANAGED_WORKSTATION.md).
This authorizes device testing, not APK or GitHub Release publication.

Owner direction, 2026-11-02: prioritize complete functionality in coherent
increments. Use builds and short checks of critical changed behavior during
development; leave deep regressions and physical acceptance to the owner and
testers. Hardware uncertainty is recorded, not a reason to stall independent
implementation work or repeatedly request the handheld.

## Gate 1: candidate ready for hardware testing

This gate is **not reached**. The repository still contains development probes,
not an end-user beta. Emulator fixtures prepare individual contracts; they do
not substitute for integrating those contracts with official Picotron.

| Required capability | Current evidence / work remaining |
| --- | --- |
| One Android APK and icon, modern target, no root or helper installation | Target-36 development APK; direct icon launch after import and recovery UI implemented. Final product packaging remains open |
| User supplies their official compatible runtime through Android SAF | Actual owner 0.3.0d2 Raspberry Pi ZIP imported on Retroid through the picker; pinned staging/activation. Additional runtime contracts remain open |
| Original workstation video with correct fit and input coordinates | Official desktop and pointer interaction on Retroid. Immersive viewport, texture-sized SDL window and per-presentation managed capture remove redundant padding and the diagnostic throttle. Broader orientation/resize acceptance remains |
| Touch, mouse/keyboard, phone IME and configurable handheld controls | Shared input and editable cursor/menu/game bindings. Native SDL game controllers and configurable touch gamepad reach official Picotron `btn()` on Retroid. Compact/full touch layouts, ownership/cancel checks and settings are implemented; phone ergonomics, physical selection/reconnect, analog/multiple-player and broader IME acceptance remain |
| Reachable keyboard, host menu, resume and deliberate exit | Overlay controls and Android Back open the menu; official quit has process and delivery receipts; Home/resume retains native PID |
| Audible sound and interruption recovery | Official callback PCM reaches Android AudioTrack; no listening confirmation. Focus/duck/background behavior is implemented; physical audio acceptance remains |
| Persistent native home and projects | Actual `.p64` save/reopen, SAF import/export, ZIP backup and separate-folder restore on Retroid; original/exported/restored hashes match. `.p64.png` hardware workflow and broad upgrade acceptance remain |
| Reliable lifecycle and recovery | Lease, unfinished marker, process guard and retained environment recovery implemented. Force-stop/recovery tested without replacing home. Long sessions and broader sleep/interruption cases remain with testers |
| Reviewable candidate and instructions | Final packaging, dependency/license review, supported device scope, diagnostics and tester instructions must match the actual candidate |

Continue completing these capabilities in one candidate with focused checks.
Report hardware uncertainty explicitly; neither fixtures nor successful builds
establish physical acceptance.

## Gate 2: validated beta

On the owner's freed device, follow [ACCEPTANCE](ACCEPTANCE.md) and begin with
[the official-runtime input check](RETROID_INPUT_CHECK.md). Record device/Android,
runtime and APK versions/hashes, actual native PID/exit evidence and screenshots.
Exercise a complete user workflow: fresh import, real desktop/editor navigation,
Unicode/code entry, graphics editing, sound, save, normal exit, relaunch and
reopen the same native project. Repeat interruption and update/recovery cases
without deleting projects. Check actual controller and touch behavior, not only
injected events or callback completion.

Call the result beta-ready only after its supported workflows pass on the stated
hardware and limitations are documented. Retroid evidence alone cannot establish
phone/tablet usability or broad compatibility; meaningful additional devices are
needed for those claims. Hardware validation is separate from the owner's
authorization to publish a beta APK.
