# Help and community links

Issue: [#5](https://github.com/EriArk/PicotronNest/issues/5).

The touch drawer and controller host menu open **Help and community**. About
retains diagnostic export and dependency notices, and adds **AbyssTail Website**,
**Join our Discord** and **Report a bug on GitHub**. Standard Android `ACTION_VIEW`
intents hand HTTPS links to the user's browser/app, without tracking parameters,
embedded credentials or a bundled invite. Missing/blocked handlers show a selectable
address with an explicit Copy link button. Offline retry happens in the browser;
the app does not probe network connectivity or claim that a page loaded.

## Destinations and dependency

Verified on 2026-11-10: `https://abysstail.art` redirects to the English site, whose
`contacts` section includes the maintained Discord invite. The requested stable
`https://abysstail.art/discord` route returns HTTP 404. Until that site dependency
is deployed, **Join our Discord** opens `https://abysstail.art/en#contacts`, with
text explaining the additional website step. No invite code is copied into the
APK. GitHub reporting opens `https://github.com/EriArk/PicotronNest/issues/new/choose`.

This implements website-based community discovery, not the full direct Discord
redirect acceptance. After the site route exists, verify its destination and
replace the fallback constant; do not infer readiness from the button's existence.

## Reproducible owned checks

Use the Java-only fixture described in [SCREENSHOTS.md](SCREENSHOTS.md), then:

```sh
python3 -B experiments/execution-model/run_screenshot_tests.py \
  --adb /path/to/adb --serial emulator-5582 --mode community
```

Fresh collection `ec9cec5f-5d13-4f8b-96f6-b12b97b95c36` passed on a dedicated
Android 11/API 30 x86_64 emulator (`sdk_gphone_x86_64`):

- Controller navigation opens the real About screen; touch actions dispatch the
  exact three HTTPS intents without flags/extras. Diagnostic and notice buttons
  remain present and links are focusable.
- Closing About returns focus to the owned workstation fixture without requesting
  session shutdown.
- A deliberately unavailable browser produces a selectable URL and explicit
  clipboard copy. The clipboard starts with a unique sentinel; assertions wait
  for the posted dialog callback so stale clipboard contents cannot hide failure.

All 55 production Java sources compile/dex against API 36 (minimum API 26);
16 Android test sources compile. The six screenshot fixture groups also pass on
the final APK (`b9fd1db1-fa9c-4361-ac76-ba2beb3797fe`).

The real About screen preview was inspected. Browser intents are intercepted;
external browser navigation, offline pages, Discord installation/login, physical
controller behavior and preservation of a live official Picotron process remain
untested. No purchased runtime or personal collection is in the fixture. Initial
fixture failure exposed a clipboard callback timing race, corrected before the
recorded passing run. Evidence and test APKs remain private under `.local`.
