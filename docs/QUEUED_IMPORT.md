# Cartridge import during a session (issue #3)

Development behavior after beta 3, 2026-11-10. No APK release is implied.

## Workflow

The floating controls include **Import cartridge (queue)**, which opens Android's
document picker. The existing **Projects and backups > Import cartridge** action
uses the same flow. Choose a `.p64` or `.p64.png`; the host checks the filename,
bounded size and format signature, then keeps a private copy outside Picotron home.
No persistent permission to the provider is needed after copying.

You can return to the running workstation. To install queued cartridges, save and
quit Picotron, open **Settings and projects > Projects and backups**, then tap
**Finish queued imports**. Start Picotron and reopen **Your games** to see them.
This is an explicit queued-import fallback: it does not claim live filesystem
refresh or safe writes into files used by the official runtime. Choosing an
import while idle also queues it until **Finish queued imports** is pressed.

**Review queued imports** lists waiting and interrupted copies. A failed or
interrupted application is never automatically replayed, because it may already
have published a file. Check Picotron Drive first; explicitly queue another
attempt or discard only the retained queued copy. Collisions create a numbered
copy, preserving the existing file and extension case. A provider-copy interrupted
before activation cannot be retried from potentially incomplete bytes: select
the original again. Cancelling the picker does not create a job.

## Safety boundary

The queue has its own cross-process lock, canonical-path and regular-file checks,
and limits of 32 retained items and 128 MiB of queued payloads. One bounded copy
can temporarily add up to 128 MiB while quota validation completes. A crash during
that copy leaves an incomplete item requiring review. Handled cancellation or
provider/signature failure cleans only that attempt's known staging file.

Applying the queue retains the existing `RuntimeStore.withHome` lease and idle
process inspection. The active runtime or an unfinished-session marker still
blocks home writes. Each job is atomically marked for review before publication;
a restart cannot silently replay it. Existing home files are never replaced.
The import result distinguishes confirmed imports, waiting items and items
requiring review. Queue copies are outside home backups; retain your originals.

## Validation and limits

Linux x86_64, Temurin JDK 17.0.21+9:

```sh
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover -s experiments/execution-model -p test_pending_cartridges.py
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover -s experiments/execution-model -p test_project_files.py
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover -s experiments/execution-model -p test_game_library.py
```

All three checks pass. Owned fixtures cover queueing while a runtime-store lease
is held, refusal to apply until idle, original preservation, Unicode/case/collision
handling, invalid formats, provider failure, cancellation, incomplete provider
copies, faults before/after publication, fresh queue-instance recovery without
replay, explicit retry/discard, locking and count/byte limits. Signature admission
is not proof that a PNG contains a valid native cartridge.

All 53 production Java sources compile with warnings as errors against API 36
and pass D8 at minimum API 26; Android test sources compile. No new Android SAF,
lifecycle or official-runtime device acceptance is claimed. The physical device
remains ADB-unauthorized. An emulator belonging to another project was observed
but not used. Full UI/device checks remain open for issue #3.
