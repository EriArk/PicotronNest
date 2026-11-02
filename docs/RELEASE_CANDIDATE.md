# Product candidate evidence

Current published prerelease: [**0.1.0-beta.3**](https://github.com/EriArk/PicotronNest/releases/tag/v0.1.0-beta.3), code 1003.
See [onboarding and runtime import](ONBOARDING.md) and
[audio/library checks](AUDIO_AND_LIBRARY.md) for its scoped evidence.
The publication and device results below describe earlier APKs.

Published prerelease: [**0.1.0-beta.2**](https://github.com/EriArk/PicotronNest/releases/tag/v0.1.0-beta.2)
from commit `a22ca4b726ea608a869a007d34146af6fc2993a1` adds the owner-selected MIT license, native
Splore networking, Android DNS/system trust and a screenshot bundle. See
[network evidence](NETWORK.md) and [beta 2 notes](releases/0.1.0-beta.2.md).
The following sections retain the original beta 1 packaging evidence.
Their publication, license and networking limitations are historical; beta 2
uses MIT, adds networking and has owner authorization for a public prerelease.
Final beta 2 build and focused validation hashes are in [network evidence](NETWORK.md).


2026-11-02. **0.1.0-beta.1 is prepared locally, not published.** It is a candidate
for testing local Picotron projects, not a fully accepted cross-device release.

## Packaging and repository

- Package `art.picotronnest`, version code 1001, native ARM64 only, target SDK 36,
  minimum manifest SDK 26. The original lab's package/data are left intact.
- Non-debuggable APK, original vector launcher icon, separate persistent signing
  identity. Only the product launcher is exported; it discards external extras.
  The owned diagnostic/guest native executables are excluded from this APK.
- User-facing version, dependency notices and SAF diagnostic export are available
  under About and diagnostics. Export uses a field allowlist and never includes
  captured runtime stdout, typed text, file paths, carts or runtime files.
- Before this increment, the fetched remote had only `main`, at `9d159fc`.
  Current tracked files and all 41 reachable commits (509 historical path/blob
  entries) passed the proprietary/archive/key audit. No Picotron runtime file
  had to be deleted or removed from history. A final post-commit audit accompanies
  the prepared release attachments. Local owner runtime inputs remain ignored.
- README is a user-facing showcase with two actual device screenshots. The second
  uses the original [Pocket Orbit example](../examples/pocket_orbit.lua).
  No commercial runtime files or archive contents are committed.

## Scoped Retroid check

Retroid Pocket Classic, Android 14/API 34, ARM64. The new product package was
installed alongside the lab without replacing its data. The owner ZIP was chosen
through Android SAF and successfully imported in the new app's separate home.
The non-debuggable product started official Picotron 0.3.0d2, showed its original
desktop, and ran the owned showcase. An injected touchscreen D-pad gesture moved
the spacecraft. This is not physical-controller or broad gameplay acceptance.

The final build, after the diagnostic path fix, also launched and closed normally.
Its real SAF-exported support report contains:

- COMPLETE, `closed=true`, exit code 0, elapsed 62,701 ms;
- 480x270 frames, 3,859 received frames;
- ordered accepted/sent/acknowledged **16/16/16**;
- audio received/written **11,534,356 / 11,534,356 bytes** (not listening proof);
- `session_running=false` after exit. A process census found no Picotron/PRoot
  child, only the Android hosts.

`run-as art.picotronnest` was rejected as non-debuggable, and an external attempt
to open its private MainActivity was rejected by Android. The final exported JSON
was inspected and contains only the documented device/version/session fields.
The app's notice list and a notice document opened successfully.

An earlier export lacked session counters because Android's `/data/user/0` alias
did not compare equal to its canonical path. The collector now canonicalizes the
trusted app-owned root before validating descendants. A real Android collection
regression check passes, and the final SAF export includes the session above.

The first showcase terminal command exceeded Picotron's command-line length and
was rejected. The source was then stored in bounded chunks before the successful
run. This does not establish a new save/reopen acceptance result for the product
package; the previous lab's scoped save/reopen evidence remains separately recorded.

## Artifacts and focused checks

| Artifact | SHA-256 |
| --- | --- |
| Final product APK, installed and checked | `4d16830e45d4529cf43f21e8ae6ffabef5ff0fa94ef127e8c0a83b4b3d95168d` |
| ARM64 owned fixture APK, focused instrumentation passed | `43549eb6cff88d48e5089363b66cdeea86fa98f1aee5fd593c100bafef89521a` |
| Intermediate product used for README captures | `b9c575fe6c65cc6ac2eda2059a84b2776220c983f6460e84de54f10fdc7e97c5` |

Release signing certificate SHA-256:
`3d7a10c54321501adfbcd8b32c9ee528771a157be765735d0e43c10b5f17d538`.
The key/password stay private under `.local/release/signing/`.

Thirteen tool checks pass (archive inspection, repository guard, candidate
manifest/version boundaries). Both APK builds pass payload/source checks.
Focused Android instrumentation passes diagnostic privacy/canonical-path
collection plus the existing multi-touch/source-ownership/cancel checks. No full
emulator or deep physical suite was rerun. Private output includes
`.local/release/candidate-final-diagnostics.json` and build logs; public screenshots
have [provenance notes](screenshots/README.md).

## Handoff limits

Local release preparation supplies the APK, project source archive, pinned full
dependency-source bundle, notices, checksums, build manifest and repository audit.
See [release instructions](RELEASING.md), [release notes](releases/0.1.0-beta.1.md),
[user guide](USER_GUIDE.md) and [tester checklist](TESTING.md).

The source-license question remains with the owner; no open-source grant was
invented. The existing rights position is retained by the project notice until
that selection is made. No tag, GitHub Release or public APK upload was created.

Online Picotron features are unavailable (no network permission). Audible sound,
phone ergonomics, physical analog/multiple-controller behavior, long sessions
and broader/enforcing ARM64 compatibility remain tester work. The local-project
candidate must not be advertised as a fully functional online workstation.
