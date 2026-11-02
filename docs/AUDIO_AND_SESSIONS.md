# Audio and managed sessions

Subsequent 2026-11-02 work and actual hardware scope: [Managed workstation](MANAGED_WORKSTATION.md). Earlier pending statements below are historical; the full acceptance script is not claimed complete.

Implemented on 2026-11-02. This increment connects callback audio to Android and
removes diagnostic lifetime limits from the user-imported runtime path. It is
build-checked, with a focused PCM protocol test; audible official Picotron and
physical interruption behavior are reserved for the owner/testers.

## Audio path

The inspected 0.3.0d2 ARM64 executable imports `SDL_OpenAudioDevice`, pause,
close and audio locking functions. The observer wraps callback playback while
the pinned SDL dummy driver supplies callback timing. An SDL audio stream
converts the obtained format to 48 kHz signed little-endian 16-bit stereo.
Android `AudioTrack` owns actual playback. Picotron's audio engine is unchanged.

The native callback queues PCM in eight bounded 16 KiB slots. A separate thread
sends to an app-private abstract Unix socket, with bounded writes and oldest
block dropping under backpressure. The Android receiver authenticates the UID
and matches the sender PID against the live desktop frame peer before playback.
The PNA1 header fixes version, sample rate, channel count, bit depth and maximum
block size; each following block has a little-endian unsigned byte count, bounded
to 16 KiB and aligned to a stereo sample frame. Truncation and invalid lengths
fail the stream. No samples or proprietary files are saved as test artifacts.

Playback follows Android audio focus: transient ducking reduces volume, other
focus loss pauses/flushes, and returning focus resumes playback. Backgrounding
the Activity mutes/drains incoming audio instead of accumulating delayed sound.
A dead AudioTrack is recreated once per block; route stalls have a bounded
write deadline. Reopening the SDL playback device accepts a new connection.
Managed-session JSON records connection count, received/written bytes and errors.
These counters are transport evidence, not proof of audible output.

The adapter currently supports one callback playback device. Queued playback
and a second simultaneous playback device fail explicitly. Capture passes through
to SDL and has no Android microphone bridge. Device routing, sample fidelity,
latency, Bluetooth changes and actual interruption recovery require tester checks.

## Normal launch and lifetime

- The icon opens runtime setup, or reconnects to a running managed workstation.
  Setup imports the user's pinned compatible ZIP, then **Start Picotron** launches
  the original workstation with persistent native home and shared session controls.
- Managed sessions skip execution fixtures and headless diagnostic runs. They
  have no automatic SDL quit or host/system wall-clock timeout. The explicit
  diagnostic path retains its timeouts and quota.
- A foreground service owns an ongoing return-to-session notification until
  process completion. It does not restart a killed guest. Background survival and
  OEM power-management behavior are not established by the service declaration.
- Managed frame reads tolerate idle/sleep gaps; explicit socket shutdown still
  cancels the reader. Session menu exit continues to require process/receipt
  evidence. The latest 16 KiB of process output is retained for final receipts.
- The compatibility environment is cached by APK hash and its fixed files are
  verified on reuse. This avoids duplicating the full guest tree each launch.
  Runtime objects and persistent home remain separate. Old caches and small
  per-session reports are retained; automatic disk reclamation is not implemented.
- The diagnostic 16-session quota does not apply to managed sessions. Storage
  locks and unfinished-session markers still prevent unsafe reuse after host
  loss. A user-facing recovery workflow remains to be implemented.

## Validation scope

On the Windows development host:

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p test_audio.py
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
py -3 -B experiments/execution-model/build_bridge_tests.py
```

The focused test checks fragmented PCM input, payload preservation, clean EOF,
truncated records, wrong format and invalid/overflow lengths, including
`0xffffffff` (which must not be confused with clean EOF). The production ARM64
observer and both Android APKs compile. Build metadata includes the new native
audio source hash. APKs remain local and contain no official runtime.

Built artifacts (SHA-256):

| Local artifact | SHA-256 |
| --- | --- |
| `.local/execution-model/artifacts/picotronnest-execution-lab.apk` | `443464ada4857f655c24c5e21d9641bdd056be0a0cdd960476aa2b2bf9358829` |
| `.local/bridge-tests/artifacts/bridge-tests.apk` | `cf411fa2b5ffd7e139b1ec8605515e33ece48b1ae3a10344f419b7fbe2fbea50` |

Build metadata is retained beside each APK; build output is in the corresponding
`.local/execution-model/audio-build.log` and `.local/bridge-tests/audio-build.log`.

No emulator scenario suite or hardware session was run in this increment. There
is no new screenshot or listening result. At the owner's request, deep and physical
testing is delegated to the owner/testers; implementation increments use builds
and focused checks, with defects fixed as reports arrive.

Next: implement user-facing input configuration, project transfer/backup and
session recovery, then assemble the private functional candidate and tester guide.
