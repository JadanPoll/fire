# Native Snake + Android Dojo — bounded TV iteration

This slice builds on the existing Console, Playroom, Input Lab and
[previous 2D probes](FIRST_SLICE.md). No new launcher channel, native
engine, shared asset manager, permission or background service is added.

## Playroom → 2D Minigame Lab → Snake

Snake is a standalone Android Activity and a pure Java deterministic
`SnakeGameModel`: 16×10 board, one queued change of direction per tick,
no immediate reverse, wall/body collisions, tail-vacate behavior, growth
and deterministic food placement. The View renders board and pieces with
Android Canvas. The ticking loop stops on window detach/onPause, resumes
when the Activity returns, and preserves the in-memory current round.
`OK` pauses/resumes; `D-pad` changes direction; `Back` returns to the
previous catalog. **NEW GAME** resets. This intentionally tests remote
timing and model/renderer separation, not graphics fidelity or native
engine performance. It has no network or external assets.

**Physical-device test to perform:** turn twice between two ticks,
attempt to reverse, eat initial food, collide with a wall, pause/resume,
switch apps and return. Record actual control behavior. Compiling and unit
testing are not proof that every Fire remote emits the expected events.

## Playroom → Android Dojo (道場)

The TV now presents the same problem-first catalog used by
`tools/android_dojo.py`. Android Gradle mounts
`research/android_dojo/catalog.json` directly as an asset; there is
no second copied topic ontology or per-challenge hard-coded lesson tree.

The dark Dojo theme is intentionally scoped to this Activity:
ink-dark panels, warm ivory text, gold navigation accents, reddish
observations and conspicuous D-pad focus. It does **not** change the
light Fire Console theme. This is an early example of independent
developers giving projects a recognizable style while respecting common
focus and Back behavior.

Each challenge provides its interesting problem statement, topic path,
glossary, tacit skill checks, illustrative partial progress, and a
local experiment journal. The input challenge opens the existing Input
Lab. The asset challenge links to the existing Playroom mini games.
Mark a challenge **exploring**, **paused** or **revisiting** without grades;
write any observation, and reopen it later. The first iteration keeps
notes in the Fire Console app's private SharedPreferences. The
host-side Python Dojo CLI retains its own separate local notes, so
**cross-device sync is not implemented**. No notes leave the TV and no
external credentials or telemetry are collected.

This is a TV-native learning loop rather than a full challenge execution
environment. The richer next experiment is an actual Android problem
whose code/trace an agent attaches to one journal entry. Challenges
should accrete experiments, not become compulsory coursework.

## Intent / evidence / rollback

- Parent purpose: tacit Android learning through meaningful, ungraded
  experiments; tiny games reveal real engine/input assumptions.
- Factors: Java game rules, Canvas view, problem catalog, TV progress,
  themed rendering and existing Input Lab remain separately replaceable.
- Static and JVM checks: `SnakeGameModelTest`; existing Fire CI
  runs app unit tests and assembles the APK. Physical-device evidence
  is separate and must be recorded honestly.
- Prior-art basis: actual inspected
  [MiniGameLabActivity](../app/src/main/java/dev/jadan/fire/MiniGameLabActivity.java),
  [InputLabActivity](../app/src/main/java/dev/jadan/fire/InputLabActivity.java),
  and [TvUi](../app/src/main/java/dev/jadan/fire/TvUi.java).
- Rollback: remove the Snake/Dojo entry points without changing saved
  browser, GBA, or other game records.
