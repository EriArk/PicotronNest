# Product vision

## In one sentence

**PicotronNest makes the real Picotron fantasy workstation feel at home on Android — especially gaming handhelds — without replacing its desktop, apps or creative tools.**

## Intended experience

Install one ordinary Android APK; open it; select a legitimately purchased, compatible Picotron build; let the app prepare the runtime; arrive at the **actual Picotron desktop**. Work, draw, code, create music, browse files, run carts and return later to the same persistent Picotron environment.

A smartphone/tablet can use touch plus a soft keyboard; a handheld can use its controls as a mouse and keyboard substitute; connected keyboards/mice should work naturally. The wrapper should mostly disappear once Picotron is running.

## Product boundaries

- Android first. One installable APK and one launcher icon. No required Termux, root, emulator frontend, second helper APK or manually installed Linux distribution.
- Run an unmodified, official **user-provided** Picotron build when technically feasible. No bundled proprietary executable, assets, SDK or game files.
- The **Picotron desktop itself is the application**. Do not recreate Picotron's editors, file manager, window manager or cartridge library in native Android.
- This is not PikoNest 2.0. PikoNest's Play/Workshop UI, PICO-8 cartridge parser and 128x128 rendering are not project requirements.
- No fake compatibility engine, no replacement Lua implementation and no unsupported promise that PICO-8 carts run directly.
- Native Android UI is for first-run setup, runtime controls, permissions, recovery, diagnostics and a small contextual overlay when necessary.

## Experience priorities

1. **Authenticity:** original Picotron rendering and behavior, not a replica.
2. **Usability:** accessible mouse, typing, pointer precision, small-screen ergonomics and configurable controls.
3. **Persistence:** projects, settings, downloaded apps and saved data survive normal app restarts and upgrades.
4. **Reliability:** predictable launch, suspend/resume, exit, sound, failures and safe recovery.
5. **Portability:** multiple Android display shapes, controllers, GPUs and versions; don't code for one Retroid.
6. **Minimalism:** add host functionality only where Android integration truly needs it.

## Version 1 target

A user can import their compatible official Picotron runtime, launch to the desktop, use the code and graphics editors with suitable input, save a .p64 project, quit, relaunch and open it again. Sound works. Normal app switching and long-lived sessions do not lose projects. A bundled open-source bridge is allowed after provenance/license review; purchased Picotron itself is never shipped.

## Later, not the initial milestone

Convenience import/export via Android's document picker; direct .p64/.p64.png association; sharing; polished controller profiles; optional desktop shortcuts; performance tuning and more device testing. External launcher integration is optional and must not compromise the native Picotron experience.

## Non-goals

New game editors, a separate Android desktop shell, Picotron file-format conversion, cloud services, accounts, Game Store, emulation of an entire PC or Android modification/root.

The primary proof is not an attractive settings screen: it is a **real Picotron desktop with working input, audio and persistent files on an Android device**.
