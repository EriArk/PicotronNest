# Cartridge discovery follow-up (issue #1)

Development changes after beta 3, 2026-11-10. No APK release is implied.

## Reproduction and changes

Owned host fixtures reproduced two distinct failures in the beta 3 source:
import rejected `Mixed.P64`, and discovery omitted `Mixed.P64.PnG`. Spaces,
parentheses and Unicode in lowercase local cartridge filenames already worked.
Import, export selection, library titles and cover selection now recognize the
Picotron extensions without regard to case. The original filename, extension
case and native launch path are preserved, including collision suffixes.

PICO-8 `.p8` / `.p8.png` imports now explain the format mismatch and that renaming
is not conversion. The library reports skipped PICO-8 files. Hidden imports are
rejected explicitly instead of importing a file that discovery would hide.
Unsafe path components and links remain excluded.

Native Splore cache entries keep their observed `bbs://` mapping and sandbox.
The ASCII cache-name predicate is intentionally retained: there is no evidence
that arbitrary local display names can be mapped to valid native BBS URIs.
Recognizable Picotron files that cannot be indexed there are now counted with
an actionable Splore/import explanation instead of silently disappearing.

## Validation

Linux x86_64, Temurin JDK 17.0.21+9, Android compile API 36, D8 minimum API 26:

```sh
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover -s experiments/execution-model -p test_game_library.py
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover -s experiments/execution-model -p test_project_files.py
```

Both focused checks pass. They cover ordinary names, spaces, parentheses,
Unicode, mixed case, nested folders, directory cartridges, cache URI mapping,
skip explanations, preserved startup, exact Unicode launch request transport,
unknown entries, collision preservation, unsupported formats, signature
mismatch, cancellation and existing backup/restore failure cases. Linux also
checks symlink and newline-path rejection; these filesystem fixtures are not
created on Windows. All 52 Android Java sources compile with warnings as errors
against API 36, and D8 completes for minimum API 26.

The fixtures are owned test bytes, not proof that the official runtime can open
all extension-case variants or that a PNG signature proves a valid cartridge.
No new APK/device acceptance has been recorded. ADB saw an unauthorized device;
its model/OS were not readable. The tester's exact missing filenames and import
locations remain unavailable, so this does not establish the original report's
root cause or close the full issue acceptance criteria.
