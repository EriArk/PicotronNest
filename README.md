<div align="center">

# PicotronNest

### Your pocket fantasy workstation.

Run the real **Picotron** on Android. Play, write code, draw sprites and pick up
your projects wherever you are.

**Bring your own Picotron. One app. No root or Termux.**

[Getting started](#getting-started) · [Controls](#play-your-way) · [Help](docs/USER_GUIDE.md) · [Report a problem](https://github.com/EriArk/PicotronNest/issues)

<img src="docs/screenshots/desktop.png" width="720" alt="The original Picotron desktop running inside PicotronNest on a Retroid Pocket Classic">

*The actual Picotron desktop on Android, captured on a Retroid Pocket Classic.*

</div>

## Create and play, in Picotron

Your familiar desktop, windows, code editor, graphics tools and cartridges are
all supplied by **your official Picotron copy**. PicotronNest brings the Android
controls, fullscreen display and a persistent home for your work.

- **Make room for the game.** Small floating icons and translucent touch controls
  sit over the picture. No permanent control bar squeezing the screen.
- **Choose your controls.** Use a handheld gamepad, a mouse and keyboard, or your
  phone's touch screen and onscreen keyboard. Switch a controller between games
  and moving the pointer.
- **Make touch controls yours.** Pick a compact layout or add sticks and triggers.
  Adjust size and transparency, or hide the controls completely.
- **Keep creating.** Save in Picotron, close the app and return to the same home.
  Import/export cartridges and back up your projects through Android's file picker.

## Play your way

<div align="center">
<img src="docs/screenshots/touch-controls.png" width="540" alt="A project running in the official Picotron with translucent PicotronNest touch controls">

*An original example project running in Picotron. Touch controls are optional;
Auto mode hides them when a gamepad is connected.*
</div>

The floating **menu** opens controls and settings. The **keyboard** icon lets you
type. On a gamepad, **Start** goes to the game's pause menu; **Guide** opens the
PicotronNest menu. Android **Back** also gives you a way out.

## Getting started

**The first beta candidate is prepared for testing. There is no public APK download yet.**

When you receive a test build:

1. Install the PicotronNest APK on your Android device.
2. Get your own [Picotron copy from Lexaloffle](https://www.lexaloffle.com/picotron.php).
   This candidate accepts the **Raspberry Pi 64-bit ZIP, version 0.3.0d2**.
3. Open PicotronNest, choose **Import your runtime** and select that ZIP.
4. Tap **Start Picotron**. After setup, opening the app takes you straight to Picotron.

**Picotron is not included.** Windows, macOS, PICO-8 and other runtime versions
cannot be used in this candidate. There is no separate helper app to install.

## Before you try the beta

The current hardware check is **Retroid Pocket Classic, Android 14**. A 64-bit
ARM Android device is required; other phones, tablets and handhelds still need
testing. Audio playback and longer play sessions also need tester confirmation.

This first candidate works with local projects. Picotron's online features are
not enabled; use cartridge import/export to transfer files. Back up your Picotron
home before uninstalling the app: Android removes its private files on uninstall.

Need help? Read the [user guide](docs/USER_GUIDE.md), or use the short
[tester checklist](docs/TESTING.md). You can export a report from **Settings and
projects → About and diagnostics** when reporting a problem.

## For contributors

[Build and release instructions](docs/RELEASING.md) · [Current status](docs/STATUS.md) ·
[Roadmap](docs/ROADMAP.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

Inspired by the separate [PikoNest](https://github.com/EriArk/PikoNest) project.
PicotronNest is an independent, unofficial project and is not affiliated with
Lexaloffle Games. Picotron belongs to Lexaloffle; its runtime files are never
included in this repository or the APK.
