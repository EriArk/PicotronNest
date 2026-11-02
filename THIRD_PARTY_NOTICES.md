# Third-party components

PicotronNest does not contain Picotron. Users import their own official copy.
Picotron, its desktop and its tools belong to Lexaloffle. The screenshots in this
repository document the actual application; they are not a runtime distribution.

The Android package contains these open-source compatibility components:

| Component | Pinned source | Notices included in the APK |
| --- | --- | --- |
| PRoot and its loader | Termux PRoot 5.1.107.99 | `PROOT-COPYING.txt` |
| talloc | 2.5.0 | `TALLOC-LGPL-3.0.txt`, `TALLOC-COPYRIGHT.txt`, `GPL-3.0.txt` |
| libandroid-shmem | 0.7 | `ANDROID-SHMEM-LICENSE.txt` |
| glibc loader and libraries | Debian 2.36-9+deb12u14, ARM64 | `GLIBC-COPYRIGHT.txt`, `GLIBC-COPYING.txt`, `GLIBC-COPYING.LIB.txt`, `GLIBC-LICENSES.txt` |
| SDL2 | 2.32.10, compiled from pinned source with the PicotronNest dummy-audio clock patch | `SDL2-LICENSE.txt`, `SDL2-EMBEDDED-NOTICES.txt`, `SDL2-PicotronNest-CHANGES.txt` |
| libcurl | 8.23.0, built for HTTP/HTTPS from pinned source | `CURL-COPYING.txt` |
| Mbed TLS | 3.6.7 LTS, statically linked into libcurl | `MBEDTLS-NOTICES.txt` |
| Android NDK CRT/compiler support | 28.2.13676358 | `NDK-NOTICE.txt` |
| Linux headers (build input) | Debian linux-libc-dev 6.1.176-1 | `LINUX-HEADERS-COPYRIGHT.txt` |

Full notices are available in **About and diagnostics → Open-source notices**.
The full upstream text governs each component; a project-wide license does not
replace component licenses. The talloc source includes LGPL library terms and
GPL build-tool terms, so both are retained.

Exact URLs, checksums and source/recipe versions are recorded in
[proot.lock.json](experiments/execution-model/proot.lock.json) and
[headless.lock.json](experiments/execution-model/headless.lock.json), and
[network.lock.json](experiments/execution-model/network.lock.json). PRoot,
talloc and shmem are extracted from pinned Termux packages. The packaging script
adjusts bounded ELF dynamic strings (`libtalloc.so.2` → `libtalloc.so`, Termux
library search path → `$ORIGIN`) without replacing program code. SDL2 is built
with the configuration in `prepare_headless.py`; desktop builds apply the original
`patch_audio_clock.py` adaptation to schedule dummy playback by sample count.
The patch and its headers accompany the dependency source bundle. Original SDL
copyright and license notices remain intact; glibc uses pinned Debian files.
PicotronNest's SDL observer and Android frontend are original project code.

Original PicotronNest code is licensed under [MIT](LICENSE), selected by the
owner on 2026-11-02. This grant does not relicense Picotron, third-party components,
or the Picotron artwork depicted in device screenshots. Mbed TLS's complete
upstream license texts are retained; the build uses its Apache-2.0 option.
System CA certificates are obtained locally from Android when a session starts;
no private certificate, user-added root or fixed downloaded CA bundle is shipped.

Every binary release must ship the matching dependency source bundle alongside
the APK, the PicotronNest source archive, checksums and this notice. The bundle
contains exact upstream source archives, Debian source/patches, the pinned full
Termux recipe tree and the project build/adaptation scripts. See
[release preparation](docs/RELEASING.md). Do not upload private runtimes, signing
files or the entire `.local` directory as release attachments.
