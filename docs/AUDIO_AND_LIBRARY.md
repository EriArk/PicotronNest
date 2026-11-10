# Audio buffering and cartridge library

[Beta 3 is published](https://github.com/EriArk/PicotronNest/releases/tag/v0.1.0-beta.3)
as a prerelease with owner authorization. Picotron itself remains unchanged.

## Sound

Open **Picotron controls > Sound buffer**, or the same setting in management.
Changes apply immediately and persist across launches:

| Mode | Requested buffer |
| --- | --- |
| Auto | Starts at 80 ms, grows by 20 ms on an observed underrun, up to 240 ms |
| Low latency | 40 ms |
| More stable | 160 ms |

Android's hardware minimum can exceed these durations. Device and Bluetooth
latency are additional. A larger buffer can help interruptions but cannot fix
every source of distorted sound or slow emulation.

The pinned SDL dummy backend now waits against a fractional sample-count clock.
The old fallback rounded each period to milliseconds and slept after callback
work, accumulating timing error. The marked patch changes the open-source
compatibility component only. Android output primes before playback, uses an
audio-priority worker, handles one dead-track recreation, and flushes on focus
or background transitions. A long input pause resets the startup threshold.
Growing/changing the buffer pauses and primes it again: increasing capacity
alone cannot replenish an empty queue when input and output have the same rate.
Buffer settings, underruns, discards, track recreations and available native
queue-drop counters enter the allowlisted diagnostic export; paths and cart
source do not.

## Your games

The four-square button beside the hamburger opens a scrolling cover grid. It
lists local `.p64` / `.p64.png` cartridges on Picotron Drive and downloaded
cartridges from native `cache/carts`. Appdata is excluded. Scanning and image
decoding are bounded; symbolic links are not followed. No new permission is used.

A tap requests official `create_process` and waits for a matching acknowledgement.
Downloaded entries retain their `bbs://` URI and native sandbox. Existing games
remain under Picotron's normal workspace/lifecycle rules. A positive process ID
confirms creation, not successful execution of every cartridge.

An original Lua service uses the native user-startup hook. PRoot overlays
session-owned files at startup and at a reserved appdata mount point. Existing
startup contents, including their metadata header, are preserved; no official
script is patched. PRoot removes temporary mount placeholders on normal exit.
An occupied mount point or unreadable startup disables integration instead of
overwriting user files. Versions without the expected hook/API can still use
Splore; the library reports that its service is unavailable. Runtime import
remains version-independent.

## Reproduce and evidence limits

Host checks: `py -3 -B -m unittest discover -s experiments/execution-model`.
Clock checks cover fractional periods, callback work, jitter and suspend reset.
Library checks cover discovery, native cache URIs, startup preservation,
readiness, request correlation and missing entries.

Owned Android PCM fixture, without official runtime or listening acceptance:

```powershell
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5560 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B experiments/execution-model/run_audio_tests.py --serial emulator-5560
```

The Android 15 x86_64 fixture checks live mode changes, background discard and
resume with synthetic PCM and real AudioTrack. Its emulator has no audio output.

Private Retroid evidence: `.local/evidence/beta3-audio-games`.
Retroid Pocket Classic / Android 14 ran official owner-imported 0.3.0d2. The first
completed lab session received/wrote 39,887,884 PCM bytes, with zero Android
underruns, discarded bytes or track recreations, and closed cleanly. Startup
overlays were gone after exit. User games and their screenshots remain private.

A later 25-minute four-game lab session exposed 1,189 underruns even after Auto
reached 240 ms. This was not an audio pass: capacity grew without replenishing
the depleted queue. The follow-up pauses/flushes and primes the new target before
resuming. The repeated Android 15 fixture (`a71c869e-9132-4b2a-90a5-3d66818e4991`)
passed with 729,600 received bytes, 672,000 written, zero underruns/recreations,
three explicit buffer reprimes and 57,600 deliberately discarded background bytes.
This is synthetic PCM evidence, not audible or official-runtime acceptance.

The four supplied cartridges were copied privately with matching hashes. In the
Retroid lab on official 0.3.0d2:

| Cartridge | Observed interaction |
| --- | --- |
| Grid Rider | Mode/sector selection and active grid gameplay |
| Phoenix 0.80 | Player-one start, firing and score change |
| Sokofox 1.06 | Native first-run input setup, puzzle selection and fox movement |
| Stix Disruption 0.80 | Player-started story/gameplay, movement and life loss; attract demo excluded |

These are bounded smoke checks, not full-game compatibility certification. Input
was injected through Android touch/key events, not physical handheld-button testing.
The four-game session exited normally with all 122 ordered records acknowledged,
no held input and no surviving native children. Its audio result above is separate.

The product library displayed 32 existing cartridges and launched the cached
**Corre, Flavio!** into its native title screen by tapping its cover. The lab grid
also launched local cartridges. Covers and gameplay screenshots remain private.

After the reprime fix, an isolated 123-second Retroid session launched Phoenix
through the library and reached player-one gameplay (score 40 after firing).
It received/wrote 23,525,452 bytes, counted one underrun and one adaptive reprime
to 100 ms, with zero discarded bytes or recreated tracks. It exited 0 and both
native/PRoot PIDs were absent afterwards. Evidence session:
`b0098f0b-4a0a-4843-afa1-e8b9bdb56dc6`. This short session does not establish that
the reported crackling is eliminated; no one listened during the test.

The final local product APK was installed over the existing product without
clearing data; its installed SHA-256 matches
`0be3e848c28e7e72b7664c7902ceb6b6fa5821284fa9bb7310fbf80cb2d08051`.
The separately installed lab uses the same audio/library sources. The product
APK is now published with matching source/dependency archives and notices; all
12 uploaded digests and an anonymous APK download were verified. The lab APK,
private runtime, user carts and their screenshots remain unpublished.

## Owner listening follow-up — 2026-11-02

After beta 3 publication, the owner reported that Phoenix sounded good. The
conversation identifies the listening device as the Retroid used above (Retroid
Pocket Classic / Android 14, with official 0.3.0d2 in the recorded setup). This is
owner-reported listening evidence, recovered on 2026-11-10; no new device test
was performed during recovery. The report does not specify the listening duration,
output route or buffer mode, or provide a separate session ID/build hash.

This supports audible Phoenix playback on that setup. It does not close A2.6:
background/foreground and sleep/wake audio continuity still require listening.
The earlier automated session and its counters remain separately scoped above.
Listening on the reporting friend's Android phone, Bluetooth, sustained sessions
after the reprime fix and other Picotron versions remain separate acceptance checks.
