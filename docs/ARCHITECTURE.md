# Proposed architecture — Android host for official Picotron

**Status: design, not implementation.** Any subsystem described here is a target boundary until an explicit probe proves it.

## System sketch

```text
Android package (one APK, one launcher icon)
|
+-- Host UI (setup, status, runtime menu, diagnostics)
+-- Archive / installation manager (SAF, validation, staged activation)
+-- Runtime session manager (start/stop/resume, process journal, recovery)
+-- Storage adapter (private Picotron -home, export/import)
+-- Input adapter (touch, mouse, IME, gamepad -> SDL semantics)
+-- Display + audio frontend (scale, frame pacing, device output)
|     ^
|     | bounded, versioned inter-process/native boundary
|     v
+-- Native runtime bridge / compatibility environment
      +-- app-owned Linux userland and loader (PRoot is a candidate)
      +-- SDL video/event/audio bridge
      +-- official USER-IMPORTED Picotron Linux/ARM64 process
      +-- separate persistent Picotron data home
```

The UI, bridge and official Picotron may execute in separate Android processes **inside one package**. That is not a second required APK. Never assume a frontend launch alone means Picotron successfully started.

## What remains official

Picotron owns its desktop, editors, files, runtime semantics and cartridges. The host does not parse/edit source projects or intercept its virtual filesystem operations except at carefully documented host integration boundaries.

The official manual describes:
- 480x270 standard screen and other supported modes; dynamically observe actual output rather than assuming a fixed surface;
- .p64 and .p64.png cartridges (not PICO-8's .p8 formats);
- SDL2 as an upstream dependency;
- `picotron -home <path>` as a way to isolate host data and the drive;
- `-x` for headless execution.

References: [Picotron manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html), [official roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap). Actual flags, ELF ABI, dependency closure and file layout for the **user's downloaded Raspberry Pi version** must be checked against that build.

## Separate platform ports

### RuntimePort

Conceptual operations (not a finalized API):

- `inspectCandidate(archive)`: bounded archive inspection; version / arch / required files; no activation.
- `prepare(candidate)`: stage legal user-owned runtime files plus reviewed free/open dependencies.
- `activate(installation)`: atomic switch after verified successful preparation.
- `start(home, args, sessionId)`: launch one real runtime process, never caller-supplied arbitrary shell.
- `observe(sessionId)`: explicit starting/running/exited/failed/unknown states backed by evidence.
- `requestExit` and `resume`: never force-kill by default; report unsupported operations honestly.

PikoNest's Android integration may inspire these boundaries, but its PICO-8-specific paths, flags, process identities and boot scripts are not reusable contracts.

### DisplayPort

- Investigate the actual SDL2 render path: window surface, software renderer, SDL textures and/or GL. Do not assume `SDL_UpdateWindowSurface` is called.
- Bridge **complete displayed frames** with explicit width, height, pixel format, stride, timestamp and ownership.
- Handle resize, alternate video modes, orientation, RGB/RGBA/BGRA conversion, frame pacing and teardown without stale frames.
- Scale without altering the original aspect ratio or silently filtering pixel art; selectable integer and fit-to-screen scaling are candidates.
- Exact input transform is inverse to video transform; black bars must not shift the pointer.
- Do not patch official Picotron bytes or depend on undocumented fixed RAM framebuffer offsets.

**PikoNest warning:** the examined PICO-8 upstream SDL shim hardcodes 128x128 and copies 16,384 pixels from a surface. That implementation is **not Picotron-compatible** as-is. Build a small video spike before designing any permanent renderer.

### InputPort

- Pointer: Android touch tap/drag/long press, physical mouse relative/absolute position, buttons, wheel; mode and click gestures adjustable.
- Text: Android IME / soft keyboard, hardware keyboards, Unicode/text composition when supported by the target SDL bridge; correctly pair key-down/key-up and modifiers.
- Controller: a remappable virtual mouse + useful keyboard shortcuts; avoid assuming that all Picotron applications use native gamepad semantics.
- Prevent stuck buttons after overlays, Android Home, rotation or process focus changes.
- Retain native gamepad input where possible; virtual-mouse mode must not swallow a game's own controls indiscriminately.

### AudioPort

- Inspect target SDL audio interfaces and format/latency; adapt actual callback/queue semantics.
- Ensure routing, continuous playback, no device-open races, resume after sleep and clean release.
- Do not label an open audio handle as user-audible sound without a device check.

### HomeStore

- Use one private, durable Picotron `-home` selected by a validated pointer, separate from extracted binaries and temporary sessions.
- Handle Picotron config, virtual drive, /desktop, /appdata and cartridge files via Picotron's own home. Paths in Picotron's virtual FS are **not Android filesystem paths**.
- Use SAF for user-visible import/export and optionally configurable external library folders. Copy or explicitly stage content URIs; never reinterpret them by string replacement into arbitrary Linux paths.
- Verify backup/migration without deleting the original. Safe app *upgrades* must preserve data; Android *uninstall* may remove private data, so offer export/backup before claiming portability.
- Archive extraction rejects traversal, symlinks outside permitted staging, duplicates/case aliases and decompression bombs; sizes and quotas must be version-appropriate, not copied blindly from PikoNest.

## Native compatibility and Android constraints

The PikoNest experiment packages a Godot-based frontend and PRoot Linux environment. Reuse is a **candidate architecture**, not a binding requirement. Inspect the legal distribution and runtime linkage of every helper. A smaller native Android frontend may be preferable after video experiments; pick through measured evidence, not framework loyalty.

Android target SDK 29+ limits executing code from writable app-private storage. PikoNest temporarily targets 28; **do not silently inherit that as a shipping solution**. Research/test acceptable native packaging/loading/execution options on current Android versions before final P4 integration. The installed user-owned Picotron must remain legally separate from bundled open-source code.

## Robustness & diagnostics

- Each runtime start has an immutable token and explicit journal, including boot/process identity where available.
- Backgrounding or losing the host Activity must not manufacture a successful exit or trigger a duplicate launch.
- Unknown process state blocks destructive cleanup and new conflicting sessions; recovery is explicit and conservative.
- Collect bounded redacted logs only, without source code, files, credentials, personal paths or purchased binary payloads.
- Preserve previous installation, configuration and saves on failed update, interrupted extract or unverified migration.

## Architecture decisions still open

| Decision | Evidence required |
| --- | --- |
| Exact Raspberry Pi package / loader | Local archive inventory and ELF/dependency report |
| Whether PRoot is necessary | Minimal launch comparison |
| SDL frame capture mechanism | Trace real renderer/present calls |
| Godot vs minimal native frontend | Prototype frame throughput, input and lifecycle |
| Runtime binary execution with modern target SDK | Test on target Android versions |
| Controller cursor defaults | Physical handheld usability tests |
| Persistent drive mapping / external folders | Real .p64 save/reopen and SAF lifecycle |

Do not freeze a bridge protocol or commit a full UI framework until P0/P1 clarify these decisions.
