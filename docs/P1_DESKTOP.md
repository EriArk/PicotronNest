# P1 desktop experiments

## P1a — Presentation tracing, 2026-10-23

The official user-imported Picotron 0.3.0d2 boots its desktop under the existing
target-36 lab on Retroid Pocket Classic, Android 14/API 34, ARM64. A locally
captured original frame was visually inspected: the desktop, Picotron Drive
and readme.txt are visible. This stage captures pixels privately; it does not
yet display them in the Android Activity.

An original `LD_PRELOAD` observer records calls at the official SDL API boundary.
The purchased executable/data and upstream SDL library remain unchanged.
`SDL_VIDEODRIVER=dummy`, `SDL_AUDIODRIVER=dummy` and `SDL_RENDER_DRIVER=software`
select the existing source-built SDL backends. No accelerated-renderer flags
are rewritten. No PikoNest shim code is copied.

Observed contract:

| Boundary | Observation |
| --- | --- |
| Window | 1024x768, flags 4101 |
| Renderer | Requested flags 4; SDL reports `software` |
| Texture | 480x270, ARGB8888, streaming access 1 |
| Valid texture update | Pitch 1920 bytes, return 0 |
| Presentation | External `SDL_UpdateTexture` and `SDL_RenderPresent` |
| Window surface calls | No external `SDL_GetWindowSurface` or `SDL_UpdateWindowSurface` calls in the recorded session |
| Capture | `SDL_RenderReadPixels` before present, full 1024x768 output, RGBA32, packed stride 4096 |
| Ownership | Observer-owned copy; renderer storage is never retained or modified |

The first pre-window update/present uses an invalid renderer; subsequent real
window/renderer creation succeeds. SDL error strings can retain that earlier
error after a successful call: return values, valid dimensions and actual pixels
are the evidence. Joystick/controller initialization reports that support was
not compiled into this deliberately minimal SDL. Input/audio acceptance is open.

The observer captures frames 60 and 180 into the same bounded private file,
atomically replacing it. The four little-endian 32-bit header fields are magic
`0x504e4631`, width, height and sequence, followed by packed RGBA pixels. Each
dimension is bounded to 2048. The collector rejects invalid headers, oversize
dimensions or mismatched payload length; it saves the frame only under ignored
local evidence. This is a diagnostic format, not a frozen product protocol.

After eight seconds the observer delivers one SDL_QUIT through the real poll
boundary. Picotron exits normally with code 0. Android timeout remains a separate
15-second failure bound; a timeout is not accepted as graceful shutdown. The
collector checks real process identity, presentation/capture markers, shutdown,
installed APK hash and a host-only final UID census.

### Reproduction and provenance

Preserve a verified `--headless` APK and its sibling `build.json` outside the
artifact output directory. For example, the P0c base is retained privately at
`.local/desktop/base/`, SHA-256
`e677363c51531aeafd041dbcfbac006698d13cc51299a90c469e033111dbfcd2`.

```powershell
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
adb -s ARM64_SERIAL install -r .local/execution-model/artifacts/picotronnest-execution-lab.apk
adb -s ARM64_SERIAL shell am start -n art.picotronnest.executionlab/.MainActivity
py -3 -B experiments/execution-model/check_report.py --serial ARM64_SERIAL
```

The separately provisioned owner import from P0c is retained across this update.
New experiments use a separate `desktop-probes` journal and fresh roots/homes.
The prior Bionic, static guest and two official headless cases also run.

The preparation script verifies the complete base APK hash and the current
headless build-input hashes before reusing its unchanged libraries and notices.
New observer/launcher code is compiled against the pinned Linux sysroot/SDL
headers, with warnings as errors and a checked DT_NEEDED closure. The new glibc
launcher includes both CRT prologue and epilogue objects. An initial missing
epilogue caused SIGILL before exec; that failed session was retained and not
counted as evidence. A clean `--headless` build remains the rebuild path for the
base; reuse is not an arbitrary downloaded bootstrap.

### Evidence

Final P1a APK SHA-256: `75c0fab0766b6b07218e1e33f5f2e74670961f637879124459e15f5cea4ef131`.
Private session report: `4e6943cb-ac12-442c-9f6a-12ecb6cb621b.json` under
`.local/execution-model/evidence/`, with a separately validated `.rgba` frame.
The earlier exploratory frame was captured at sequence 180; it is private local
evidence, not a redistributed Picotron asset. Focused host validation includes
34 tests: 9 archive and 25 lab checks.

### Next

Receive live complete frames in the Android Activity, preserving color channels
and aspect ratio. Then route a correctly transformed pointer event through SDL
and verify a real desktop action and repeat launch. Broad physical-input,
audio, alternate-mode and enforcing ARM64 acceptance remain open.

## P1b.1 — Android frame display, 2026-10-23

Lab version 0.0.5 streams complete RGBA frames through one abstract Unix socket
named with the session UUID. Android checks the peer UID and records its real
PID; the collector requires that PID to equal the observed Picotron PID. There
are no network permissions or external frame files in the live path. Native
sends have a one-second timeout; partial sends fail the stream. Android validates
dimensions, exact payload length and increasing sequence numbers, with a
five-second receive timeout. Diagnostic snapshots from P1a remain separate.

The native sender samples at most once per 100 ms. Java reuses bounded byte/int
buffers and one Bitmap per size; the synchronized store prevents mutation while
Canvas draws. There is no accumulated UI-frame queue. RGBA is explicitly converted
to Java ARGB; nearest-neighbor drawing fits the full captured window without
stretching. Picotron's own letterboxing remains part of that frame. The last
frame is visibly labeled as ended after the process closes its stream.

Retroid result: **67 frames received**, last sequence **467**, sequence **467**
drawn by Android, peer/official PID **20623**, 1024x768 output. The boot sequence
and the actual desktop were observed in private ADB screenshots of the lab;
the desktop screenshot was taken after exit and is labeled accordingly.
The process exited 0 and the final app-UID census contained only the host.

APK SHA-256: `624dbb0b058b58a79479d7a6c2edd31109f05c1f3dd8185691788cae3c0a9b47`.
Private evidence: `a13b92c8-56c8-411e-ac0b-f6ccb1a7f172.json`.
A subsequent cold launch also passed process/stream collection:
`86c572b3-8621-4157-8671-5f5ca45bf88d.json`. Its screenshot showed a different
foreground application and is explicitly excluded from Picotron visual proof.

Host Java checks cover red/blue/alpha channel order, portrait/landscape aspect
fit, stale sequence rejection and invalid dimensions. Pointer, text, audio,
alternate modes and physical-owner acceptance remain open.

## P1b.2 — Pointer transport, host-tested only, 2026-10-23

Lab version 0.0.6 implements the minimal single-touch left-button path. **It was
not installed or tested on Retroid.** The owner requested continued development
without further screen tests while the device was occupied. The verified
0.0.5 display evidence above does not prove this new input path.

Android applies the inverse of its full-frame fit transform. A new touch in
the outer letterbox is ignored; captured drag and release clamp to the frame
edges. Picotron's internal letterboxing remains part of its SDL window. Shared
release logic handles touch-up/cancel, additional fingers, focus loss, view
detachment and Activity pause. Disconnect clears local capture without claiming
that a release reached a process that has already closed its socket.

A separate writer sends 20-byte packets: five little-endian 32-bit fields for
magic `0x504e4931`, sequence, window x/y and left-button state (0 or 1). The
128-entry queue keeps socket IO off the UI thread; overflow is recorded as a
failure and queues a release. Socket closure unblocks a pending writer. The
native reader assembles partial packets without blocking, checks sequence and
bounds, exposes mouse state and returns SDL motion/button events through
`SDL_PollEvent`. SDL cursor warps update the adapter's state. This is an original,
replaceable diagnostic adapter, not a change to the official runtime.

The collector requires equal Java/native packet counts, paired down/up counters,
no held button and no transport/protocol error. Zero-input sessions are allowed
for display diagnostics and are explicitly **not interaction acceptance**.
The current session requests SDL_QUIT after 20 seconds, ahead of further input;
the failure bounds are 30 seconds at the system level plus two seconds kill
grace, and 35 seconds in the host. The eight-second results above belong to the
earlier versions and have not been relabeled as tests of this build.

Local validation: **37 tests pass** (9 archive and 28 lab checks). Java host
checks exercise inverse mapping, outer bars, captured edges, ordered press/drag/
release and idempotent release. Report tests reject lost input, held buttons,
wrong frame peers and missing draw evidence. Native/Java compilation and APK
packaging checks pass. No extra third-party dependency was added.

Built-only APK SHA-256:
`6e0cfd88a26755909ca9d4e50149f2a1dc9ac4eefca9dd79e897290f855884df`.
Artifact and build metadata remain private under `.local/execution-model/artifacts/`.
There is no device session report for this version.

Next: on available Retroid hardware, verify real click/drag behavior, coordinates
near displayed edges, focus loss/re-entry and clean exit with matching counters.
Native polling versus state-sampling behavior still needs runtime observation.
Double-click semantics, secondary click, wheel, mouse hardware, keyboard/IME,
controller mapping and sound remain outside this minimal pointer increment.
