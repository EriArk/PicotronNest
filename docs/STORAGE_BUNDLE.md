# Runtime import and persistent-home laboratory bundle

2026-11-02. The Android lab now has optional runtime setup, atomic activation,
retained rollback and a separate persistent home. Host/Android tests pass.
Real Picotron save/reopen through this binding remains NOT TESTED; Retroid was
not touched. The [functional beta gate](BETA_READINESS.md) remains open.

## Implemented contract

Open the desktop lab with `--ez storage_controls true`. The shared setup screen
uses Android `ACTION_OPEN_DOCUMENT` and ContentResolver. A `content://` URI is
never converted to a Linux path. No broad storage permission or retained document
grant is required. The production contract accepts only the inspected Picotron
0.3.0d2 Raspberry Pi ARM64 package, directly or inside the owner's combined ZIP.
Package and executable/data SHA-256 hashes and sizes must match
`runtime-contract.json`. Other versions are rejected, not assumed compatible.

Setup starts MainActivity with `managed_runtime` and `session_controls`. The
store lease covers preparation and observed process completion. Headless probes
retain disposable homes; only the desktop command binds the durable directory
to guest `/home`, preserving the existing official `picotron -home /home` command.
The dependency closure and bounded watchdog are unchanged. Existing developer
provisioning remains available for older probes. Start is disabled on x86_64.
This is still a bounded development session, not the final long-lived launcher.

Private layout under `files/runtime-store`:

| Path | Purpose |
| --- | --- |
| `objects/<package hash>/` | Verified runtime files; installed objects are never overwritten by the store |
| `staging/<UUID>/` | Source ZIP, optional nested package and candidate files |
| `active` | Versioned bounded active/previous package record |
| `home/` | Durable native home, separate from runtime versions and probes |
| `store.lock` | Cross-process import/rollback/session exclusion |
| `session.pending` | Conservative unfinished-session marker |

Files and candidate directories are synced before atomic object move and active
record replacement. Android uses real directory fsync. Failures before activation
preserve the old pointer; a failure after the switch can leave the fully verified
new version selected. Reopen setup to inspect actual state. Previous objects are
retained. Rollback is tested with two owned contracts; only one official contract
is enabled today.

Runtime replacement never traverses or deletes home contents. A missing/invalid
home with an active runtime is rejected. The trusted Android app-private parent
alias is resolved once; links at/below the store boundary are rejected. Successful
imports remove only their own known scratch files. Failed attempts are retained,
with four staging attempts allowed before inspection is required. No old runtime,
probe tree or user project is automatically cleaned. App uninstall removes
private data; backup/export is not implemented.

Before launch the lease writes a synced pending marker. Closing the lease alone
does not clear it: only the caller's observed completion path does. A fresh host
with a pending marker shows recovery required and blocks launch/import/rollback.
Automatic process reconciliation and a user recovery workflow remain future work;
there is no reset/delete-home shortcut.

## Input bounds

ZIP32, single disk, at most 4096 entries, 256 MiB source, 128 MiB member and
512 MiB declared aggregate expansion. Inventory rejects traversal, absolute or
ambiguous paths, duplicate/case-aliased names, file/directory conflicts, symlinks,
special entries, encryption, ZIP64 and unsupported compression. Ambiguous legacy
non-ASCII filename encoding is rejected. Local names/flags/methods must match the
central inventory. Required streams are size/CRC checked to EOF and hash checked.
Extraction destinations are fixed host-owned paths; unselected outer payloads
are not extracted or executed.

The Android descriptor reader polls for cancellation, with a five-second read
idle bound and 120-second request cancellation deadline. Provider opening receives
CancellationSignal. An uncooperative provider's Binder call cannot be forcibly
terminated by this in-process code. A cancelled/failed copy retains its attempt.

## Evidence

Owned `PicotronNest_Mouse_API35`, Android 15/API 35 x86_64, SELinux Enforcing,
target SDK 36. Both APK source manifests match the validated tree.

| Artifact | SHA-256 |
| --- | --- |
| Owned bridge/storage APK | `ea9068058ae2cfc25b25f9bd067c9411736e4a3db7c180550812b0c4cc20a81b` |
| Official-runtime lab APK, build only | `a86b77b0dcb133c38e831243979f9b9a45541fb953db77c5f15572e548d81e49` |

39 lab host tests and nine archive tests pass. The shared store suite covers
nested/direct import, upgrade, repeat import, rollback, home preservation,
exclusion, five interruption points, cancel/read failure, unfinished sessions,
corruption, unsafe ZIP paths and symlink entries. Windows tests do not simulate
directory fsync; the same suite also runs on Android's actual filesystem.

Storage collection `da7c1985-a99d-4720-97ad-788b8a5c5233` passes three phases in
distinct host processes. The setup issues the picker action (intercepted by the
test), imports a real non-seekable content provider through its result handler,
survives recreation and cancels a stalled provider without replacing the runtime.
Native PID 3698 writes/syncs `owned-home-proof.txt`, exit 0. A fresh host upgrades
the runtime, native PID 3760 reads the retained bytes, exit 0, and the shared
rollback button restores the prior runtime. These are owned bytes, not a valid
Picotron cartridge. The third host rejects launch/import/rollback after an
unfinished lease, preserves the home and starts no native child. Numeric UID
censuses confirm no surviving children in all phases.

All 17 input/lifecycle scenarios pass on the same APK in collection
`cb490319-2633-40d8-95cf-890f3c64fbf9`. The owner-provided archive also passes the
new Java importer on Windows, with exact package/file hashes. Private evidence:
`.local/storage-tests/owner/d5e8add3-f2fe-4de0-8ac3-dbea53f6d4cc/`. The official
executable was not launched, bundled or committed.

The unedited `write.png` was inspected and shown in chat: owned-fixture label,
verified fixture version 1 and shared setup controls. Start is disabled because
the emulator is x86_64. This is neither a Picotron desktop screenshot nor manual
document-picker acceptance. Reports/screenshots remain under ignored
`.local/storage-tests/evidence/<UUID>/`.

Retained failed evidence: `f4794ce8-2968-4e95-91c5-2d43d5109f02` exposed the Android
parent alias; `28f89bbb-fc54-4f08-8cf5-4b6a2c1df864` exposed a test intent filter
missing MIME/category matching. `d1803d4e-f2e1-4a44-a0b1-7636069b23ba` encountered
the leftover picker task and was rejected for stale evidence. Tests now use the
correct filter and a fresh owned Activity task. No failed report counts as PASS.

## Reproduction and next step

```powershell
py -3 -B -m unittest discover -s experiments/execution-model -p 'test_*.py'
py -3 -B -m unittest discover -s tools -p 'test_*.py'
py -3 -B experiments/execution-model/build_bridge_tests.py
adb -s emulator-5554 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B -u experiments/execution-model/run_storage_tests.py --serial emulator-5554
py -3 -B -u experiments/execution-model/run_bridge_tests.py --serial emulator-5554
py -3 -B experiments/execution-model/build.py --desktop-base .local/desktop/base/picotronnest-execution-lab.apk
```

Real desktop/home binding, `.p64` save/reopen, official upgrades and manual SAF
selection remain unaccepted. Project import/export/backup, reviewed recovery,
additional runtime contracts, long sessions, input settings and audio remain
open. Next: audio transport and interruption/format tests together; actual
audibility and official SDL behavior remain separate hardware acceptance.
