# End-to-end acceptance

This checklist defines **proof needed**, not features currently present. Start every item as NOT TESTED. Status values: PASS (with evidence), FAIL (reproducible), BLOCKED (dependency not available), NOT TESTED. Code review, mockups or compile success alone never satisfy physical-device acceptance.

## A0 — Environment / official runtime

- [ ] A0.1 Imported archive is the user's legally obtained current compatible Picotron build; version and ELF ABI are recorded.
- [ ] A0.2 No proprietary Picotron runtime/data in repository, support bundle, APK or public artifacts.
- [ ] A0.3 Package import validates contents and rejects traversal/duplicates/oversize, and a rejected import leaves previous setup untouched.
- [ ] A0.4 Minimal official runtime starts in a contained Android environment without root or Termux.
- [ ] A0.5 Exact dependencies and process identity/lifecycle are observed, including actionable failure diagnostics.

## A1 — Actual desktop output

- [ ] A1.1 Real Picotron desktop is visible on physical Android hardware.
- [ ] A1.2 Frame output is correct (colors, aspect, crop, orientation, no tearing/stale overlays).
- [ ] A1.3 Normal 480x270 and another supported mode are tested; changing mode or resizing does not corrupt frame mapping.
- [ ] A1.4 Pixel-perfect/integer and fit scaling, where implemented, do not stretch the original ratio.
- [ ] A1.5 The screen remains responsive after repeated navigation, animation, app switching and returning.

## A2 — Real usable input and audio

- [ ] A2.1 Touch taps, cursor movement, drag, secondary click and pointer boundaries land at expected Picotron coordinates.
- [ ] A2.2 Hardware mouse and keyboard work, including modifiers, key release and text entry.
- [ ] A2.3 Android soft keyboard/IME permits entering and editing Lua, symbols and filenames; limitations explicitly documented.
- [ ] A2.4 Handheld gamepad can navigate, position the pointer, select/edit objects, summon keyboard and exit; a controller-only path is testable.
- [ ] A2.5 Switching focus, dialogs, Home/return and sleep never leave stuck inputs.
- [ ] A2.6 Music/SFX are audibly heard on hardware; audio remains functional after background/foreground and short sleep/wake.

## A3 — Picotron workflows and persistence

- [ ] A3.1 Open the **real** Picotron code editor; create a small ordinary cartridge and run it.
- [ ] A3.2 Use the real graphics editor; save a native `.p64` project, exit Picotron and re-open it unchanged.
- [ ] A3.3 `.p64.png` opening is checked in the official runtime without rewriting source.
- [ ] A3.4 Picotron settings, desktop and `/appdata` persist through regular app upgrades and process recreation.
- [ ] A3.5 Storage permission loss/import cancellation/disk full do not destroy existing files or silently activate incomplete homes.
- [ ] A3.6 An explicit Android document export/import path is verified if supplied in the current milestone.

## A4 — Lifecycle and clean device

- [ ] A4.1 Fresh Android installation with **only PicotronNest APK plus the user's runtime archive** reaches Picotron (no preinstalled PikoNest/wrapper/helper/Termux).
- [ ] A4.2 One app icon; multiple internal processes are allowed but no second user-installed APK.
- [ ] A4.3 Cold start, repeated launch, graceful exit and return to host all work without duplicate instances.
- [ ] A4.4 Home/background/resume, rotation/insets and screen sleep/wake are exercised.
- [ ] A4.5 Unknown/crashed/interrupted sessions are recovered deliberately without deleting unverified data.
- [ ] A4.6 Modern-target Android executable-loading approach is implemented and verified; legacy target SDK cannot be the final hidden workaround.
- [ ] A4.7 At least two meaningfully different Android device/screen/input combinations are tested before claiming broad compatibility.

## A5 — Distribution and quality

- [ ] A5.1 Reproducible build with pinned upstream dependencies and verified dependency licenses/notice files.
- [ ] A5.2 Security review of archive import, command arguments, SAF, process boundaries and redacted logs.
- [ ] A5.3 Release README describes actual supported devices, requirements and known limitations, rather than aspirational claims.
- [ ] A5.4 Owner has inspected an actual build and **explicitly authorized** an APK GitHub Release.

## Evidence template

```text
Check: A?.?
Outcome: PASS / FAIL / BLOCKED / NOT TESTED
Date:
Device / Android version / ABI:
Picotron version + archive SHA-256:
Host commit + APK SHA-256:
Procedure:
Observed result (automated vs user-verified):
Regression risk / remaining unknowns:
Evidence locations (non-proprietary):
```

See [STATUS](STATUS.md) for current completion, [ROADMAP](ROADMAP.md) for delivery order, and [RUNTIME_SPIKE](RUNTIME_SPIKE.md) for the initial experimental checks.
