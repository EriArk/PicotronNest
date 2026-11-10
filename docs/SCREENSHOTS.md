# In-session screenshots (issue #4)

Development behavior after beta 3, 2026-11-10. No product APK release is implied.

Choose **Save screenshot** in the floating controls, or in the **Picotron** host
menu with touch/controller. Android asks where to create an `image/png` document.
Only the original Picotron frame is captured at its current native dimensions:
no letterboxing, scaling, host controls, touch gamepad or Android keyboard.
Nothing is uploaded or shared automatically. A user-selected cloud document
provider may synchronize its destination according to that provider's behavior.

The capture makes an immutable copy under the frame lock, then releases that
lock. PNG compression and destination writes run on a worker, leaving frame
presentation free to continue. One snapshot can be pending at a time, bounded
by the existing 2048-by-2048 frame protocol. There is no screenshot cache on disk.
Picker cancellation releases the snapshot without opening a destination. Errors
explain that the chosen document may be incomplete; exported documents are never
automatically deleted. The user can choose another destination and retry.

The same snapshot survives Activity recreation; the picker is not reopened.
If Android kills the app process, the capture expires instead of silently saving
a later frame. An incomplete/empty document created by a provider may require
manual removal. Missing/ended frames cannot be captured. Navigating away while
a write is in progress does not recycle the bitmap underneath the worker; a
completion message follows. A stalled provider can keep that single capture
busy; provider timeout behavior has not been established.

## Reproduction

The Java-only fixture has no native libraries or purchased files. Its separate
package is `art.picotronnest.screenshottest`; only use an owned emulator.

```sh
python3 -B experiments/execution-model/build_screenshot_tests.py \
  --jdk /path/to/jdk \
  --android-jar /path/to/sdk/platforms/android-36/android.jar \
  --build-tools /path/to/sdk/build-tools/36.0.0
python3 -B experiments/execution-model/run_screenshot_tests.py \
  --adb /path/to/adb --serial emulator-5582
```

The runner verifies source/APK hashes, installs only the owned fixture and saves
reports under `.local/screenshot-tests/evidence/<session>`. The build verifies
an APK allowlist containing only the manifest, classes and signing metadata.

## Evidence and limits

2026-11-10: dedicated PicotronNest Android 11/API 30 x86_64 emulator, target 36,
Temurin JDK 17.0.21+9, compile API 36, minimum API 26. Final session:
`b9796919-ea97-4fa5-88cc-8101fa56da68`.

Six groups pass: immutable frame and stale/missing rejection; controller menu,
rotation and single-picker ownership; cancellation without writes; denied
provider and retry; invalid URI rejection and repeated portrait export; expired
capture rejection. PNG dimensions and every pixel match the owned 240x135 and
135x240 frames despite subsequent frame changes. The exported portrait fixture
was visually inspected. The owned session-exit state stays RUNNING.

The first collection failed because the test's ActivityMonitor returned a stale
finishing Activity during rapid successive captures. The fixture now selects a
new capture ID; the final collection above passes. The provider is a real private
Android ContentProvider, but the picker result is controlled by instrumentation.
This does not establish external DocumentsUI navigation, arbitrary cloud providers,
physical controller use or official Picotron continuity. Those issue #4 device
acceptance checks remain open. Two product-manifest checks also pass; the
ScreenshotActivity is internal and no storage permission was added.
