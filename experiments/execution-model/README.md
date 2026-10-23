# Modern-target execution probe

This temporary lab measures Android execution restrictions before a Linux
compatibility environment is selected. It is not PicotronNest's product UI.
It uses an original, harmless native fixture, never the purchased runtime.

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

Override SDK/JDK locations with `--sdk` and `--jdk`. Outputs, debug signing key,
ELF reports, build hashes and APK are under ignored `.local/execution-model/`.
Build work directories are unique and retained. No runtime archive input is
accepted. Packaging checks allow only the manifest, owned Java/native code,
NDK notices and signing entries. APK signatures and native payload hashes are
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
5. Run the owned hang mode with a 250 ms host deadline, terminate and reap it.

Other commands have a six-second host deadline. The fixture has a four-second
alarm; its trace child has a three-second alarm and parent-death signal. The
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
No C++ runtime, glibc, SDL2, PRoot or purchased runtime is shipped in this lab APK.

See [device findings and next decision](../../docs/P0_EXECUTION_MODEL.md).
