# Modern-target execution probe

This temporary lab measures Android execution restrictions before a Linux
compatibility environment is selected. It is not PicotronNest's product UI.
It uses original, harmless native fixtures, never the purchased runtime. The
optional `--linux-loader` build adds the pinned PRoot closure described below.

Package: `art.picotronnest.executionlab`. Compile/target API 36, minimum API 26.
The APK has one icon, no permissions, no network and no storage provider access.
It installs independently of PikoNest and any existing Picotron data.

## Build

Prerequisites on Windows: Python 3.11+, Android Studio JDK, SDK platform 36,
build-tools 36.0.0 and NDK 28.2.13676358. No automatic dependency download occurs.

```powershell
py -3 -B experiments/execution-model/build.py
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py' -v
```

For P0b.2, build with `--linux-loader`. This downloads and verifies only locked
open-source packages, matching source archives, recipes and license texts; caches
are under `.local/proot/downloads/`. It produces version 0.0.2 in the same lab
namespace, with packaged PRoot/loader/dependencies and owned Linux guest assets.
The default build remains the original Bionic preflight (version 0.0.1).
See [P0b.2 evidence and native dependency audit](../../docs/P0_LINUX_LOADER.md).

Override SDK/JDK locations with `--sdk` and `--jdk`. Outputs, debug signing key,
ELF reports, build hashes and APK are under ignored `.local/execution-model/`.
Build work directories are unique and retained. No runtime archive input is
accepted. Packaging checks allow only the manifest, owned Java/native code,
NDK notices, signing entries and explicitly locked optional PRoot assets/libraries.
APK signatures and native payload hashes are
verified. Build commands are reproducible; ZIP timestamps and the locally
generated debug key mean the APK is not claimed bit-for-bit deterministic.

The same original fixture is built for ARM64 (physical device) and x86_64
(policy control in an emulator). The emulator does not establish ARM64 support.
Native code compiles with warnings as errors and 16 KiB ELF segment alignment;
this is not a 16 KiB page-size device test.

## Run and collect

Use an explicitly selected device. First verify that this package is absent;
do not overwrite an unknown installation with a matching namespace.

```powershell
adb -s DEVICE_SERIAL shell pm list packages art.picotronnest.executionlab
adb -s DEVICE_SERIAL install .local/execution-model/artifacts/picotronnest-execution-lab.apk
adb -s DEVICE_SERIAL shell am start -W -n art.picotronnest.executionlab/.MainActivity
py -3 -B experiments/execution-model/check_report.py --serial DEVICE_SERIAL
```

Starting the Activity runs fixed probes in the app's `untrusted_app` context.
`run-as` only reads the resulting report; it never launches the native fixture.
The collector also verifies installed APK/fixture hashes, process markers and
ownership, expected trace-child exit, bounded-output status, complete process
census and a stable report. Only the recorded host may remain under its UID.
Evidence is retained under `.local/execution-model/evidence/`.

The Activity startup callback can precede test completion. If collection sees
an unfinished/stale session, wait for this app's `PicotronNestProbe` COMPLETE log
and collect again. Do not treat `am start` success as execution evidence. Restrict
logs to this tag/current PID; do not collect unrelated applications' crash logs.

After a verified complete session, repeat a cold start with:

```powershell
adb -s DEVICE_SERIAL shell am force-stop art.picotronnest.executionlab
adb -s DEVICE_SERIAL shell am start -W -n art.picotronnest.executionlab/.MainActivity
py -3 -B experiments/execution-model/check_report.py --serial DEVICE_SERIAL
```

This stops only the disposable lab after its children have exited. It does not
test graceful Picotron exit, active-session interruption or recovery. Preserve
existing session evidence. Future builds may update this known lab using `install
-r`; never clear user data to obtain a passing result.

## Cases and containment

1. Execute the packaged PIE fixture from `nativeLibraryDir`.
2. Copy that same fixture into a fresh private session, verify SHA-256, chmod
   0700, then attempt direct execution.
3. Invoke `/system/bin/linker64` with that private fixture (Bionic, not glibc).
4. From the packaged fixture, fork an owned child, request tracing, observe its
   stop, continue it and reap its known exit code 37.
5. Wait at most five seconds for the owned hang fixture's explicit readiness
   marker, then apply a 250 ms host deadline, terminate and reap it. Missing
   readiness cannot pass the evidence checker.

With `--linux-loader`, two additional cases launch a libc-free guest under PRoot
in a new app-private root. The first must read the current session's marker; the
second retains that file under a different name and must exit 41 for missing
input. The guest itself reports PID/parent/UID. Each PRoot group has a system
timeout of five seconds plus one second kill grace and an eight-second Java
observation bound. Its short temporary directory avoids the path-length limit
previously observed in PikoNest.

Base commands have a six-second host deadline. The fixture has a four-second
alarm (ten seconds for hang mode); its trace child has a three-second alarm and parent-death signal. The
trace parent enables `PTRACE_O_EXITKILL`. These limits apply only to owned test
processes. No arbitrary command/intent arguments are accepted.

Output is drained concurrently and retained up to 16 KiB per invocation. The
timeout case may record an interrupted pipe read after termination; non-timeout
read errors invalidate the evidence. Exit 143 was observed for host termination;
no claim is made about the precise Android implementation's kill signal.

Each session has an immutable UUID and atomically written report. A process-wide
launch gate plus file lock prevents parallel runs; Activity recreation does not
start another. Incomplete sessions are retained and block retry, and a 16-session
quota refuses further tests without deleting anything. Recovery UI and product
lifecycle behavior are intentionally outside this probe.

## Provenance

Java and C probe sources are original PicotronNest work. No source was copied
from PikoNest, PRoot, Termux or their APKs. The build uses standard SDK packaging
tools. NDK CRT/compiler support is linked by the toolchain; its installed
`NOTICE` is included verbatim as `assets/NDK-NOTICE.txt`. The fixture's only
DT_NEEDED entries are Android system `libc.so` and `libdl.so`; neither is bundled.
The default build bundles no C++ runtime, glibc, SDL2, PRoot or purchased runtime.
The optional Linux-loader build includes PRoot, talloc and android-shmem from
verified packages, with selected dynamic-string adaptations, license texts and
source manifests. Its [component audit](../../docs/P0_LINUX_LOADER.md) records
the complete native closure, exact modifications and remaining release gates.

See [device findings and next decision](../../docs/P0_EXECUTION_MODEL.md).

## Optional official headless probe (native ARM64 only)

`build.py --headless` implies `--linux-loader` and builds lab version 0.0.3.
It adds pinned glibc and a minimal SDL2 built from source using CMake/Ninja and
the NDK Clang cross-compiler. CMake and Ninja must be on PATH; versions are
recorded in the component manifest. Proprietary files are never APK build inputs.
The build rejects source changes made during compilation.

After installing the completed build, use `provision_runtime.py --serial SERIAL
--archive OWNER_COMBINED_ZIP` to provision the exact inspected owner runtime.
This is an explicit debug-only procedure: bounded ZIP validation, exact hashes,
fixed members, base64 ADB transport, device-side hash checks and a final ready
marker. An existing import is refused and retained. The app validates/copies
the import again into each session's separate root. App updates retain it.

Two additional cases run the official runtime with `-home /home -x /probe/check.lua`.
The owned glibc launcher records identity before exec; Picotron must print two
fresh Lua markers per attempt and exit 0. A timeout or leftover same-UID process
fails collection even if a marker was printed. Headless reports use the separate
`files/headless-probes` journal; the existing collector selects it from build
metadata. The prior 16-session quota applies separately to this journal.

See [P0c evidence, dependency audit and complete procedure](../../docs/P0_HEADLESS.md).
Do not install this optional ARM64 experiment on the x86_64 control emulator;
it deliberately rejects unsupported ABIs. The regular `--linux-loader` probe
continues to support both ABIs.
