# AGENTS.md — PicotronNest implementation brief

PicotronNest is a **new** Android wrapper, not a rename of PikoNest. Start with [status](docs/STATUS.md), [product vision](docs/PRODUCT_VISION.md), [roadmap](docs/ROADMAP.md), [architecture](docs/ARCHITECTURE.md), [runtime spike](docs/RUNTIME_SPIKE.md), [reuse notes](docs/REUSE_AND_PROVENANCE.md) and [acceptance](docs/ACCEPTANCE.md). Documents distinguish goals, hypotheses and device-proven behavior.

## Core rule

Run the **real official Picotron fantasy workstation** on Android. Do not reimplement its desktop, editors, window manager or scripting runtime. One end-user APK, one icon, no root, Termux or separate helper APK required. Users provide their own legally obtained official Picotron.

## First development task

Do P0 **runtime feasibility before UI work**. Inspect a user-provided Raspberry Pi 64-bit Picotron build privately, determine its real ELF/dependency/SDL behavior, test execution, then visible output. Read [RUNTIME_SPIKE](docs/RUNTIME_SPIKE.md). Never invent runtime filenames, argument syntax, archive layouts, framebuffer paths or successful tests.

## Invariants

1. Proprietary Picotron binaries/data, owner archives, personal carts, secrets and extracted runtime trees are **never committed, included in APKs, or publicly published**.
2. Open-source code may be reused only with known provenance, license notices and a transitive bundled-dependency audit. PikoNest is reference code; it is not an installed-app dependency.
3. Picotron's `.p64` / `.p64.png` and native `-home` semantics are authoritative. No PICO-8-specific cartridge editor or 128x128 framebuffer assumptions.
4. No destructive migration: validate and stage separately, atomically activate, retain recovery or rollback. Preserve existing user projects during host/runtime upgrades.
5. No broad storage permission if Android SAF and app-owned storage suffice. A `content://` URI is not a Linux path; stage or bind it safely.
6. Key-up, focus loss, Unicode text, modifiers and pointer-coordinate transforms must be correct. Controller mapping must not be hardcoded to one handheld.
7. Lifecycle requires process evidence. A launched activity or successful exit callback is not proof of a rendered desktop or a successful Picotron session.
8. Do not silently ship with an obsolete Android target API as the end state. Track and test a modern execution model.
9. Keep adapters replaceable; preserve documented project interfaces before extensive refactors.
10. Do not publish GitHub Releases/APKs or make destructive changes to user data or other repositories without explicit owner approval.

## Working procedure

- Prefer a small isolated probe over broad scaffolding. Only add code tied to a measurable milestone/test.
- Update `docs/STATUS.md` with actual evidence, device/OS/version and remaining blockers after validated milestones.
- Maintain reproducible test instructions and regression checks. No fabricated screenshots, performance numbers or compatibility promises.
- When the owner-provided runtime isn't accessible, write a bounded non-sensitive inspection procedure and state the dependency.
- Prioritize P0 → P1 → P2; leave cosmetic UI polish until the real Picotron screen and controls work.
- Use English for repository documentation and public-facing text unless the owner changes that preference. Discuss decisions with the owner in the conversation language.
- Finish each development increment with focused validation, a reviewed commit and a push to the project remote. Include only task-owned changes; never stage proprietary files or unrelated work. Report the commit and push result, or the concrete blocker if either cannot complete. This does not authorize publishing APKs or GitHub Releases.
