# Splore fullscreen follow-up

2026-11-02: the previously reported fullscreen layout defect was a mistaken
interpretation of native Splore behavior. No Android host or Picotron patch is
needed for the observed case.

## Observed behavior and reproduction

1. Open Picotron's menu, then Splore.
2. Open Splore's own title-bar menu and enable Fullscreen (Ctrl+1 also toggles it).
3. The desktop is replaced by a dedicated workspace. The list keeps its previous
   size and position. Its dark background shows the selected cartridge's artwork
   when available; an empty selection can leave it black.
4. Double-tap the list title bar to maximize it within Picotron's display.
   Double-tap again to restore its earlier geometry.
5. Select New and a cartridge: the category and selected item respond at their
   displayed positions. Show Android's keyboard and select another category:
   the full Picotron display fits above the IME and input still reaches the target.
6. Hide the keyboard, restore the list and disable Fullscreen. The desktop window
   returns. Picotron's own Shutdown ends the test session.

The fullscreen workspace and maximized list are separate native operations.
Keeping a small list is intentional, not evidence of a clipped Android frame.
Private inspection of the owner's native Splore code corroborated the retained
geometry and separate title-bar maximize/restore behavior. No proprietary source,
runtime bytes or edited system files are included in this change.

## Evidence and limits

- Retroid Pocket Classic, Android 14/API 34, ARM64; official owner-imported
  Picotron 0.3.0d2 Raspberry Pi 64-bit build; host target SDK 36.
- Published product `art.picotronnest`, version 0.1.0-beta.2/code 1002.
  Installed APK bytes match release SHA-256
  `2ced8dea28a6d9f0f6ebccf3558b229a52b0d39f82b25ce5e065783057e9e166`.
  Updated in place with the same signature; no home/data reset.
- Product native PID 28685 and tracer PID 28681 were present during the check
  and absent after native Shutdown. The pre-existing separate lab session was
  retained. No product exit-code/receipt assertion is made from a screenshot.
- Actual screenshots were inspected for the small fullscreen list, maximized
  list with selected artwork, keyboard-visible category selection and restored
  desktop. Private evidence is in `.local/evidence/splore-fullscreen-20261102/`:
  `product-fullscreen.png`, `product-maximized.png`, `product-ime.png`,
  `product-restored.png`. The initial lab observation and private source review
  remain there separately.
- This is a scoped visual/input check of the shipped APK, not a new build,
  broad device acceptance, network retry test or gameplay/audio acceptance.

The old warning in the original release attachment documents is superseded by
this record and the corrected live release description. Published APK, archives
and checksum files remain unchanged.
