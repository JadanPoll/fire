# Portability Intelligence as algebraic substitution

**Parent goal:** Given "get me software X on Fire TV," the agent discovers how
to deliver the intended experience, which variables can change, which
constraints are binding, and which experiments would change the answer.
This is the REA-style long-horizon feasibility frontier (Layer 2 / B),
not merely an Android package search or catalog of screenshots.

This is an application of the *existing* Logical Cloud algebra rather
than a new "portability framework":

- [Logical Cloud README / the algebra](https://github.com/JadanPoll/Logical_Cloud/blob/main/README.md):
  `Outcome = Simulate(Application, Fabric, Placement, Demand, ArtifactCache)`;
  `Experiment = Variant × Axis₁ × Axis₂ × …`.
- [Logical Cloud architecture](https://github.com/JadanPoll/Logical_Cloud/blob/main/docs/architecture.md):
  `Plan = Place(ApplicationGraph, DeviceFabric, Demand, Constraints)` and
  `Run = Execute(Plan, ArtifactStore, StateService, EventTransport)`.
- [Agent change protocol](https://github.com/JadanPoll/Logical_Cloud/blob/main/docs/agent-change-protocol.md):
  `ApplicationContract × ImplementationVersion × ExecutionPlan × ExperimentPolicy → DeploymentCandidate`.

## Portability is a constrained product space

For a *requested user experience*, start conceptually with:

`Candidate = Experience × Implementation × Executor × Placement × Presentation × Controls × Artifacts × State × Transport`

subject to `Constraints = {device capabilities, latency, rights, privacy,
network, budget, authority, fidelity}`.

These are **question-conditioned variables**, not a mandate to store the full
Cartesian product. In particular, a combination may be impossible, untested,
or usable only after substantial adaptation. Cross-product enumeration is a
way to **find questions**; it is not validation.

The requested *experience* is the invariant across the experiment. Changing
the implementation may preserve its key behavior but not identical binaries,
features, performance, or compatibility. A recreation of a GUI is a new
implementation whose behavioral fidelity requires its own evidence.

| Factor | Example values | What should remain unchanged when isolating it |
| --- | --- | --- |
| Experience | edit source code; play GBA game | User's requested outcome and essential semantics |
| Implementation | VS Code Web; code-server; native compatible port | Requested experience, **not** necessarily executable code |
| Executor/placement | TV browser; remote workstation; cloud worker | Logical task and expected user-facing result |
| Presentation | native Android view; web UI; video streaming; reconstructed UI | Important visible behaviors and interaction meaning |
| Controls | D-pad; keyboard/mouse; iPad companion; semantic commands | Intended action (open file, move cursor, run code) |
| Artifacts/state | built APK; versioned assets; remote workspace; offline cache | Identity and recoverability of continuing work |
| Transport | direct local; WebSocket messages; screen stream | Required ordering, responsiveness and correctness |

## Real example: "Get VS Code on the TV"

The [seed index](../research/portability/targets.json) already separates
the candidate *execution route* from the *interaction configuration*.
Its [lookup CLI](../tools/portability_matrix.py) can explore these two
variables without pretending they automatically compose:

```bash
python3 tools/portability_matrix.py lookup "VS Code"
python3 tools/portability_matrix.py variants "VS Code"
python3 tools/portability_matrix.py variants "VS Code" --control ipad_companion
```

The `vscode-dev × remote` and `vscode-dev × ipad_companion` variants
share the same editor implementation and browser route, but differ in
their human-control variable. The former is *assessed as poor*, the latter
as a *candidate*. Neither has been verified on the actual TV. When
comparing a remote code-server backend, additional variables change
(placement, host, state and network). A follow-up experiment must identify
which variables were changed and whether comparison is still meaningful.

For each candidate, an agent should provide:

1. **Preserved intent:** what does "get me X" require in practice?
2. **Assignments:** which relevant factor values were selected, and which
   remain unknown?
3. **Binding constraints:** what would make the combination fail?
4. **Evidence level:** upstream reference, repo integration, physical TV
   observation, or untested hypothesis—never silently promote one into another.
5. **Discriminating experiment:** the cheapest test that could reject a
   plausible candidate or reveal a reusable adaptation.

**For an unknown title, the answer is `unassessed`, not `impossible`.**
A known application might be feasible under one assignment and unusable
under another, especially because TV remote usability is independent of
binary execution. A demonstrated incompatibility must identify the
constraints under which it was observed.

## Connection to the other Layer 2 purposes

- **A (Creative Playroom)** may swap a verified Snake visual asset while
  preserving the game rules; the content-addressed artifact variable
  changes without rewriting the renderer.
- **C (Android Dojo)** trains the human to think in variables, generate
  counterexamples, and ask agents stronger questions: "Would changing
  only controls make this usable?" rather than "Does it run?"
- **D (Compute)** discovers potential executors and storage capabilities
  (GitHub, an agentic Python worker, a real computer); these are offerings,
  **not** permission to invoke them. The broader agentic kernel remains an
  independently evaluated systems-engineering goal.

## Current stopping boundary

The actual tested implementation is a **small 2-factor variation** of
execution route and control, plus honest source/evidence metadata. This
is an initial slice of the general algebra, not a full solver, capability
checker, interactive adaptation agent, native Windows compatibility layer,
or proof of physical Fire TV playability.

**Next meaningful evidence:** run a single VS Code or emulator candidate
on the real TV and compare D-pad-only to iPad-assisted control, holding
the execution route fixed. Feed any resulting evidence into the
portability index, noting versions and device conditions. Expand to more
variables only when an actual request requires them.
