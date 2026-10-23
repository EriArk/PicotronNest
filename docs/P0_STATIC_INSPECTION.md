# P0a — Owner package static inspection

Date: 2026-10-23. Outcome: **EXPERIMENTED / static ARM64 candidate identified**.
P0 process execution and P1 visible output remain **NOT TESTED**.

## Reproduction

Host used: Windows, Python 3.12.10, GNU readelf (GNU Binutils) 2.46.
Repository base: `70c37c2cb68c60bfe259ad7b164bd3953e154b4b`; the inspector and
this record were added as working-tree changes on that base.

Keep the purchased archive outside Git. Run from the repository root:

```powershell
py -3 -B tools/inspect_runtime.py 'PATH_TO_OWNER_BUNDLE.zip' --member picotron_0.3.0d2_raspi.zip --readelf 'PATH_TO_GNU_READELF'
py -3 -B -m unittest discover -s tools -p 'test_*.py' -v
```

For the standalone Raspberry Pi ZIP, omit `--member`. The nested member name is
explicit, not an assumed universal archive layout. JSON goes to stdout; save it
under ignored `.local/evidence/` when needed. Review filenames before sharing
reports from other packages. Do not redirect binary data into reports.

The tool inspects ZIPs without extracting archive-controlled paths. It rejects
traversal, case aliases, file/directory collisions, links, encryption, unsupported
compression and declared oversize entries. It reads selected-package members
with size/CRC checks. Limits: 256 MiB input, 512 MiB declared expanded contents,
128 MiB per member, 4,096 entries and 32 MiB per ELF. Only one explicitly selected
nested ZIP is supported; unrelated outer payloads are not decompressed or CRC
checked. These are research limits, not the final Android import policy.

The ELF alone is temporarily copied to an OS temporary directory under a fixed
filename for readelf, then removed by the temporary-directory context on normal
completion or handled failure. No Picotron code executes. Forced host termination
can leave temporary files; treat any such files as private. The inspector is a
local research tool, not a hardened sandbox for native binary parsers.

Exit codes: 0 = static AArch64 candidate, 2 = rejected input/tool failure,
3 = inspected ELF package without an AArch64 candidate. Code 0 never certifies
runtime compatibility or successful launch.

Validation on the recorded host:

- Nine synthetic regression tests passed, covering normal reads, traversal and
  ambiguous names, backslash paths, case aliases and file/directory collisions,
  symlinks, declared oversize, CRC corruption, bounded reads and truncated ELF.
- The supplied Raspberry Pi package returned code 0 and the observations below.
- The supplied `picotron_0.3.0d2_amd64.zip` returned code 3 with machine ID 62
  for both ELF members and `arm64_candidate: false`; no executable was launched.
- Local documentation links and `git diff --check` passed. The detailed local
  JSON report is ignored by Git; only reviewed structural metadata appears here.

## Observations

| Item | Observed value |
| --- | --- |
| Owner outer archive label | `Pico8_Picotron.zip` |
| Selected package | `picotron_0.3.0d2_raspi.zip` |
| Bundled manual version marker | `Picotron v0.3.0d` |
| Executable | `picotron/picotron`, 1,276,928 bytes |
| ABI | ELF64, little endian, AArch64 (machine 183) |
| Interpreter | `/lib/ld-linux-aarch64.so.1` |
| Direct NEEDED | `libSDL2-2.0.so.0`, `libm.so.6`, `libdl.so.2`, `libpthread.so.0`, `libc.so.6` |
| ELF search path | `/usr/local/lib` |
| GLIBC symbol versions | `GLIBC_2.17`, `GLIBC_2.29` |
| Data member | `picotron/picotron.dat`, 11,978,331 bytes |
| Other entries | Directory, license, icon and manual; six entries total |

SHA-256:

```text
Outer ZIP: c306a8df807c51fda6ee08bb17ba6900b6f488423fdad9c59e67f8a3da961c0f
Pi ZIP:    ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75
ELF:       481b0214c51517cac9c4aaad5bea3c2de9d16c58a8c50bec1358fef552724a5f
```

The archive label and manual marker differ; no executable version command has
been run. Report both rather than claiming a runtime-reported version.

Imported functions include `SDL_CreateRenderer`, `SDL_CreateTexture`,
`SDL_UpdateTexture`, `SDL_RenderCopy`, `SDL_RenderPresent`,
`SDL_GetWindowSurface` and `SDL_UpdateWindowSurface`. Audio imports include
`SDL_OpenAudioDevice`; events include `SDL_PollEvent`. This is evidence for
possible interfaces only. It does not establish which paths execute, SDL driver
availability, pixel format, audio configuration or working input.

The bundled manual documents `picotron -home myhome` and `picotron -x foo.lua`.
Before executing the headless probe, verify the drive/home mapping and probe-file
placement from that manual; do not infer them from PICO-8. The presence of the
data file does not yet prove how the process locates it at runtime.

## Device and reference inventory

Read-only commands on the explicitly selected physical device:

```text
adb devices -l
adb -s DEVICE_SERIAL shell getprop ro.product.model
adb -s DEVICE_SERIAL shell getprop ro.build.version.release
adb -s DEVICE_SERIAL shell getprop ro.build.version.sdk
adb -s DEVICE_SERIAL shell getprop ro.product.cpu.abilist
```

Observed: Retroid Pocket Classic, Android 14, API 34,
`arm64-v8a,armeabi-v7a,armeabi`. An x86_64 Android emulator was also attached;
it is not ARM64 runtime proof. No runtime execution, installation, copying or
user-visible device interaction was performed.

PikoNest reference checkout inspected read-only at
`c747934d988301648c64cb320b4b82fc88e80588`:
`docs/SINGLE_APK.md` and `experiments/android-integrated/README.md`.
They document a temporary target-28 PRoot integration and unfinished transitive
redistribution review. No code or binary support payload was copied. That
reference does not settle PicotronNest's execution model or licensing audit.

## Remaining P0 work

1. Resolve the complete loader/glibc/SDL2 dependency closure from reviewed,
   version-pinned sources; this ZIP does not bundle those shared libraries.
2. Define an independent app namespace, disposable home and process evidence
   contract, with no dependency on an installed PikoNest or helper app.
3. Investigate execution under a modern Android target. If a legacy-target
   diagnostic comparison becomes necessary, record it explicitly as a bounded
   experiment; it cannot satisfy product acceptance.
4. Run an owned deterministic headless probe and record PID, output, termination,
   created home files and repeat-launch behavior. Do not use `ldd` on imported
   executables as a substitute for static inspection.
5. Trace actual SDL presentation before choosing a video bridge.

No native frontend framework, PRoot requirement or final bridge protocol has
been selected. No APK or proprietary file is part of this increment.
