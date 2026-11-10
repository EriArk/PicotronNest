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

## Android provider failure and process-death evidence (2026-11-10)

The shared document reader previously treated pipe EOF as success without checking
an Android reliable pipe's error channel. An owned provider sent a valid cartridge
signature and then `closeWithError`; the queue incorrectly accepted those bytes.
Collection `5bf447f5-9d92-4a18-8009-41f516f81eba` reproduces that failure before the
fix. The reader now calls `ParcelFileDescriptor.checkError()` before accepting EOF.
A reported transfer failure rejects and cleans that attempt, retaining older queued
copies. This also protects folder imports and other users of the shared reader;
it cannot detect truncation that a provider does not report.

The owned API 30 / Android 11 x86-64 emulator (SELinux Enforcing), target-36 fixture,
passed seven assertion groups in collection
`d0be5256-9b0a-47e4-8f45-ce0ff4cd8419`:

- Non-seekable content-URI queueing while the runtime-store lease is held;
  attempted home application is rejected until the lease is completed.
- Reliable-provider failure after a valid prefix rejects the copy and cleans its
  staging; provider-open failure and bounded stalled-provider cancellation retain
  existing queued files. Cancellation before application leaves the job waiting.
- Explicit idle application preserves Unicode/extension case and original bytes.
- Actual `am force-stop` during provider-copy staging leaves a non-retryable copy
  for explicit discard. The copy interruption uses an owned InputStream barrier;
  the provider success/error/cancellation checks use real content URIs.
- Actual process death after claiming, before publication, requires explicit retry.
- Actual process death after publication preserves both the imported project and
  the retained review copy. A fresh process never silently replays it; discarding
  the review copy does not delete the project or create a numbered duplicate.

The runner records seven distinct host PIDs (4612, 4665, 4715, 4760, 4813, 4857,
4910), observes each barrier process before force-stop, and verifies its absence
before the next phase. These are process-death tests, not power-loss durability
tests. Test-only barriers do not alter production queue behavior.
Fixture APK SHA-256:
`fede0d4469ceda5a64d1e9aa41bf8619bc74cba08374e4fd75cd6ddf980dcf60`.

The same fixture also passed all three existing runtime-storage scenarios in
collection `596ffd2f-e872-4d52-abc1-e37845f6f6e9`: setup recreation and non-seekable
runtime import; native home write, fresh-process upgrade/native read and rollback;
unfinished-session recovery guards. Native children 5030 and 5101 exited 0, with
no surviving child from the fixture UID. All runtime/cart bytes were owned test
fixtures; official Picotron was neither included nor executed.

Run from a clean LF checkout; see [Linux build setup](LINUX_BUILD.md) for tools and
an isolated source export when a working tree contains unrelated line endings:

```sh
python3 -B experiments/execution-model/build_bridge_tests.py \
  --sdk "$ANDROID_HOME" --jdk "$JAVA_HOME" --artifacts-dir .local/queue-tests/final
adb -s emulator-5582 install -r .local/queue-tests/final/bridge-tests.apk
python3 -B experiments/execution-model/run_queue_tests.py \
  --serial emulator-5582 --artifacts-dir .local/queue-tests/final
python3 -B experiments/execution-model/run_storage_tests.py \
  --serial emulator-5582 --artifacts-dir .local/queue-tests/final
```

Reuse the existing owned fixture's debug key for replacement installs; do not
uninstall the product or erase user data. Both runners refuse physical devices,
verify source/build/installed APK identities and require fresh report tokens.
Evidence stays under `.local/queue-tests/evidence` and `.local/storage-tests/evidence`.
The queue runner force-stops only `art.picotronnest.bridgetest` on that emulator.

The three host checks for queued imports, project files and folder transfers also
pass. All production and Android fixture Java sources compile with warnings as
errors against API 36 and pass D8 at minimum API 26 as part of the fixture build.
External system-picker/provider behavior, complete management-activity lifecycle,
actual running Picotron and physical-device acceptance remain open for issue #3.
Retroid is unavailable; the owner explicitly deferred all testing until permission
is given to resume. No APK or release is published by these checks.

A private ARM64 product candidate includes this fix (same beta-3 package version;
not a release). SHA-256: `3a44aafcabb049380b3f7596144ba87e8df3e4ac070a1c24302c08303d1d8eaf`.
Its sources, payload allowlist and existing signing identity are verified; the
previous Linux candidate and published artifacts remain unchanged. It has not
been installed on Retroid. Local path:
`.local/queue-tests/candidate/PicotronNest-0.1.0-beta.3-arm64.apk`.
