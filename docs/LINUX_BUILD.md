# Linux build and owned native checks

## Scope

The product builder and native bridge-fixture builder now resolve tools for Linux
and Windows x86-64. `--sdk` / `--jdk` override environment discovery; defaults use
`ANDROID_HOME` (or `ANDROID_SDK_ROOT`) and `JAVA_HOME` / `javac` on PATH. Windows
retains `.exe` names and its prior SDK/JBR fallback. Unsupported host architectures
fail explicitly. Actual Windows rebuilding and macOS support are not claimed here.

The headless SDL, desktop/audio adapters and HTTP/TLS preparation use the same host
tool resolver. Fresh headless bases and audio/network caches include its identity;
old base receipts without it are rejected with a rebuild instruction. Lock-file
checks, native dependency closure, APK payload allowlists and source hashes remain
enforced. Dependency-source release preparation includes the helper as well.

`--artifacts-dir` separates private development outputs from published candidates.
The bridge fixture generates/reuses its own debug key in that directory; it no
longer requires an earlier lab build's key. Keep that directory when updating the
same fixture installation. Older lab-signed fixtures use a different identity, so
use a fresh disposable emulator installation for this new fixture key.

## Reproduce

Use Python 3.11+, JDK 17+, Android SDK platform 36, build-tools 36.0.0,
NDK 28.2.13676358, and CMake/Ninja on PATH. This run used JDK 17.0.21+9,
NDK r28c / Clang 19.0.1, CMake 3.31.6 and Ninja 1.12.1 on Linux x86-64.
The native compiler came from an existing local Android SDK toolchain; its hash
is recorded. The CMake distribution was checked against the official SDK catalog
size/SHA-1. Source dependencies retain their existing SHA-256 pins.

From a checkout whose source bytes match the intended commit:

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk
export PATH="$ANDROID_HOME/cmake/3.31.6/bin:$ANDROID_HOME/platform-tools:$PATH"

python3 -B experiments/execution-model/build.py --headless \
  --artifacts-dir .local/development/headless
python3 -B experiments/execution-model/build.py --candidate \
  --desktop-base .local/development/headless/picotronnest-execution-lab.apk \
  --keystore .local/release/signing/picotronnest.jks \
  --password-file .local/release/signing/password.txt \
  --artifacts-dir .local/development/candidate

python3 -B experiments/execution-model/build_bridge_tests.py --abi x86_64 \
  --artifacts-dir .local/development/bridge
adb -s emulator-5582 install -r .local/development/bridge/bridge-tests.apk
python3 -B experiments/execution-model/run_bridge_tests.py \
  --serial emulator-5582 --artifacts-dir .local/development/bridge --mode menu
python3 -B experiments/execution-model/run_bridge_tests.py \
  --serial emulator-5582 --artifacts-dir .local/development/bridge --mode bundle
```

The signing files must already be the owner's private release identity. They are
never generated over an existing identity, printed, committed or bundled. No
Picotron archive is a build input. The bridge runner permits only an explicitly
selected x86-64 emulator and verifies installed/source/APK hashes plus a fresh
session marker and final UID process census.

## Validated artifacts — 2026-11-10

Builds used an isolated export of the reviewed Git index with canonical line endings,
preserving the owner's pre-existing CRLF-only worktree changes. The final candidate's
93 recorded source hashes match the index exactly. Its 44 payload entries match the
allowlist; none matches the known proprietary runtime/archive hashes. Packaging
checks confirm ARM64 only, min SDK 26, target SDK 36, non-debuggable, no diagnostic
native executables and the existing published signing certificate.

| Private artifact | SHA-256 |
| --- | --- |
| Fresh headless base | `cce5886aeb93b5ce1810d64ed15fe0ae885b9569a0aa87dc5f247ad65b09bf7d` |
| Final product candidate | `2e973bc7a391d7c9a04054d59d1894cfa5c562b027005f29c2494b4ffa05d926` |
| Final x86-64 native fixture | `5975f1fc7677c0afe0f9d842e6046fc5909a838acbe9ea2c1a8d10912feedd21` |

The product candidate is under `.local/linux-build/candidate-final/`, with build,
signature and packaging verification receipts. Packaging version remains
`0.1.0-beta.3` / code 1003: this is a private development binary containing #1–#7,
not the published beta 3 artifact. Its hash identifies it. Existing release/candidate
artifacts were preserved; no tag, upload or GitHub Release was created.

Seventeen host checks (tool resolution, stale-base rejection, headless evidence and
source/PRoot guards) and two candidate-manifest checks pass. On the dedicated
Android 11/API 30 x86-64 emulator (`sdk_gphone_x86_64`, SELinux Enforcing):

- `menu`, collection `c4fc4301-700c-4ff8-970d-a38a54e7bf6f`: 11 checks pass.
- `bundle`, collection `e7890d0e-6464-4afb-ab81-8c62426f37d0`: 17 checks pass.

Both report exactly one owned native launch, verified exit code 0 and no surviving
child in the fixture UID. The bundle covers ordered keyboard/pointer/modifier events,
Unicode/Enter, repeat/cancel, focus loss, Activity recreation and verified quit.
An initial bundle run failed because two UI-idle waits turned an intended quick
virtual-key tap into a long hold. The test now sends both tap edges in one UI turn;
it still separately tests held repeat/cancellation and requires exactly one balanced
tap. Production input code was not changed to satisfy that test.

This proves the Linux build pipeline and owned Android/native fixtures, not operation
of the ARM64 product with official Picotron, physical inputs, audible sound or modern
ARM64 execution policy. Per the owner's instruction, Retroid testing is deferred
until explicit permission to resume. Continue emulator-based storage/lifecycle work
in the meantime; do not poll, install or run tests on the owner's device.
