# First-run setup and version-flexible runtime import

2026-11-02. Implemented for local **0.1.0-beta.3**, code **1003**.
This is not a published release. The public beta 2 keeps its pinned importer.

## User journey

The native host wizard has three scrollable steps, with a bounded content width
for tablets and larger text/buttons for phones:

1. **Welcome:** real Picotron desktop, tools and games; bring your own legally
   obtained copy; no root, Termux or helper APK.
2. **Bring your Picotron:** choose the Linux / Raspberry Pi 64-bit ARM64 ZIP
   through Android's document picker, or open Lexaloffle's Picotron page.
   It explains incompatible platforms and that a static check is not a promise
   that every old/future build will run.
3. **Ready:** menu, keyboard, native Splore, saving and backup reminders;
   **Start Picotron** is explicit. Import alone does not launch the runtime.

The step and unfinished onboarding state survive Activity/process recreation.
Existing installations skip the wizard and retain normal icon-to-desktop behavior.
Interrupted sessions keep the recovery screen. Management keeps runtime replacement,
rollback, input settings, projects/backups and diagnostics accessible.
Changing runtime asks the user to back up: the host preserves home, but official
Picotron itself may change project files between versions. No Picotron preferences
or first-run desktop state are patched or silently skipped by this wizard.

## Admission and preservation

- No fixed Picotron version or archive-name allowlist for new imports. Locate one
  `picotron` / `picotron.dat` pair in the same archive directory, or in one level
  of nested ZIPs. Multiple candidate pairs are rejected with a request to select
  one package directly. Archive names are lookup keys, never filesystem paths.
- Bounded ZIP inventory rejects traversal, duplicate/colliding paths, links,
  unsupported entry formats and excess expansion. The outer archive cap is
  256 MiB; entries/nested archives are capped at 128 MiB; at most 16 nested ZIPs
  are inspected. The executable cap is 32 MiB.
- The executable must be ELF64, little-endian AArch64, with bounded load/dynamic
  tables, the supported Linux loader and the SDL2 dependency. Every declared
  dependency must be supplied by the packaged guest manifest. A conservative
  GLIBC requirement scan rejects versions beyond the guest's current 2.36.
  These are host ABI constraints, not Picotron release-number restrictions.
- Static admission does not establish authenticity or runtime compatibility.
  Users must obtain their runtime from their legitimate purchase. Import does not
  execute archive contents. Only the executable/data pair is copied.
- Per-object size/SHA-256 metadata is persisted and synced before atomic activation.
  Hashes identify immutable objects and detect subsequent corruption; they no
  longer serve as a new-version allowlist. Legacy beta objects without metadata
  retain their original known contract, without rewriting files or the home.
- The previous selection remains available for rollback. Failure/cancellation
  before activation preserves the active selection; an interruption around atomic
  activation leaves either the complete old or complete new selection. The home
  is never migrated, reset or deleted. Retained incomplete crash attempts remain
  bounded. Handled errors/cancellation clean only the known current staging files
  so ordinary retries do not exhaust that quota.
- Managed guest directories include both APK hash and runtime identity. Switching
  runtimes cannot accidentally reuse the previous version's staged executable.
  Interrupted environment retention uses the same identity.

## Validation

Host checks: **43 execution-model tests and 13 tools tests passed**. Owned ELF/ZIP
fixtures cover two unlisted package identities, nested/direct import, persisted
metadata after reopening, rollback/home preservation, rejected architecture and
missing libraries, ELF bounds, newer GLIBC, ambiguous packages and corrupt metadata.
The legacy-object test upgrades from a store without per-object metadata to the
flexible reader. More than four handled cancellations/bad ZIP attempts can be
followed by a successful import. Existing atomic activation, lease, interrupted
session, unsafe ZIP and project-transfer tests also pass.

The owner's private 0.3.0d2 ARM64 ZIP was imported and reopened on the host with
**no pinned contract supplied**, proving that the compatibility path accepts the
known real package. The archive and extracted files remain ignored/private.
No other official Picotron release has been supplied or executed for this change.

Android 15/API 35 x86_64 emulator, SELinux Enforcing, target 36: the final
`write`, `read` and `recovery` phases all passed in separate host processes.
Collection: `.local/storage-tests/evidence/eb709959-3003-4390-9a8d-315c784621d7/`.
The native owned home-check children exited 0 (PIDs 5367 and 5436); no surviving
child remained. The shared wizard passed picker/URI import, explicit launch and
recreation on the import/ready steps. Three portrait screenshots were reviewed;
system-bar contrast/background also survives recreation. This is fixture UI and
storage evidence, not official Picotron execution or broad layout acceptance.
An earlier emulator attempt timed out during boot, and another at the native
child deadline under high I/O wait. The final collection passed unchanged timing
gates after startup settled.

The signed product APK built successfully:
`.local/release/candidate/PicotronNest-0.1.0-beta.3-arm64.apk`, SHA-256
`d7c7797a5a15993be6414faf55296260617dfc967c68e6776f00b733e8c5189a`.
Its build manifest records the exact source hashes. It has not been installed on
hardware or published.

Reproduce owned Android checks after the emulator reports boot completion:

```powershell
py -3 -B experiments/execution-model/build_bridge_tests.py --abi x86_64
adb -s emulator-5560 install -r .local/bridge-tests/artifacts/bridge-tests.apk
py -3 -B experiments/execution-model/run_storage_tests.py --serial emulator-5560
```

Owned instrumentation substitutes fixture bytes and an ABI-matched native
home-check child; it never claims to execute Picotron.

The initial Retroid fixture run completed its storage/native-child checks but
failed while collecting a screenshot because UI Automation was already occupied.
That collection is not counted as a passing Android suite. The owner confirmed
the handheld is busy; no further device operations or product install were made.

## Remaining acceptance

Run the exact beta 3 product on an available ARM64 device: clean wizard/import,
existing beta 2 upgrade without data reset, original desktop/save/reopen, runtime
replacement/rollback and interrupted-session recovery. Test any additional official
versions individually when their legally obtained ARM64 packages are available.
Published beta 2 device evidence does not establish beta 3 acceptance. Publishing
beta 3 requires separate owner authorization.
