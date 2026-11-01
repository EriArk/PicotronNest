# Beta readiness and the Retroid handoff

Owner direction, 2026-10-24: continue without occupying Retroid, and notify the
owner when the required functionality is assembled for a beta hardware check.
The owner will then release the handheld from another task. Do not install,
launch or interact on Retroid before that handoff. Source commits/pushes remain
authorized; publishing an APK or GitHub Release still requires explicit approval.

## Gate 1: candidate ready for hardware testing

This gate is **not reached**. The repository still contains development probes,
not an end-user beta. Emulator fixtures prepare individual contracts; they do
not substitute for integrating those contracts with official Picotron.

| Required capability | Current evidence / work remaining |
| --- | --- |
| One Android APK and icon, modern target, no root or helper installation | Target-36 execution lab and audited runtime components exist; product packaging/setup is open |
| User supplies their official compatible runtime through Android SAF | Bounded archive inspector and private developer provisioning exist; end-user selection, staging, activation, rejection/rollback and upgrade flow are open |
| Original workstation video with correct fit and input coordinates | Real frames have Retroid lab evidence; supported orientations/resizes and integrated controls still need implementation/acceptance |
| Touch, mouse/keyboard, phone IME and configurable handheld controls | Limited host adapters, [secondary/wheel transport](POINTER_WHEEL.md) and [Android mouse routing](ANDROID_MOUSE.md) have fixture evidence; official integration, configurable secondary/wheel touch/controller actions, physical device selection/reconnection and cartridge-control routing are open |
| Reachable keyboard, host menu, resume and deliberate exit | [Owned host-menu experiment](HOST_MENU.md); official-runtime shutdown and product integration are open |
| Audible sound and interruption recovery | Android audio path and validation are open |
| Persistent native home and projects | Persistent `-home`, save/reopen, `.p64`/`.p64.png` workflows, SAF project import/export and upgrade preservation are open |
| Reliable lifecycle and recovery | Owned process/lifecycle evidence exists; integrated cold start, normal exit/relaunch, sleep/Home, interrupted/unknown-session recovery and no surviving children are open |
| Reviewable candidate and instructions | Reproducible private APK, dependency/license review, install/import steps, supported device scope, diagnostics and known limitations must match the actual candidate |

Before asking the owner to reserve Retroid, assemble these capabilities in one
candidate, run applicable host/emulator checks, and prepare a bounded hardware
script with the candidate hash and recovery steps. Report remaining hardware
uncertainties explicitly. If a runtime uncertainty blocks further integration,
report that dependency instead of filling the gap with a replacement workstation
or describing a fixture as a completed product feature.

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
