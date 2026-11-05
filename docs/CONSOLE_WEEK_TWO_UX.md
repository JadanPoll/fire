# Fire Console usability: the week-two test (REM)

**Human report (2026-11-05):** Opening a core channel often showed a
vertical list of options merely because those features had been built.
Snake, already playable, appeared only after a second screen and
scrolling past experiments. This defeats the Console's intent to feel
like a game system rather than a development inventory.

Source of engineering criteria:
[The Richer Engineering Method — On UI and Interfaces](
https://github.com/JadanPoll/Learning_Visualizations_Taxonomy/blob/main/Navigationals/The_Richer_Engineering_Method.md).

REM asks whether a person will recognize an action when returning **a week
or a month later** and whether a control earns its existence in isolation
*and in the actual usage-frequency distribution of the whole system*.
The test is not "can all features eventually be reached?" but rather:
**Can the person immediately do what they realistically returned to do?**

## What changed

| Core channel | One/two-week returner's likely intent | Primary screen | Rare path preserved |
| --- | --- | --- | --- |
| Inbox | Read or send a message | Existing Browser-to-Inbox entry; untouched | Site-specific account/API functions |
| Browser | Reopen a site | Existing signed-in Fire Browser; untouched | Developer Browser Lab in Tools |
| **Playroom** | Play Snake/GBA or a pinned favorite | **No scrolling**: fixed game grid with direct Snake and GBA, up to two pinned web games, My Web Games, Explore | Tiny Collect/Dodge demos, Android Dojo, portability research under Explore. Browser shortcuts/add/remove live in My Web Games |
| **Compute** | Draft an intention or return to a Codex task | Draft, RUN PAIRED HOST, OPEN CODEX, current status | Connecting a host, device enumeration, diagnostics and job operations are contextual; old executor protocol/cancellation/recovery retained |
| **Apps** | Open an installed TV app | Actual installed app icon grid is first, and the first app receives initial focus | Install/update Catalog follows the apps |
| **Tools** | Update or repair the Console | INSTALL/UPDATE plus OTHER TOOLS | Terminal, voice/keyboard recovery, key lab, Browser Lab, Compute diagnostics, installed apps |

**Boundary:** This is not a new tabbed navigation SDK or a recommendation
algorithm. It changes only presentation and focus paths, reusing the
existing Activities and data. No accounts, permissions, game files,
installer implementation, codex executor protocol, pinned-game records,
or Browser cookies are deleted or migrated.

## The algebra behind this specific design

`Useful surface = actual intent × attention × discoverability × time-since-use`

This is a **heuristic product lens**, not a calibrated numeric model.
A low-frequency function can still be valuable, especially for recovery,
but it should not displace the high-frequency play/work action on the
main screen. Interactions must be visually distinct with blurred eyes:
game icons, short ordinary verbs, stable locations, readable focus rings.

For each feature, ask:

1. Would someone naturally use it one or two weeks after installation?
2. Is it a direct destination or a rarely used management mechanism?
3. Is its label legible after its mechanics have been forgotten?
4. Does exposing it on every channel visit improve the holistic flow?
5. Would hiding it cause a recovery hostage situation?

A feature can move to a secondary context if its action remains
**obviously discoverable when needed**. This is why Unpin remains an
explicit button inside *My Web Games*; a secret long press would
violate REM's rediscoverability principle. Conversely, "Connect remote
registry" should not be more prominent than actual Compute device cards.

## Three real TV acceptance flows (not rhetorical tests)

1. **Cold game return:** From Console, press Playroom. Snake should be
   visible immediately as the first large card; pressing OK starts Snake
   without visiting labs. Back returns to Playroom. Reopen Playroom after
   a week: the icon and label should still make sense without explanation.
2. **Returning to a web favorite:** Open Playroom, launch a pinned web
   game directly, return. Add a shortcut through My Web Games; on return
   the primary grid reflects the new pinned favorite, without losing
   existing shortcuts or Browser data. In My Web Games, Unpin is clearly
   labeled, confirmation is required, and only the shortcut is deleted.
3. **Practical work/maintenance:** Open Compute with no configured host:
   the useful action is OPEN CODEX or drafting an intention, not host setup
   or device registry. If a job ID exists, its status and recovery remain
   reachable. Open Apps: installed application tiles come first. Open
   Tools: App Catalog update is immediately available while optional
   diagnostics do not fill the screen.

**Device observation is still missing.** GitHub CI can verify Android
compilation and unit tests but not physical 720p focus behavior,
readability, TV remote handling, or actual week-two usage frequency.
The next relevant evidence is a short physical Fire TV session using
these exact flows. If a primary grid spills beyond one viewport or
focus jumps unexpectedly, record the device model and screenshot and
correct the smallest relevant factor.

## Future agent design guard

Before adding a new first-screen tile/button, show a concrete
high-frequency scenario that benefits. An experimental capability
normally belongs in its relevant dedicated activity. Do not merely
move every option to a second vertical list; distinguish **play**,
**create**, **investigate**, **configure**, and **recover** by intent
and surface only the relevant verbs in each context.

A useful feature does not earn permanent Home prominence solely by
existing. Conversely, hiding a rare but vital action behind an
undocumented gesture is also a design failure.
