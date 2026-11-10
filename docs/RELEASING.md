# Build and release preparation

The current published prerelease is **0.1.0-beta.3**, application ID **art.picotronnest**,
version code **1003**, ARM64, target SDK **36**, minimum SDK **26**. The minimum
SDK is a manifest floor, not a claim that every Android 8+ device can execute the
runtime. Retroid/Android 14 is the hardware evidence currently available.

Beta 3 was published with owner authorization from commit `2dbdbf6`; all 12
attachment digests and the anonymous public APK download were verified. Its
scoped evidence is tracked in [ONBOARDING](ONBOARDING.md) and
[AUDIO_AND_LIBRARY](AUDIO_AND_LIBRARY.md).

## Build on Linux

The product and owned native fixture now support Linux x86-64 with explicit SDK/JDK
paths and separate development artifact directories. See [Linux build and validation](LINUX_BUILD.md)
for reproducible commands, current private artifact hashes and acceptance limits.

## Build on Windows

Required local tools: Python 3.11+, Android SDK platform/build-tools 36.0.0,
NDK 28.2.13676358, Android Studio's JBR, CMake and Ninja. The pinned Linux sources
and packages are downloaded by the build scripts and checked against lock files.
No user runtime ZIP is a build input.

```powershell
# Build the open-source base (the first run compiles SDL and downloads dependencies):
py -3 -B experiments/execution-model/build.py --headless
# Preserve the generated APK AND its sibling build.json together in a private directory.
# The examples below use .local/desktop/gamepad-base/ as that directory.

# Create the release signing identity ONCE, then keep a private backup of both files:
py -3 -B tools/create_signing_key.py

py -3 -B experiments/execution-model/build.py --candidate `
  --desktop-base .local/desktop/gamepad-base/picotronnest-execution-lab.apk `
  --keystore .local/release/signing/picotronnest.jks `
  --password-file .local/release/signing/password.txt
```

The candidate APK and its `build.json` are placed in `.local/release/candidate/`.
Candidate packaging has a strict payload allowlist and source hashes. It is
non-debuggable, ships only ARM64 libraries and removes the owned execution/guest
and headless diagnostic executables. Its only exported activity is the launcher,
which does not forward external intent extras. Other activities and the runtime
service remain private. The launcher has an original vector icon.

The signing helper refuses to overwrite existing files. The same private key
must sign subsequent product updates. Keep the key and password outside Git,
with a private backup; losing them prevents compatible updates. They are not
release attachments. Do not sign public builds with the development key.

## Validate the candidate

```powershell
py -3 -B -m unittest discover -s tools -p test_*.py
py -3 -B -m unittest discover -s experiments/execution-model -p test_frame_protocol.py
py -3 -B experiments/execution-model/build_bridge_tests.py --abi arm64-v8a
py -3 -B tools/audit_repository.py
```

Use the [tester checklist](TESTING.md) for the actual APK. Inspect its exported
diagnostic JSON, not raw private logs. The separate owned fixture APK is a
developer test tool; it is never an end-user dependency or a release attachment.

`audit_repository.py` scans current tracked files and all locally reachable Git
history for known runtime hashes, executable/archive signatures, private-key
material, private folders and disallowed binary/cart extensions. Reviewed PNGs
are allowed only under `docs/screenshots/`. It is a guard against accidental
payload inclusion, not a general secret-detection guarantee. Fetch remote refs
before auditing; do not rewrite shared history without first reviewing findings.

## Prepare attachments

After review, commit the exact build sources and docs on `main`, then run:

```powershell
py -3 -B tools/prepare_release.py
```

The command requires a clean worktree and matching candidate source hashes.
It prepares `.local/release/0.1.0-beta.3/` with the APK, source ZIP, exact
dependency-source ZIP, original-screenshot ZIP, notices, build manifest, audit report
and `SHA256SUMS`. The screenshot ZIP contains only the tracked gallery at that commit.
It checks that no known proprietary runtime payload appears inside the APK.
The dependency bundle includes SDL, glibc source/Debian patches, PRoot, talloc,
shmem, libcurl, Mbed TLS, exact recipe files and the pinned full Termux build-recipe tree. Project
build/adaptation scripts and the exact source archive accompany the binaries.

Files remain local until publication is explicitly authorized. Original project
code uses the owner-selected MIT license, included as `LICENSE` and in the APK.
Third-party terms and their source/notices are always preserved.

## Publication handoff

Before a public prerelease:

1. Confirm the owner-selected project license and the candidate's stated support
   scope. Record unresolved tester checks instead of calling them passed.
2. Keep the APK, both source archives, notices and checksums together. Review the
   exact attachment list; never upload `.local` wholesale.
3. Obtain the owner's explicit approval to publish. Preparation and source pushes
   do not create that approval.
4. On approval, tag the reviewed commit `v0.1.0-beta.3`, publish as a **prerelease**,
   and use the prepared release notes. Change README's download status only after
   verifying the actual release URL and attachments.

No workflow in this repository publishes an APK or creates a GitHub Release
automatically. Lab-to-product data transfer is described in the [user guide](USER_GUIDE.md).
