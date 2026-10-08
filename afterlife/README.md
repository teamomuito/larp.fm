# Afterlife Clerk

A rulebook-and-rubber-stamp puzzle game for Android, built with Godot 4.3.

You are a newly dead clerk at the **Lighthouse Customs for the Dead**. Souls arrive carrying a
**ledger** and a **death slip**. Check them against an ever-growing rulebook, then stamp a
verdict: **Meadow**, **Archive**, **Furnace**, or **Return**. Correct stamps pay down your
afterlife debt, citations add to it, and interest is charged every night. Some souls plead
for mercy. The rulebook says no, but the Meadow is right there.

## Mechanics

- Rules are checked in priority order; the first that applies decides. Weight is the fallback.
- A new rule or rule change arrives most shifts, and the seal and contraband re-roll daily.
- The **Lens** (3 per shift) highlights mismatched papers. Tap any field to circle it.
- **Mercy**: wave a pleading soul into the Meadow against the rules. There is no citation and
  no credit, but the Auditor may fine you. Your mercy count picks the tone of the ending.
- Clear the debt to open the last file (the ending). Hit 350 and you're reassigned to the Furnace.

## Project layout

| Path | Purpose |
|---|---|
| `scripts/rulebook.gd` | Per-day rules, verdict evaluation, lens logic |
| `scripts/soul_factory.gd` | Generates souls and shift queues consistent with the rules |
| `scripts/data.gd` | Names, causes, story souls, narrative text, endings |
| `scripts/main.gd` | Game flow and all UI (built in code) |
| `scripts/ghost_view.gd` | Procedural ghost drawing |
| `scripts/sfx.gd` | Procedural sound effects (no audio assets) |
| `tests/` | Headless logic and UI smoke tests, plus a screenshot script |

## Run

Open this folder in Godot 4.3+ and press Play, or:

```
godot --path afterlife
```

## Test

```
godot --headless --path afterlife --import
godot --headless --path afterlife --script tests/sim.gd      # rules and generator invariants
godot --headless --path afterlife --script tests/smoke.gd    # plays full games through the UI
xvfb-run -a godot --path afterlife --rendering-driver opengl3 \
  --script tests/screenshots.gd -- /tmp/shots                # renders key screens to PNG
```

## Build for Android

1. Install the Android SDK/JDK and the Godot 4.3 export templates, then set the SDK path in
   Editor Settings > Export > Android.
2. Create a debug keystore (see the Godot docs) and point the Android export settings at it.
3. Project > Export > Android (preset included), or:
   `godot --headless --path afterlife --export-debug Android build/afterlife-clerk.apk`

The Android preset has not been exercised in CI here; verify it in the editor on first export.

## Ideas for next steps

Real art and audio, more rule types (documents that need two stamps, forged seals that only the
Lens catches), more story souls, achievements, and a daily-challenge seed.
