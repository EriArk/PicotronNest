# Short official-runtime input check

**Pending: do not run while the owner has reserved Retroid or requested no screen
tests.** This procedure is prepared, not executed evidence. Owned emulator
results in [P1_BRIDGE_TESTS](P1_BRIDGE_TESTS.md) do not satisfy it.

## Before occupying the device

1. Re-read [STATUS](STATUS.md). Use only the known lab namespace and the owner's
   existing separately imported Picotron 0.3.0d2. Verify the exact physical serial,
   installed package and available foreground window. Never act on another app.
2. Build the current `--desktop-base` APK using the verified private headless base.
   Retain its `build.json`, source revision and hash. The collector must match
   the installed APK to this build; an earlier display-only APK is insufficient.
3. Check the lab's prior report/process census. Resolve unknown active sessions
   before starting another. Preserve journals, private imports and homes; never
   clear application data or delete evidence to get around the 16-session quota.
4. Update only the known lab with `adb -s SERIAL install -r ...`. No public APK
   upload or GitHub Release is authorized. Sessions use fresh disposable homes,
   so the interaction check does not edit the owner's existing projects.

## Three bounded sessions

The desktop currently requests SDL_QUIT after 20 seconds. Use a small number of
actions per session, and stop touching before that deadline so normal release
can be observed. The 30-second system timeout is a failure bound, not a normal
exit mechanism. Do not increase limits merely to hide a failed shutdown.

| Session | Action | Required observation |
| --- | --- | --- |
| Pointer and real action | Wait for the real desktop; move to a visible menu/item and activate it; record its actual label and behavior | Cursor reaches the intended point; a real Picotron selection/menu/window changes. A packet count alone does not pass |
| Drag and geometry | Use an observed draggable item in the disposable home; move slowly, then toward a displayed edge and release; check a fresh touch in the outer Android bars | Expected Picotron drag behavior; release clears the button; outer bars do not begin an unintended click. Record the full window and its inner letterboxing |
| Focus and return | Hold a drag, leave the lab for Home, return; repeat with rotation if the time budget allows | Release arrives, pointer is usable again, same guest PID persists during interruption, no second launch |

Include one short click and a rapid press/release pair: the shared decoder is
tested, but Picotron's event-polling versus sampled mouse state is still unknown.
If a menu/icon needs a double-click, record that separately; the current adapter
does not yet implement SDL double-click counting. Do not label that as supported.

After each session run the existing collector:

```powershell
py -3 -B experiments/execution-model/check_report.py --serial SERIAL
```

Capture actual before/after screen evidence only while the lab is foreground.
Keep it under ignored local evidence. If another application appears, exclude
that capture from proof. Do not publish proprietary desktop assets or personal
screens. A normal exit must be code 0 with the expected SDL quit/destructor
markers and a final host-only UID census. Require matching sent/received pointer
counts, equal down/up counts, held=0 and no stream/protocol error.

## Record and decide

Record device/API/ABI, Picotron archive hash, source commit, APK hash, journal
UUID, actual action, cursor location, before/after evidence, native PID, input
counters and exit/census. Distinguish injected input from owner-operated physical
touch. Mark each unobserved scenario NOT TESTED; zero-input reports are display
diagnostics only. Re-run a cold launch after confirmed shutdown to test reuse.

If these checks pass, close only the scoped P1b interaction gate. Keyboard/IME,
hardware mouse, controller mappings, sound, project persistence, alternate video
modes and enforcing ARM64 compatibility still require their own evidence.
