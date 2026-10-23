# P0c — Official Picotron headless execution

2026-10-23. **Device-verified for the scoped headless probe on Retroid Pocket
Classic, Android 14/API 34, native ARM64, target SDK 36.** Desktop rendering,
interactive input and audible sound remain NOT TESTED.

The owner's unchanged Raspberry Pi Picotron 0.3.0d2 executable and data file
execute an original Lua script twice, emit distinct current-session markers,
and exit with code 0. The same disposable home is used for those two launches.
The collector verifies the installed APK hash and that only the host remains
under the app UID after completion. No installed Termux/PikoNest runtime, root,
helper APK or device-policy change is used.

## Exact scope

The app is still the separate, debuggable `art.picotronnest.executionlab`, version
0.0.3. This is a developer experiment, not a user-facing runtime importer or a
product APK. ADB provisions the owner's files into this app's private storage;
the app then launches the processes under its own UID/SELinux context. `run-as`
is used for provisioning and reading evidence, never to execute the runtime.

The prior enforcing Android 15 x86_64 emulator proved the static Linux guest
only. It has **not** run this ARM64 glibc/Picotron probe. Retroid reports seccomp
mode 0 in the Bionic control; its SELinux enforcement mode is not readable.
Target 36 on this device is not broad modern-Android compatibility proof.

## Private runtime and execution contract

[The metadata-only contract](../experiments/execution-model/runtime-contract.json)
locks the inspected archive and both necessary members:

| Input | SHA-256 |
| --- | --- |
| `picotron_0.3.0d2_raspi.zip` | `ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75` |
| `picotron/picotron` | `481b0214c51517cac9c4aaad5bea3c2de9d16c58a8c50bec1358fef552724a5f` |
| `picotron/picotron.dat` | `c41a294a4a0158fa7857e764e1c137c4978315ca2c8d9c5a3d957e42d5297e7c` |

The bundled manual identifies itself as v0.3.0d and explicitly documents
`picotron -home PATH` and `picotron -x FILE`. The headless script may reside
outside Picotron's drive; Picotron copies it internally. `printh()` writes to
the host console. These facts were read from the owner's manual, not inferred
from PICO-8 flags.

Provisioning validates the outer ZIP inventory and selected inner ZIP before
reading fixed members. It compares archive/member hashes and sizes, transfers
only those two members, verifies hashes on-device, and writes a ready marker
last. Existing candidates are refused rather than overwritten. Each session
copies the validated members into its own fresh root and verifies all hashes
again. The original import and every previous home are retained.

Inside the PRoot guest the owned glibc fixture calls:

```text
/runtime/picotron -home /home -x /probe/check.lua
```

The fixture reports the actual glibc/SDL versions and PID/PPID/UID, flushes the
output, then `execv`s Picotron. Successful exec preserves that PID. This identity
alone is insufficient: the official runtime must also execute the owned Lua
script and emit both current-attempt markers, then exit normally. The script
prints its token, checks `6*7 == 42`, prints completion and calls `exit()`.
Picotron prefixes these lines with its internal userland process number `[004]`.
The second launch uses a different marker, preventing old output from passing.

PRoot uses its APK-packaged loader and a fresh short temporary directory. The
root binds `/proc`, `/dev/null` and `/dev/urandom`; no broad shared-storage bind
is used. PRoot translates paths; it is **not a security sandbox**. Android's app
UID is the security boundary. The guest inherits no Android `LD_PRELOAD`; the
fixture selects guest `/lib`, dummy SDL video/audio and guest `/home` explicitly.
Android `timeout -k 2 20` bounds the PRoot process group, with a 25-second host
observation deadline. Existing lock/quota/unfinished-session protections apply.

## Dependency closure and provenance

[The lock](../experiments/execution-model/headless.lock.json) pins official
HTTPS downloads, including glibc upstream source and the exact Debian patch
archive described by its `.dsc`. Source/archive hashes are checked before
packaging. This is hash pinning of reviewed HTTPS inputs, not an independently
verified Debian signature chain.

| Component | Origin / role | License evidence |
| --- | --- | --- |
| glibc 2.36-9+deb12u14 ARM64 | Debian `libc6`; loader, libc, libm, libdl, libpthread | LGPL-2.1-or-later plus embedded notices; Debian copyright, upstream COPYING, COPYING.LIB and LICENSES retained |
| glibc development files, same version | `libc6-dev`; build-only headers, CRT and linked `libc_nonshared` fragments | Same corresponding glibc source and notices |
| Linux 6.1.176-1 UAPI headers | Debian `linux-libc-dev`; build-only | Package copyright retained; UAPI syscall exception applies to exported headers, not a bundled Linux kernel |
| SDL2 2.32.10 | Unmodified official source, cross-compiled for Linux ARM64 | zlib license and embedded third-party notice blocks retained, including Sun fdlibm and CC0 code |
| Owned glibc exec fixture | This repository, dynamically linked to glibc/SDL2 | Original source; linked CRT covered by glibc notices |
| Android PRoot closure | Unchanged from P0b.2 | [Separate pinned audit](P0_LINUX_LOADER.md) |

The native graph is checked after building: loader has no DT_NEEDED; libc needs
the loader; libm needs libc/loader; libdl and libpthread need libc; SDL2 needs
those four glibc libraries; the fixture needs SDL2/libc. Picotron's five direct
dependencies from P0a are all in this closure. The actual headless executions
then establish that symbol resolution and initialization work for this probe.
No X11, Wayland, GPU stack, PulseAudio, ALSA or shell is packaged in the Linux
root. Unused kernel netfilter headers are excluded from the Windows sysroot
because they contain case aliases; no runtime behavior is patched.

SDL's video/audio dummy drivers are retained; real display/audio backends and
joystick/haptic/sensor integrations are disabled. This deliberately does not
claim to supply the eventual workstation bridge. CMake/Ninja/Clang versions,
disabled options, per-file hashes and DT_NEEDED entries are recorded in the
build manifest. SDL and the owned fixture are built locally; glibc and PRoot
are pinned distribution binaries with source inputs retained. A complete
from-source rebuild and release source-provisioning review remain release gates.

APK packaging uses an exact payload allowlist. Owner files are never build
inputs and are absent from the APK. Notices and source manifests are in
`assets/licenses/`; proprietary archives, extracted files, APKs and raw reports
remain ignored local/private artifacts.

## Reproduce

Use the Windows SDK/NDK/JDK prerequisites in the [lab README](../experiments/execution-model/README.md),
plus CMake and Ninja on PATH. Build and wait for successful completion before
installation; do not edit build inputs during compilation.

```powershell
py -3 -B experiments/execution-model/build.py --headless
adb -s ARM64_SERIAL install -r .local/execution-model/artifacts/picotronnest-execution-lab.apk
py -3 -B experiments/execution-model/provision_runtime.py --serial ARM64_SERIAL --archive OWNER_COMBINED_ZIP
adb -s ARM64_SERIAL shell am start -n art.picotronnest.executionlab/.MainActivity
py -3 -B experiments/execution-model/check_report.py --serial ARM64_SERIAL
```

Only update the project-owned lab package. On a first install follow the
namespace-ownership check in the lab README and omit `-r`. Provisioning is needed
only once for this exact contract; rerunning it intentionally refuses an
existing import. App updates retain the import. Launch the activity after
installation; wait for COMPLETE before collecting. For another experiment
launch, first verify the prior report and host-only process census, finish the
activity, then start it again. Never clear app data to erase unknown sessions.

## Evidence and limits

Repository base: `5b90c6dfa662e438eca8f59aa127bf15438611a7`, plus this increment.
Final APK SHA-256:

```text
e677363c51531aeafd041dbcfbac006698d13cc51299a90c469e033111dbfcd2
```

Final private report:
`.local/execution-model/evidence/4f00bfe8-0664-4a09-b468-f968fcd521b6.json`.

| Observation | Final result |
| --- | --- |
| Device / host | Retroid Pocket Classic, Android 14/API 34, ARM64, target 36 |
| First official headless invocation | Current `:1` Lua/start/completion markers; exit 0 |
| Second invocation, same disposable home | Distinct current `:2` markers; exit 0 |
| Dynamic versions | glibc 2.36, SDL2 2.32.10 observed in the owned exec fixture |
| Prior Bionic/ptrace/static guest controls | All expected outcomes retained |
| Final app-UID census | Host only; no runtime/tracer/timeout children remain |
| Installed APK | Hash matches the completed build |
| Owner payload isolation | Neither proprietary file hash appears among APK members; exact APK allowlist verified |
| Source/build correspondence | All recorded input hashes match the final sources |

Final host PID/UID: `18181` / `10151`; exec-preserved Picotron PIDs: `18258`
and `18266`. Kernel: `5.10.209-android12-9-ge986468a683e-dirty`. These are session
observations, not stable identifiers or a claim of visual/device-owner review.

Tools: NDK 28.2.13676358, its Clang 19.0.1, CMake 4.3.3, Ninja 1.13.2,
Android compile/target API 36 and build-tools 36.0.0. Java and owned C fixtures
compile with warnings treated as errors. Upstream SDL uses its own warning
configuration. The build logs and native hashes remain in ignored local output.

Host validation: **32 tests passed** (9 archive inspection, 23 execution-lab
tests). Negative cases reject exec-only output, stale attempt markers, timeout
after script output, a wrong owner contract, a wrong archive and unsafe source
paths. Existing process, missing-marker and dynamic-string checks still pass.

An exploratory build also completed both official launches, with normal exit
and no remaining guest processes. The final session created `picotron_config.txt`,
`sdl_controllers.txt` and `drive/.info.pod` under the disposable home. This does
not prove saving/reopening user projects or P3 lifecycle acceptance.

Two initial ADB binary-stdin transfers were rejected by on-device hash checks
(one short data file and one truncated executable). They were never marked
ready or executed. Both incomplete candidates were retained under separate
names. Base64 transport over the shell protocol, followed by device hash checks,
successfully provisioned exact bytes. No proprietary data is sent through the
tool's output stream or a shared device directory.

The initial source build also exposed the required `libc_nonshared` fragment;
it is now an explicit build input. Build metadata snapshots source hashes before
compilation and refuses completion if they change during the build.

## Next step

P1a: trace actual SDL window/renderer/surface presentation calls from the official
Picotron desktop, recording dimensions, stride, format and ownership. Then
implement only the bridge needed to display a real frame. Headless success does
not establish the GUI path, interactive input, sound, performance, save recovery
or execution under an enforcing ARM64 Android configuration.
