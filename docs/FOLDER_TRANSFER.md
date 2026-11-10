# Cartridge folder transfer

Issue: [#6](https://github.com/EriArk/PicotronNest/issues/6). Development build only;
not part of the published beta 3 APK.

Under **Settings and projects > Projects and backups**, **Import cartridge folder**
opens Android's folder picker. **Export cartridge folder** first selects a source
folder on Picotron Drive, then opens the Android destination-folder picker. Both
operations require a closed Picotron session and hold the existing idle-home lease.
Single-cartridge transfer, queued imports and home backup remain available.

## Contents and conflict policy

Only signature-checked `.p64` and `.p64.png` cartridges are transferred, preserving
case, spaces, Unicode names and the relative folder hierarchy. Other files,
including loose assets, are skipped. This is collection transfer, not an arbitrary
project-directory copier. The confirmation explains that distinction before selection.
Runtime objects and extracted runtime trees are outside the export source area;
there is no new runtime-bundling or ZIP export path.

Every operation creates a separate folder with a random UUID suffix. Its root
name prefix is limited to 160 UTF-8 bytes so repeated transfers and long Unicode
names fit provider/filesystem limits; inner names are never shortened. Existing
folders are not merged, overwritten or deleted. Case-insensitive duplicate names
within one source directory and repeated provider document IDs are skipped.
Unsafe names, symlinks, invalid signatures and unsupported filesystem types are
rejected. A provider that changes a requested export name/type causes that entry
to fail; only the newly created entry is eligible for cleanup.

Bounds apply to the whole batch: 4096 entries including directories and skipped
files, 32 directory levels, 128 MiB of bytes read, and the existing safe-path length
limit. Input bytes count even when a cartridge later fails validation. There are
no broad storage permissions or persistent URI grants.

## Cancellation and partial results

Import copies into private staging first, validates cartridges, syncs files and
directories, then atomically activates one new folder under `drive/desktop`.
Cancellation or a batch limit before activation installs nothing. Recoverable
per-entry read/validation failures permit other valid cartridges to be imported;
the report identifies failures and the actual new folder.

Export also prepares a validated private snapshot before writing to SAF. Completed
files can remain in the new destination folder after cancellation or an error.
An incomplete newly created file is deleted when possible; failed cleanup produces
an explicit warning. Reports count completed cartridges, skipped entries and failed
operations (a directory/listing failure counts once, not as guessed child counts).
Stopped batches say that unvisited entries are not counted. Details are limited to
six messages to keep large failure batches bounded.

Sources and pre-existing destinations are preserved. Work runs off the UI thread.
Cancellation is checked during traversal/copy, with the existing cancellable,
time-bounded Android document reader. Provider query/create/delete/write calls can
still block inside the provider; cancellation cannot guarantee immediate return
from those calls. No background job is automatically resumed after process death.
An interrupted process may leave private staging or a partially completed export;
retry creates a separate folder rather than overwriting an uncertain result.

## Reproducible checks and evidence

```sh
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover \
  -s experiments/execution-model -p test_folder_transfer.py
```

Six host groups pass on Linux/JDK 17: nested and Unicode names, signature checks,
case collisions, unsupported/traversal/control-character rejection; preserved export
bytes and existing destinations; source/write/cleanup failures, cancellation and
symlinks; entry/depth/byte limits and repeated document IDs; a 500-cartridge collection;
long Unicode folder roots. The fixture uses owned cartridge headers, not purchased
Picotron contents.

Build the Java-only fixture as in [SCREENSHOTS.md](SCREENSHOTS.md), then run:

```sh
python3 -B experiments/execution-model/run_screenshot_tests.py \
  --adb /path/to/adb --serial emulator-5582 --mode folder
```

Collection `d6dbe5cc-e2fa-4771-80b5-4f758f440ac3` passes on a dedicated Android 11/API 30
x86_64 emulator (`sdk_gphone_x86_64`). A real owned `DocumentsProvider` exercises the
production SAF adapter: nested Unicode round trip, exact file bytes, skipped inputs,
pre-existing destinations, denied writes, provider renaming/cleanup and cancellation.
All 57 production Java sources compile/dex against API 36 (minimum API 26), and 18
Android test sources compile. Existing single-cartridge and queued-import host
regressions pass, as do the two candidate-manifest checks.

The real system picker, removable/cloud providers, process death during transfer,
physical-device UI/controller navigation and official Picotron opening the imported
collection remain acceptance work. The fixture calls the transfer engine directly;
it does not prove the Activity flow or a running official session. Test APKs and
fresh collection reports remain private under `.local/screenshot-tests`.
