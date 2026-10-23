# P0b.2 — Packaged PRoot and an owned Linux guest

2026-10-23. **PASS for the scoped Linux guest probe on ARM64 Android and the
x86_64 policy-control emulator. Official Picotron execution is NOT TESTED.**

This is the historical P0b.2 record. The subsequent [P0c headless probe](P0_HEADLESS.md)
adds glibc/SDL2 and official Picotron execution on Retroid.

The target-36 execution lab now optionally packages a pinned Android-native
PRoot tracer, its standalone loader and two native dependencies. An original
libc-free Linux ELF is staged into a fresh private root and launched through
PRoot. No Termux app, root, installed PikoNest runtime or external helper APK is
used. No device security setting was changed.

## What PikoNest contributed

Read-only reference: `D:/Projects/PikoOS`, HEAD
`c747934d988301648c64cb320b4b82fc88e80588`. Its unrelated working changes were
preserved. Reviewed `docs/SINGLE_APK.md`, `THIRD_PARTY_NOTICES.md`,
`RuntimeInstallation.java`, `PatchBootstrap.java`, integrated preparation and
the pinned upstream bootstrap inside the local reference APK.

The reference provides useful contracts: prepare a separate candidate, retain
the previous installation, keep the persistent home separate, identify real
processes and fail closed when state is unknown. It also demonstrated the Unix
socket path-length problem; this probe keeps PRoot's temporary path short.

Its upstream bootstrap starts PRoot with a Linux rootfs, SDL shim and PulseAudio;
its integrated frontend runs in a separate process within one APK. The launcher
rewrites an embedded Termux loader path through an open directory descriptor.
Our probe uses the supported `PROOT_LOADER` environment variable and a loader
in `nativeLibraryDir` instead. No PikoNest source or bootstrap binary was copied.

### Why Picotron is not a wholly different execution problem

Static comparison of the owner's packages:

| Property | PICO-8 0.2.7 Raspberry Pi `pico8_64` | Picotron package 0.3.0d2 Raspberry Pi |
| --- | --- | --- |
| ABI | AArch64 | AArch64 |
| Interpreter | `/lib/ld-linux-aarch64.so.1` | Same |
| Required GLIBC versions | 2.17, 2.29 | Same |
| Direct libraries | SDL2, libm, libc | SDL2, libm, libc, libdl, libpthread |

This suggests substantial reuse of the execution approach; it is not proof of
runtime compatibility. The main known new display work is concrete: the pinned
[upstream shim](https://github.com/Macs75/pico8-android/blob/562662e35727ae7706fe97fed3390db249a35263/shim/shim.c)
defines width/height 128, forces a 128x128 window and sends its surface through
both window-surface and renderer-present hooks. Picotron needs a separately
observed frame path, dimensions, formats and full workstation input. Simply
renaming the runtime would preserve incorrect assumptions.

PikoNest's temporary target 28 and incomplete bootstrap dependency audit are
separate integration limitations, not inherent Picotron complexity. This lab
continues at target 36 with a much smaller explicitly inventoried payload.

## Reproduce

```powershell
py -3 -B experiments/execution-model/build.py --linux-loader
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py' -v
adb -s DEVICE_SERIAL install -r .local/execution-model/artifacts/picotronnest-execution-lab.apk
adb -s DEVICE_SERIAL shell am start -n art.picotronnest.executionlab/.MainActivity
py -3 -B experiments/execution-model/check_report.py --serial DEVICE_SERIAL
```

Only update the previously created `art.picotronnest.executionlab` package; first
installs should omit `-r` and check namespace ownership as described in the
[lab instructions](../experiments/execution-model/README.md). Wait for the build
to finish successfully before installation. Wait for the app's COMPLETE record
before collection; Activity launch success does not certify the probe.

The build downloads only exact files in [the lock](../experiments/execution-model/proot.lock.json)
and verifies package, source, recipe and selected ELF-member SHA-256 values.
Artifacts and sources stay in ignored `.local/`. Changed cached files fail
verification instead of being silently accepted or replaced. No general archive
extraction occurs: only fixed regular members are read into fixed output paths.

## Native closure and licensing

| Bundled component | Version / source | Dynamic dependencies | License evidence |
| --- | --- | --- | --- |
| PRoot + standalone loader | Termux package 5.1.107.99; source tag commit `968ae1be84c69a607dbcd43ee514ab04753d60c5` | libtalloc, libandroid-shmem, Android libc; loader has none | Source headers GPL-2.0-or-later, COPYING; preserve GPLv2 and GPLv3 texts |
| libtalloc | 2.5.0 | Android libc | Library source header LGPL-3.0-or-later, LICENSE; Termux's whole-package recipe labels GPL-3.0 |
| libandroid-shmem | 0.7 | Android liblog and libc | BSD-3-Clause source LICENSE |
| Owned diagnostic helper | This repository / NDK 28.2.13676358 | Android libdl and libc | Original code, NDK notices for CRT/compiler support |
| Owned Linux guest | This repository, ARM64/x86_64 | None; no ELF interpreter | Original libc-free syscall fixture |

Both ABIs' complete static DT_NEEDED closure was checked with readelf after
adaptation. System libc/libdl/liblog are not bundled. No SDL, glibc, PulseAudio,
BusyBox, Godot, 32-bit loader, Termux app or purchased runtime is included.
This audit covers this small lab payload, not future runtime dependencies.

Termux recipes are pinned at
`8c421499d33ef49f172757c9051a1c8bf9cad9f4`; the three selected package directories
contain no additional patches. Upstream source archives and exact build recipes
are fetched/verified before packaging. Talloc's official server timed out; the
MacPorts mirror supplied the identical upstream archive with the recipe's SHA-256.
License notices, talloc copyright and the source/package manifest are in APK
`assets/licenses/`. Local use of these prebuilt packages is not a claim of a
completed from-source reproducible toolchain rebuild or permission for a public
APK release. Source provisioning/rebuild instructions remain a release gate.

Adaptations affect open-source ELF dynamic strings only:

- Change the talloc filename/NEEDED/SONAME from `libtalloc.so.2` to `libtalloc.so`
  for Android's native-file naming convention.
- Replace the Termux absolute RUNPATH with `$ORIGIN`; supply this APK's native
  directory as `LD_LIBRARY_PATH`.
- Keep the PRoot loader bytes unchanged and select it using `PROOT_LOADER`.

Each before/after hash and exact changed dynamic tag is recorded. Tests check
that the patch does not grow the file or change bytes outside the relevant
dynamic-string fields. Proprietary binaries are never patched.

`PROOT_NO_SECCOMP=1` disables PRoot's optional syscall-filter acceleration, not
Android's inherited security filter. The emulator's owned native fixture still
reports seccomp mode 2. Performance optimization is outside this experiment.

## Observed evidence

Repository base: `aed293727538868576ac32a84bca43457b692ab6`, plus this increment.
App version 0.0.2; target/compile API 36. Final APK SHA-256:

```text
bb521537d67a92a59ed65abc94514f93b7cacfa3b7effab5b5eac6d8423d9f65
```

| Check | Retroid Pocket Classic, Android 14/API 34, ARM64 | Emulator, Android 15/API 35, native x86_64 |
| --- | --- | --- |
| Linux guest reads current session marker through `/probe/marker.txt` | Exit 0, PID/UID + current UUID observed | Exit 0, PID/UID + current UUID observed |
| Second launch with marker retained under a different name | Exit 41, explicit missing-marker output | Exit 41, explicit missing-marker output |
| Prior execution/ptrace controls | Passed; private direct execution allowed on this device | Passed; private direct execution denied with EACCES |
| Host timeout after explicit fixture readiness | Termination observed and reaped | Termination observed and reaped |
| Final same-UID census | Host only; no guest/tracer children remain | Host only; no guest/tracer children remain |
| Installed APK and native component hashes | Match recorded final build | Match recorded final build |

Detailed final reports are ignored local files:
`94efa1d1-d40c-465e-8d1f-f18b9423297d.json` (Retroid) and
`6bdfbf5c-33f1-43ce-b83b-e979077e8755.json` (emulator), under
`.local/execution-model/evidence/`. The code captures the guest's real PID,
parent PID and UID, root-specific nonce, output and exit status. All execution
originates in the app context; `run-as` is only used for reading reports.

The negative marker case is a real second guest launch, not an injected success.
Android's system `timeout -k 1 5` bounds each PRoot process group; Java retains an
eight-second observation limit. `--kill-on-exit` belongs to this disposable PRoot
probe. Interrupted/unknown sessions are preserved and refuse automatic retry.

Validation found and repaired a pre-existing timeout-test race: under emulator
load, a 250 ms deadline could kill the Bionic fixture before it had run. The
fixture now emits `PICONEST_HANG_READY`; the host allows at most five seconds for
readiness and only then starts its 250 ms deadline. A ten-second native watchdog
bounds hang mode. Missing readiness cannot pass validation. Other native modes
retain their four-second watchdog.

The collector also rejected an initial installation whose APK hash belonged to
the preceding build. Final evidence above was collected after installing the
completed build and verifying both installed hashes. Failed/stale reports were
not relabeled successful.

Host validation: 25 tests passed (9 archive-inspection and 16 execution-evidence/
native-preparation checks). Documentation links, whitespace checks and the final
APK's recorded source hashes also passed. Only sources, lock metadata and
reviewed documentation enter Git; APKs, package archives and raw device reports
remain ignored local artifacts.

## Next gate

Resolve and pin the ARM64 glibc loader and SDL2 dependency closure, retain their
source/license evidence, then test the user's unchanged Picotron with the
documented headless option and a fresh home. This static guest proves the
packaged PRoot route and path translation; it does not prove dynamic glibc
loading, Picotron initialization, video, input, audio, performance or user data
persistence. P0c and P1 remain open.
