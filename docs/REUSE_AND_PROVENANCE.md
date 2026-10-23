# Reuse from PikoNest and upstream provenance

**Scope:** a map of ideas and code worth evaluating, not permission to copy entire systems blindly.

2026-10-23: [P0b.2](P0_LINUX_LOADER.md) records a fresh read-only PikoNest review,
the exact execution similarities/differences, and the small PRoot payload used
in PicotronNest's independent target-36 lab. No PikoNest code or bootstrap binary
was copied. The optional lab's native dependency audit and source/package hashes
are recorded separately from future product redistribution requirements.

P0c adds a separately locked glibc closure and source-built minimal SDL2,
without copying PikoNest's SDL shim. [Dependency/source/license evidence](P0_HEADLESS.md)
distinguishes locally rebuilt components from pinned distribution binaries.

## Proven baseline

[PikoNest](https://github.com/EriArk/PikoNest) developed an experimental Android-first host for a **user-provided official PICO-8 ARM64 Raspberry Pi runtime**. Its October 2026 single-APK work demonstrated local setup, official runtime launch and return on Retroid Pocket Classic. This is useful engineering prior art for PicotronNest; it is **not Picotron runtime compatibility evidence**.

PikoNest original source is published under MIT; consult that repository's LICENSE and THIRD_PARTY_NOTICES for details, and retain notices where source is reused. PikoNest's incorporated upstream components have **their own** terms. Licensing requirements must be reviewed per component, not inferred from a top-level MIT license.

## What is potentially portable

| Area | PikoNest reference | PicotronNest adaptation |
| --- | --- | --- |
| Archive inspection / integrity | `RuntimeArchive.java`, `RuntimeFileSet.java`, `RuntimeSetup.java` | Generalize to unknown Picotron ARM64 archive and required files, with bounded validation |
| Runtime installation / migration | `RuntimeInstallation.java`, `RuntimeArchiveJob.java`, `RuntimeHomes.java`, `RuntimeMigrationJob.java` | Separate runtime installation from Picotron's persistent `-home`; never infer PICO-8 paths |
| Session state and recovery | `RuntimeSession.java`, `RuntimeBootIdentity.java`, `RuntimeRecovery.java`, `RuntimeProbe.java` | Preserve explicit journal, PID/boot identity, fail-closed unknown sessions; adapt process contract |
| Android host / controller framework | `ControllerInput.java`, `MainActivity.java`, `RuntimeSetupActivity.java` | Small setup/runtime menu; full mouse/text keyboard mapping is new work |
| Integrated APK packaging | `docs/SINGLE_APK.md`, `experiments/android-integrated/` | Reuse tested packaging ideas; avoid copying PICO-8-specific boot scripts |
| Linux userland / bridge | `experiments/runtime-restart/`; pinned [Macs75/pico8-android](https://github.com/Macs75/pico8-android) | Investigate PRoot, process/audio infrastructure; create new SDL video path |
| Storage safety | `docs/FIRST_RUN_SETUP.md`, `docs/ANDROID_APP.md` | SAF, non-destructive staging, verified activation, private persistent data |

Names refer to PikoNest's current paths for research, **not dependencies** of PicotronNest. Keep reused modules small and separately attributable. Avoid copying packaged or generated binaries.

## What must not be copied as-is

- The PICO-8 cartridge model: `.p8`, `.p8.png`, sprite/map/Lua editors, Play shelf and workshop logic.
- The exact PICO-8 runtime paths, command-line flags, PICO-8 data layout, process names and proprietary file tests.
- PICO-8-specific UI and 128x128 framebuffer. The inspected upstream SDL shim has fixed `FB_WIDTH` and `FB_HEIGHT` of 128, loops across 16,384 pixels and reads a window surface directly. Picotron video path requires independent inspection.
- The idea that a legacy Android target SDK 28 is an acceptable permanent solution. PikoNest documents target 29+ execution restrictions and still has modernization work outstanding.
- Binary payloads from upstream APKs without reviewing source, license, provenance, redistribution obligations and necessary notices.

## Upstream references

- [Official Picotron manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html): SDL2, display modes, `-home`, `-x`, native cartridges.
- [Official Picotron roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap): Raspberry Pi 64-bit support milestone.
- [Picotron BBS Pi discussion](https://www.lexaloffle.com/bbs/?pid=184871): community report of an available Raspberry Pi download in March 2026; informative, not technical ABI proof.
- [PikoNest single-APK decision](https://github.com/EriArk/PikoNest/blob/main/docs/SINGLE_APK.md).
- [PikoNest integrated APK build](https://github.com/EriArk/PikoNest/blob/main/experiments/android-integrated/README.md).
- [PikoNest original runtime proof](https://github.com/EriArk/PikoNest/blob/main/docs/ANDROID_RUNTIME_POC.md).
- [Macs75 PICO-8 Android frontend](https://github.com/Macs75/pico8-android) and [pinned PikoNest inspection revision](https://github.com/Macs75/pico8-android/tree/562662e35727ae7706fe97fed3390db249a35263).
- [Android 10 target-29 execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission).

## Extraction procedure for future work

1. Identify a bounded reusable module or behavioral contract. Read the exact upstream revision.
2. Record upstream repository, commit, path, copyright notice, license, modification summary and tests.
3. Copy only what is necessary to a new namespaced component with independent tests. Do not bind PicotronNest to a running PikoNest installation.
4. For binary support dependencies, retain complete license/provenance evidence and reproducible build inputs.
5. Evaluate compatibility with actual Picotron ARM64 runtime and note both PASS and NOT PROVEN cases in [STATUS](STATUS.md).

If a source file or upstream dependency has unclear redistribution terms, do **not** bundle it until resolved.
