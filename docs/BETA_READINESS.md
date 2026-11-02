# Beta readiness and the Retroid handoff

The earlier Retroid reservation was lifted by the owner on 2026-11-02. Scoped
hardware checks are now recorded in [Managed workstation](MANAGED_WORKSTATION.md).
The owner separately authorized public beta 2 release publication on 2026-11-02.

Owner direction, 2026-11-02: prioritize complete functionality in coherent
increments. Use builds and short checks of critical changed behavior during
development; leave deep regressions and physical acceptance to the owner and
testers. Hardware uncertainty is recorded, not a reason to stall independent
implementation work or repeatedly request the handheld.

## Gate 1: candidate ready for hardware testing

A **product candidate with native Splore networking** is now prepared: 0.1.0-beta.2,
`art.picotronnest`. It is ready for scoped tester evaluation, not a validated
general Android release. Packaging, notices, diagnostics and instructions are
integrated. Native Splore catalog/download/play and HTTPS have scoped Retroid
evidence; account operations, network changes, audio listening and broader hardware
acceptance remain open. Original code uses MIT; beta 2 is published as an
owner-authorized [prerelease](https://github.com/EriArk/PicotronNest/releases/tag/v0.1.0-beta.2).
See [candidate evidence](RELEASE_CANDIDATE.md).

| Required capability | Current evidence / work remaining |
| --- | --- |
| One Android APK and icon, modern target, no root or helper installation | Non-debuggable, signed ARM64 product APK, original icon, target 36, one exported launcher and private internal components; actual import/launch on Retroid |
| User supplies their official compatible runtime through Android SAF | Actual owner 0.3.0d2 Raspberry Pi ZIP imported on Retroid through the picker; pinned staging/activation. Additional runtime contracts remain open |
| Original workstation video with correct fit and input coordinates | Official desktop and pointer interaction on Retroid. Immersive viewport, texture-sized SDL window and per-presentation managed capture remove redundant padding and the diagnostic throttle. Broader orientation/resize acceptance remains |
| Touch, mouse/keyboard, phone IME and configurable handheld controls | Shared input and editable cursor/menu/game bindings. Native SDL game controllers and configurable touch gamepad reach official Picotron `btn()` on Retroid. Compact/full touch layouts, ownership/cancel checks and settings are implemented; phone ergonomics, physical selection/reconnect, analog/multiple-player and broader IME acceptance remain |
| Reachable keyboard, host menu, resume and deliberate exit | Overlay controls and Android Back open the menu; official quit has process and delivery receipts; Home/resume retains native PID |
| Audible sound and interruption recovery | Official callback PCM reaches Android AudioTrack; no listening confirmation. Focus/duck/background behavior is implemented; physical audio acceptance remains |
| Persistent native home and projects | Actual `.p64` save/reopen, SAF import/export, ZIP backup and separate-folder restore on Retroid; original/exported/restored hashes match. `.p64.png` hardware workflow and broad upgrade acceptance remain |
| Reliable lifecycle and recovery | Lease, unfinished marker, process guard and retained environment recovery implemented. Force-stop/recovery tested without replacing home. Long sessions and broader sleep/interruption cases remain with testers |
| Native Splore / online dependencies | HTTP(S), Android DNS and system trust roots integrated; catalog, downloads and two games checked on Retroid. Account operations, VPN/network changes and raw socket acceptance remain; use windowed Splore |
| Reviewable candidate and instructions | Versioned packaging, source/dependency archives, notices, checksum manifest, sanitized diagnostic export and tester/user guides published with beta 2; MIT included |

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
