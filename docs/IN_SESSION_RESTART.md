# In-session Picotron restart (issue #2)

Development behavior after beta 3, 2026-11-10. No APK release is implied.

## User flow

Open the floating controls, choose **Picotron**, then **Restart Picotron**.
The same menu is accessible with the configured controller menu command (Guide
in game mode). The confirmation explicitly warns that unsaved work will be lost;
**Keep working** cancels without requesting shutdown.

This restarts the whole official workstation, not only the selected game. Save
inside Picotron first. It is not a save state, undo or crash recovery operation.

The host sends the existing ordered native quit request. It requires matching
input receipts and native exit/reaping, then waits for the execution worker to
finish writing its report, close the runtime-store lease and process guard, and
request foreground-service shutdown. Only then does it release the launch gate
and permit one restart into the same persistent home. Session reports remain
in their existing per-session directories; no runtime/home cleanup is added.

The session owns restart intent across Activity recreation. A paused Activity
does not initiate relaunch; completion resumes when the owner returns to it.
The restart can be claimed once, and a stale Activity cannot restart a different
session. A missing receipt, unresponsive process or failed/timed-out teardown
cancels restart. Late completion cannot revive it. The host explains that closure
is unconfirmed and keeps diagnostics/recovery available; it does not force-kill
an unknown session or silently discard unsaved work.

## Validation and remaining acceptance

On Linux x86_64 with Temurin JDK 17.0.21+9:

```sh
JAVA_HOME=/path/to/jdk python3 -B -m unittest discover -s experiments/execution-model -p test_frame_protocol.py
```

All 11 host checks pass. The exit test now covers single request ownership,
restart claim only after native verification and teardown, duplicate claims,
teardown failure/timeout, late callbacks and failed native shutdown. The
existing transport/input checks also pass.

All 52 production Java sources compile with warnings as errors against Android
API 36 and pass D8 for minimum API 26. The 11 Android test sources compile.
The existing menu instrumentation includes a new restart-confirmation cancellation
case, but has not been run in this increment. Full APK rebuild, Android lifecycle
execution and actual normal/hung restart on the official runtime remain untested:
the connected ADB device has not authorized this computer. The source/host checks
do not close the physical acceptance criteria of issue #2.
