# P0b.1 — Modern-target execution conditions

Date: 2026-10-23. **Owned native fixture verified on physical Android; Picotron
execution remains NOT TESTED.** This increment tests a prerequisite of P0b; it
does not complete the Linux dependency environment or P0.

Follow-up: [P0b.2](P0_LINUX_LOADER.md) subsequently verified packaged PRoot and
an owned Linux guest. This page preserves the original P0b.1 build and findings.

## Question and result

Can a target-36 app start its packaged native helper, map/execute an owned private
Bionic fixture through Android's linker, and trace a child in the actual app
context? The tested cases succeeded on both the ARM64 Retroid and an x86_64
Android 15 emulator. Direct execution of the private fixture differed between
them. We cannot rely on Retroid's direct-execution result as general policy proof.

[Android's documented target-29 restriction](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
blocks direct `execve()` from writable app-private storage. The emulator reproduced
this denial; the Retroid did not. No security policy, target downgrade or root was
used or changed during this experiment.

| Observation | Retroid Pocket Classic | Android emulator |
| --- | --- | --- |
| OS / API | Android 14 / 34 | Android 15 / 35 |
| Native fixture ABI | ARM64 | x86_64 (not translated ARM64) |
| App target | 36 | 36 |
| Child SELinux domain | `untrusted_app` | `untrusted_app` |
| Child `PR_GET_SECCOMP` | 0 | 2 |
| `adb shell getenforce` | Permission denied; mode unknown | Enforcing |
| Packaged executable | Exit 0 + native identity | Exit 0 + native identity |
| Private direct executable | Exit 0 + native identity | Start rejected, EACCES / error 13 |
| Private Bionic ELF via `linker64` | Exit 0 + native identity | Exit 0 + native identity |
| Owned traced child | Reaped exit 37; parent exit 0 | Reaped exit 37; parent exit 0 |
| Owned timeout | Deadline recorded; reaped exit 143 | Deadline recorded; reaped exit 143 |
| Final app UID census | Host only; no probe children | Host only; no probe children |

Two complete sessions using the final dual-ABI APK were verified per device,
including one cold restart after a confirmed-idle force-stop of this lab only.
The Retroid also ran an earlier ARM64-only build; its result is not used as the
final APK evidence. Existing PikoNest packages and user data were not changed.

Retroid's enforcement mode and reason for the different seccomp/exec behavior
are unknown. Do not infer that SELinux is disabled, that every Retroid behaves
this way, or that a legacy app-storage launch design is acceptable.

## Reproduction and evidence

Sources and commands: [execution-model lab](../experiments/execution-model/README.md).
Repository base: `24c1e40a872fa25d8baaed7255e4e679d25fdfff`; this increment's
source hashes are in the local `build.json` manifest.

Tested APK SHA-256:

```text
e980e8f570dc3d88ac323f838fc24d8b2ed1ea77a9c36550d40ce311f4c4f9a6
```

Fixture SHA-256:

```text
arm64-v8a: c386734e2fb80650e5a5dae85167cfa28540c7879f65971d8f1959e37eb3d225
x86_64:    76f93c58498d9d0d927bbabe459c15bf8b76c91997710a07544e244b81a95f97
```

Build: NDK 28.2.13676358, SDK/build-tools 36/36.0.0; original C fixture,
Java Activity and fixed-command runner. No third-party runtime payload.
APK signature, strict payload allowlist and installed APK/fixture hashes checked.
Eight evidence-validator tests passed, including rejection of exit-only results,
wrong UID/build, incomplete census, orphan processes and unrelated start errors.

Detailed reports remain ignored under `.local/execution-model/evidence/`:

- Retroid: `9676005e-cf8f-40f6-a837-3e4e0c47ad2b.json`,
  `6b06f07f-e663-4ac4-b3e8-0415ca42688a.json`.
- Emulator: `2abb41f1-505b-4b23-a514-7fa1483865c4.json`,
  `afbec3eb-995e-43ba-9759-0a90d6baf005.json`.

Each contains commands' named outcomes, native PID/parent/UID, context, stdout,
exit code and build hashes. The collector runs read-only shell/census operations;
execution itself originates in the Activity's process, not `adb shell` or
`run-as`. One collection during emulator restart rejected the previous report
because its host PID no longer matched. Recollection after COMPLETE verified
the new session; the check was not weakened.

No visual, audio or Picotron session acceptance was attempted. Activity launch
timing and these fixture durations are not Picotron performance measurements.
Neither environment runs Android 16/API 36; target 36 is distinct from device OS.

## Next compatibility experiment

The Bionic system linker result does **not** imply that Android's linker can
load the glibc-linked Picotron executable. Keep the imported binary unmodified.
The next candidate is a packaged Android-native PRoot tracer plus packaged
unbundled loader, with a separately staged minimal Linux userland and owner
runtime. This is a hypothesis to test, not a selected production architecture.

Source pin inspected: `termux/proot` at
`ee6543334a67261d19e7023ae5eb30af5a7ad4f3`.
Its [loader selection](https://github.com/termux/proot/blob/ee6543334a67261d19e7023ae5eb30af5a7ad4f3/src/execve/enter.c)
supports `PROOT_LOADER`; this supplies a concrete path to test a loader residing
in `nativeLibraryDir`. Source inspection and a simple ptrace test do not prove
PRoot's complete syscall translation works under either device's restrictions.
The [license file](https://github.com/termux/proot/blob/ee6543334a67261d19e7023ae5eb30af5a7ad4f3/COPYING)
and source header identify GPLv2-or-later code. No PRoot source/binary has been
incorporated here; component and transitive licensing review remains required.

Next bounded work:

1. Pin/build the Android tracer and loader, identify their native dependencies,
   preserve notices and record corresponding-source/build inputs.
2. Test an owned Linux guest fixture through that loader before adding Picotron.
3. Pin the ARM64 glibc loader/libraries and SDL2; statically resolve the full
   DT_NEEDED closure and required GLIBC symbol versions. Optional SDL runtime
   backends must be audited separately from direct linkage.
4. Stage the owner's official files outside the APK, use an empty disposable
   home, then run the documented headless probe and capture real process output.

P0b dependency closure, P0c official execution and P1 video remain open. The
execution lab is a temporary independent test APK, not a second helper APK that
the final user would need to install.
