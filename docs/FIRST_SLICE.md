# First Fire Console ↔ Logical Cloud implementation slice (2026-11-04)

**Intent:** build small, testable outcomes under the accepted Fire TV vision, not a new universal agent service. This slice preserves the six-channel home, existing pinned web games, GBA integration, apps and Browser data. Compute is intentionally deferred as a product direction: the existing channel is retained, not redesigned.

## What was actually added

| Factor | User-facing promise | Implemented candidate | What remains unknown |
| --- | --- | --- | --- |
| Playroom / 2D | Tiny games expose real D-pad/rendering assumptions | **Playroom → OPEN 2D MINIGAME LAB**: Collect the Dot (discrete movement) and Dodge the Block (timed movement). Pure-Java rules are independently testable. | Real Fire OS focus, timing and visual ergonomics |
| Portability intelligence | Agents can look up execution *and* interaction paths and honest evidence | `research/portability/targets.json` + `tools/portability_matrix.py` | Automated research, real-device title-specific verification, adapting any listed app |
| Android Dojo | Problems lead to tacit skills and glossaries; partial progress persists without grading | `research/android_dojo/catalog.json` + `tools/android_dojo.py` (host CLI, local note ledger) | Integrated TV challenge browser, skill assessment and multi-device continuation |
| Modular assets | Independent updates need not recompile core game logic | Explicit Dojo research problem; static asset-free native game baseline | Asset manifests, bundle hosting, hashes, cache, offline rollback and runtime loading |
| Compute | Discover eventual holistic purpose through projects | Existing Compute untouched | No new general Compute promises or service |

## Try the two native game probes

1. Build/update the Fire Console lab APK using the existing workflow and consent-based installation procedure.
2. Open **Playroom → OPEN 2D MINIGAME LAB**. Choose **Collect**. Tap four directions to collect the yellow target, check bounds, and return with Back.
3. Choose **Dodge**. Move left/right while an orange block advances. Compare visual updates and game events.
4. Observe focus loss, repeats, long presses, Back behavior, timer behavior after app switching. Capture specific on-device evidence using the existing Input Lab and test loop rather than claiming the emulator proves physical Fire OS behavior.

No remote assets, background downloader, new permissions, global input remapper, or service are included. The games intentionally suppress held-key repeat as an *experimental baseline*, not a general input policy.

## Query a software request as an agent

```bash
python3 tools/portability_matrix.py validate
python3 tools/portability_matrix.py list
python3 tools/portability_matrix.py lookup "VS Code"
python3 tools/portability_matrix.py lookup "GBA emulator" --json
python3 tools/portability_matrix.py lookup "An unassessed game"
```

The last lookup exits with code 2 and emits an **unassessed** research stub: absence of a record is not proof of incompatibility. Agents should compare routes separately on execution, interaction, adaptation, blockers, upstream references and *local* device evidence. Add a candidate route by editing the authoritative data; a future agent can own autonomous research and refinements against this narrow contract.

The state vocabulary distinguishes `upstream_supported` (a real upstream route), `repo_implemented` (we have integrated a route in source), and `device_verified` (requires an actual device trace). An agent must not promote a route merely because a website exists or CI succeeds. The machine-readable matrix is rough, deliberately small, and extensible.

## Navigate an ungraded Android challenge

```bash
python3 tools/android_dojo.py list
python3 tools/android_dojo.py show remote-double-action
python3 tools/android_dojo.py note remote-double-action "Observed an unexpected second ACTION_DOWN"
python3 tools/android_dojo.py state remote-double-action paused
python3 tools/android_dojo.py progress remote-double-action
```

The Dojo glossary is a *navigation aid*, not a forced reading order. Tacit checks suggest demonstrable skills, not grade requirements. Notes and states are saved to the current host's `~/.local/share/fire-android-dojo/progress.json`; override with `--progress PATH` before the subcommand. This does **not** yet synchronize progress to GitHub, TV, or another agent. Avoid entering private information into progress records intended for eventual sharing.

## The first cross-project experiment

A realistic next request is: "Get VS Code onto the TV."

1. Query `tools/portability_matrix.py lookup vscode` and inspect the multiple execution routes.
2. Keep *runs on TV* and *usable with a remote* as distinct judgments; consider the future iPad companion as an independent control factor.
3. If real Fire testing disproves a hypothesis, attach exact device observations and revise only that route. If the problem generalizes (e.g., window navigation or keyboard handling), spawn a separate research project and link its result back to the relevant record.
4. Never equate a route being listed with installation authority, app licensing, or verified usability.

## Source decisions and bounded evidence

- **Prior local implementation studied:** six-channel `ConsoleActivity`, `GameShelfActivity`, shared `TvUi`, pure JVM `GameUrlPolicy`, existing remote/input audit and Game Boy Advance integration. Their contracts are reused rather than replaced.
- **Native rendering factor:** one foreground Activity and app-owned `View`, using normal Android `Canvas` and D-pad events; not a new SDK or global input broker. The pure game rules are independent of Android and have narrow JVM tests.
- **Checks in this branch:** static Python tests and Java rule tests are provided. CI/build and physical Fire TV observations must be reported separately after execution; no device verification is claimed here.
- **Constitutional scope:** LC-16.03 (agents handle routine engineering), LC-02 (small independent factors), LC-14.01 (inspect existing mechanisms), LC-11.01 (targeted checks), LC-15.04 (method versus outcome). Method conformance is supported by source diff and selected tests once executed; full user-outcome success remains **undetermined** until a physical Fire TV session.
- **Rollback:** undo the native Playroom entry/Activity without touching pinned games or Browser data; Python tools and research catalogs are independent and have no runtime network side effects.

This is a candidate, not a universal portability platform, remote asset service, completed Dojo UI, or complete human-agent runtime. The point is to obtain inexpensive falsifiable observations and preserve their context for the next agent.
