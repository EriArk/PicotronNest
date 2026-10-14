# Owner runtime archive inventory — 2026-10-14

**Inspection only. The proprietary archive is not stored in this repository.** The source was a user-provided combined `Pico8_Picotron.zip` inspected in a temporary ChatGPT workspace. Do not claim this file is present in a Codex workspace or attached to a GitHub issue.

## Picotron package selected for P0

- Nested package: `picotron_0.3.0d2_raspi.zip`
- Compressed length: **7,605,966 bytes**
- SHA-256: `ba84788f0b043d75cf4be53a8df80f2c192f3a847e4d1023acbeb82124cbae75`
- Expected license holder: Lexaloffle; **commercial user-provided runtime**, not for redistribution in APKs or public releases.
- Outer combined archive contained PICO-8 0.2.7 and Picotron 0.3.0d2 builds for multiple platforms, **120 MB total**. P0 only requires the original Picotron Raspberry Pi build for now.

### ZIP entries (byte lengths)

| Entry | Bytes |
| --- | ---: |
| `picotron/picotron` | 1,276,928 |
| `picotron/picotron.dat` | 11,978,331 |
| `picotron/picotron_manual.txt` | 306,696 |
| `picotron/license.txt` | 5,044 |
| `picotron/lexaloffle-picotron.png` | 501 |

The executable starts with ELF magic, **ELFCLASS64**, little-endian, machine code **183 (AArch64)**. This validates architecture, **not actual Android launch, glibc compatibility or rendering**.

- `picotron/picotron` SHA-256: `481b0214c51517cac9c4aaad5bea3c2de9d16c58a8c50bec1358fef552724a5f`
- `picotron/picotron.dat` SHA-256: `c41a294a4a0158fa7857e764e1c137c4978315ca2c8d9c5a3d957e42d5297e7c`

## Next actions in the P0 spike

1. Access the owner's purchased archive through an owner-controlled **local** path in the actual development environment, not through this Markdown file.
2. Verify SHA-256, archive entry bounds, headers and full `readelf -l -d` interpreter/shared-library dependencies.
3. Record actual Linux startup/SDL/render behavior and whether an app-owned Android compatibility environment can run it.
4. Keep all extracted commercial files, temporary builds and local evidence under ignored `.local/` or equivalent.
5. Do not add purchased binaries to a GitHub Release or bundled APK. If the owner intentionally stores a copy in a private repository, isolate it from public source/build artifacts and review applicable licensing and access.

See [runtime spike](RUNTIME_SPIKE.md) and [status](STATUS.md).
