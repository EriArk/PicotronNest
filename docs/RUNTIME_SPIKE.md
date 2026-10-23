# P0/P1 runtime feasibility experiment

**Objective:** prove or disprove the narrowest useful claim: a legitimately obtained Raspberry Pi ARM64 Picotron build can execute and show its real desktop on Android under a project-owned host. No public build, no made-up screenshots, no premature UI platform choice.

## Sources and hypotheses

Official manual: [Picotron](https://www.lexaloffle.com/dl/docs/picotron_manual.html) (SDL2, `-home`, `-x`, normal display modes). Official [roadmap](https://www.lexaloffle.com/picotron.php?page=roadmap) lists Raspi 64-bit support. Community report of a Raspberry Pi download: [Lexaloffle BBS, March 2026](https://www.lexaloffle.com/bbs/?pid=184871).

These sources justify testing ARM64, **not** assuming the package has PICO-8's `pico8_64` filename, dependencies or SDL rendering behavior. A public FAQ still describes the older x86-64 platform set and may lag the roadmap. The owner's specific current download is authoritative for the experiment.

## Rules before starting

- Owner provides/downloads their own lawful runtime in a **git-ignored local directory**.
- Do not commit the archive, any extracted Picotron proprietary file, generated machine code copied from it, save data or user screenshots with personal information.
- Record package version, local SHA-256 and safe structural metadata only. Treat archive paths as hostile input.
- Pin hashes/versions of open-source shim, PRoot, helper libraries and packaging tooling; check their redistribution terms.
- Never overwrite a currently installed PikoNest, third-party PICO-8 wrapper, or existing Picotron data to make a test pass.

## Step 1 — Static inventory

The first reproducible implementation is `tools/inspect_runtime.py`.
See [the P0a record](P0_STATIC_INSPECTION.md) for invocation, limits and the
2026-10-23 owner-package observations. This tool performs static inspection only.

1. Identify archive type, size, exact entries and version markers **locally**; reject archive escapes, duplicates and suspicious links.
2. Identify actual executable and support data. Use `file`, `readelf -h -l -d` or equivalent read-only tools; record architecture, interpreter, NEEDED entries and likely library search paths.
3. Detect whether the distributed Pi runtime is AArch64, and which glibc / SDL2 / audio / GPU dependencies it needs.
4. Inspect available command-line flags from official docs or explicit executable help (where safe). Do not assume the PICO-8 option names.
5. Make a dependency graph and a minimal rootfs plan; determine whether direct Android native launch is impossible due to Linux/glibc linkage.

**Pass:** exact known package/ABI/dependencies or a clear incompatible-build report. No need for a UI yet.

## Step 2 — Native process proof

Before the official process test, [P0b.1](P0_EXECUTION_MODEL.md) records a
target-36 owned-fixture execution probe in real app contexts. Its results guide
the compatibility environment but do not pass this step for Picotron.

1. In an isolated app-owned directory, prepare a minimal reviewed Linux runtime environment. Start Picotron without root/Termux; `-home` points at an empty disposable data home.
2. Where supported, run a **harmless, owned** headless Lua probe using the officially documented `-x`. Check termination and captured stdout/stderr. Do not assume that a headless success proves the GUI.
3. Record PID, exit code, signal, child processes, created home files and repeat-launch behavior.
4. Stop/kill a disposable probe only under an explicit test plan; preserve unknown other processes.

**Pass:** real official Picotron process observed starting and producing meaningful deterministic evidence. An invocation of `proot` alone is not success.

## Step 3 — SDL audit

1. Trace SDL video initialization and which present paths are used by the exact binary: surface update, SDL_RenderPresent, GL swap, etc.
2. Observe frame dimensions, pitch, format, resize behavior and timing. Assume neither 128x128 nor a guaranteed constant 480x270 surface.
3. Compare to the inspected PICO-8 shim: it uses `#define FB_WIDTH 128`, `FB_HEIGHT 128` and a fixed surface copy. **Do not paste it into the new project unchanged.**
4. Observe SDL mouse/text events and audio initialization separately. Account for actual SDL ABI, OpenGL/EGL and backend dependencies.

**Pass:** one concrete supported video capture/presentation strategy with known lifetime/ownership. If one hook cannot capture frames, document and test another path.

## Step 4 — Real desktop pixels (P1)

1. Implement only a temporary Android display surface / bridge required to show actual frames.
2. Test color correctness, 480x270 normal mode, another supported mode and resizing where possible; preserve ratio with correct letterboxing.
3. Feed one mouse movement and click; observe pointer moving to the expected position, including letterboxed corners.
4. Record frame rate/latency **as measured**, CPU costs and crashes on real device.
5. Observe actual shutdown and restart. Ensure failure leaves the previous installation and home intact.

**Pass:** verifiable live Picotron desktop on a real Android device, not an injected frame. Attach only safe, legally shareable evidence.

## Investigate blockers before redesigning

| Blocker | Focused next probe |
| --- | --- |
| Wrong ELF architecture | Locate legitimate ARM64 build and confirm download |
| Missing glibc/shared libraries | Inspect NEEDED/interpreter and build minimal dependency closure |
| Renderer path not captured | SDL trace; renderer/texture/GL-based bridge prototype |
| Audio errors | Trace SDL device opens, drivers and lifecycle |
| Controller keys don't reach target | Test SDL scancodes/text input separately |
| Modern Android denies execution | Investigate packaging/execution model; no permanent silent downgrade |
| Launch works only after another app installed | Isolate dependencies with a clean package namespace |

## Required recorded evidence

- Owner-supplied package name/version, SHA-256 and ABI (never the binaries).
- Android device/model/API/ABI, host test revision, exact test commands and exit evidence.
- What was personally visually/audibly verified vs automated/ADB-observed.
- SDL path/format/dimensions and any remaining unknowns.
- Clear next gate recommendation, linked to [ROADMAP](ROADMAP.md) and [STATUS](STATUS.md).

**Release note:** A successful P0 or P1 is a lab milestone. It is not approval to publish APKs or to claim a generally compatible Android product.
