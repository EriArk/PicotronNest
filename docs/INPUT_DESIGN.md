# Input across phones, handhelds and larger screens

**Interaction direction accepted by the owner on 2026-10-23; exact defaults and
bindings still need usability tests.** This is not an implemented feature list.
The official-runtime lab currently has a single-touch left-button adapter.
Official Picotron input acceptance is pending; see [the prepared hardware
check](RETROID_INPUT_CHECK.md). Product UI work follows that gate.

## Accepted interaction direction

| Context | Candidate behavior | Questions to settle through use |
| --- | --- | --- |
| Phone, workstation tasks | Both direct touch and a relative touchpad mode; a clear switch; accessible keyboard and mouse-button controls | Which mode should open by default? Can users accurately select small desktop targets without covering them? |
| Phone, code entry | Keep Picotron visible above the IME; a compact row for Esc, Tab, modifiers and arrows; return-to-fit control | Portrait versus landscape comfort, useful keyboard rows, and which SDL text/composition operations Picotron actually consumes |
| Handheld with sticks | Configurable stick cursor, precision action, primary/secondary click and reachable keyboard/menu actions | Cursor speed, acceleration and deadzone require real controllers; no fixed Retroid-specific button scheme |
| Handheld without sticks | D-pad cursor with adjustable acceleration and a precision action; remappable clicks; every essential action reachable | A controller-only path must also work on devices lacking analog axes and a touchscreen |
| Tablet or mouse/keyboard | Direct pointer plus physical mouse/keyboard; wheel and secondary click; touch controls can be hidden | Hot-plug behavior, modifiers, text duplication and visible focus need tests |

Device category should suggest a profile, not lock users into one. Choose usable
controls from detected capabilities, allow overrides and retain mappings per
chosen controller/profile. Do not infer available analog sticks from a device
name. Keep the actual Picotron desktop and editors as the workspace.

## Shared behavior to preserve

- Video and input share one transform. Fit, orientation, host viewport changes
  and eventual user-controlled zoom/pan must preserve a reversible mapping.
  Changing the coordinate system cancels the active drag before new input.
- Direct touch selects an absolute point. Touchpad mode moves a visible cursor
  relatively, with explicit accessible click/drag controls. Gestures and default
  secondary-click behavior remain a usability decision, not hidden assumptions.
- If zoom is added, it is a host viewing tool with a clear way back to the full
  workstation. It must not modify Picotron's framebuffer or project data.
- Mode changes, dialogs, focus loss and disconnects release held input. Multiple
  devices need source-aware held state: unplugging one device must not release
  an unrelated physical key that is still down. An active drag needs a defined
  pointer owner to prevent another input source from moving it unexpectedly.
- Text input and key shortcuts are separate paths. Physical key down/up and
  modifiers must coexist with committed IME text without duplicate characters.
  Composition, Unicode and glyph coverage require real Picotron tests; no
  blanket promise of full IME support is made from Java compilation alone.
- Controller workstation mode and cartridge/game controls must be distinguishable.
  Switching mode must release previous held actions. Mouse emulation must not
  silently consume controls a running cartridge needs.
- Keyboard, host menu, exit and return-to-fit must remain reachable on touch-only
  and controller-only devices. Entering a setup dialog must not strand a held
  button or lose the current project.

## Proposed implementation order after the P1b hardware check

1. Verify actual SDL pointer/button and keyboard/text consumption with bounded
   probes. Add secondary button/wheel and paired key/text contracts using that
   evidence; keep adapters replaceable.
2. Implement hardware keyboard/mouse and Android IME with regression cases for
   focus, composition, modifiers, Home/return and device disconnect.
3. Add direct-touch/touchpad selection and a minimal remappable controller cursor.
   Choose default bindings only after testing devices with and without sticks.
4. Evaluate phone portrait/landscape, small handheld screens and a larger screen
   using the same real workflows: select a file, enter/edit Lua, draw a precise
   line, save/reopen a project and return after a normal interruption.

The owner's preferred phone workflow, initial mode and first controller profile
remain discussion points. Accepted direction does not establish phone usability,
hardware input support or a finished product frontend. While hardware is reserved,
bounded [keyboard/Unicode fixture work](KEYBOARD_SPIKE.md) prepares the next adapter
without enabling an unverified path in official Picotron.
The [relative touchpad/stick fixture](CURSOR_SPIKE.md) similarly exercises explicit
drag ownership, remapped controls and cancellation without selecting product
defaults or claiming physical-controller support.
The subsequent [D-pad/IME experiment](DPAD_AND_IME.md) checks key and HAT profiles,
separates keyboard arrows from controller directions, and keeps the owned frame
above the docked IME. Physical controller/IME interaction is still untested.
The [saved-profile experiment](CURSOR_PROFILES.md) persists explicit bindings
and tuning while discarding held input across View recreation. It does not
select product defaults or infer a controller from a handheld model.
