# Native Picotron networking

PicotronNest supplies the networking dependencies of the official runtime. Its
Splore UI, BBS catalog, cartridge loader, accounts and network APIs remain the
original Picotron implementation. No replacement catalog or content proxy is used.

## Implementation

- The Android application requests `INTERNET`; no storage or location permission
  is added. Guest sockets use the app's Android UID and network routing.
- The inspected 0.3.0d2 ARM64 executable dynamically loads `libcurl.so` or a
  versioned variant and uses `getaddrinfo`. The guest now includes source-built
  libcurl 8.23.0 and Mbed TLS 3.6.7 LTS, restricted to HTTP/HTTPS. Versions,
  source checksums and build options are pinned in `network.lock.json` and
  `prepare_network.py`. All ELF dependencies must be in the existing closure.
- A small `getaddrinfo` adapter forwards names to Android's resolver through a
  per-session abstract Unix socket. This avoids hardcoded DNS servers and uses
  Android's active-network resolver, including its Private DNS configuration.
  VPN and network-change behavior still require device-specific acceptance.
- The DNS server checks both the Android UID and the live Picotron frame-peer PID.
  Requests and responses are bounded, timed out, and discarded at session end.
  Names, URLs, account credentials and response bodies are never logged by this
  adapter. Numeric addresses use the normal libc path.
- Each launch atomically refreshes a PEM file from Android's system trust roots.
  User-added roots are excluded. libcurl uses that file for certificate and
  hostname verification; no fixed public CA bundle or insecure fallback is used.
- Online traffic remains direct from Picotron to the requested service. Native
  Picotron TCP/UDP sockets also benefit from the Android name resolver. Account
  login, uploads and private data are left to the owner/testers.

The new dependency sources and complete license notices accompany every prepared
release. Picotron itself is still supplied only by the user.

## Focused regression checks

Final release preparation on 2026-11-02: 13 tool tests and 43 execution-model
host tests passed. The signed product was rebuilt from the final input/network
sources (SHA-256 `2ced8dea28a6d9f0f6ebccf3558b229a52b0d39f82b25ce5e065783057e9e166`).
On Retroid Pocket Classic / Android 14/API 34, the matching ARM64 fixture
(`ffb7241e192b58b3ecc691f9b26adfcb1be41040860bf5e95f98dab479c30aba`)
passed network instrumentation, diagnostic/touch checks and all 17 ordered-input
bundle checks, including newline/CRLF/composed Enter. The bundle acknowledged
77/77 records, exited 0 and left no fixture child. Native self-tests also cover
short key/button edges across frame boundaries. An initial invocation with a
non-UUID session name was rejected before launch; the corrected UUID run passed.
Private log: `.local/release/beta2-final-input.txt`.

The earlier official-runtime session (before the final input fixes) closed
normally with 1790/1790 acknowledged records; its sanitized receipt is retained
at `.local/release/network-final-diagnostics.json`. Final fixture checks do not
constitute another official-runtime or audible-sound acceptance run.

`NetworkInstrumentation` exercises actual Android system-root export and atomic
refresh, live DNS, negative DNS, protocol/PID rejection, subsequent valid requests
and channel shutdown. It does not use Picotron or an account.

```powershell
py -3 -B experiments/execution-model/build_bridge_tests.py --abi arm64-v8a
adb -s DEVICE install -r .local/bridge-tests/artifacts/bridge-tests.apk
adb -s DEVICE shell am instrument -w art.picotronnest.bridgetest/art.picotronnest.executionlab.NetworkInstrumentation
```

Upstream references: [Picotron manual](https://www.lexaloffle.com/dl/docs/picotron_manual.html),
[curl source release](https://curl.se/download.html),
[Mbed TLS LTS release](https://github.com/Mbed-TLS/mbedtls/releases/tag/mbedtls-3.6.7).
