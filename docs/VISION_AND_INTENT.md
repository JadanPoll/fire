# Fire Console — holistic vision and agent-facing intent map

> **Status: user vision + provisional high-level interpretation, not a completed
> feature list or an authorization to deploy.** Interpret this alongside the
> [Logical Cloud Constitution](https://github.com/JadanPoll/Logical_Cloud/blob/main/CONSTITUTION.md)
> (especially LC-01, LC-02, LC-03, LC-05–12 and LC-16). This is the *why* and
> the hierarchy of experiences; mechanism-specific evidence belongs in the
> linked architecture and implementation files.

## 0. The holistic big picture: "What can't I do with this?"

The human wants a playful, open-ended, increasingly capable **TV-centered
computing and creative environment**, usable from TV and also supported from an
iPad or another available device. The motivating feeling is: *"What can't I
do with this? I'm going to have so many cool ideas."*

This should not become a narrow game launcher, an Android debugging utility,
or another engineer-only build tool. The point is to make it easy to **imagine,
play, create, inspect, improve, and share**, without the human administering
the tooling. Big ideas are expressed through text or voice. High-level agents
make interpretations and sensible plans visible; independent agents handle
source research, implementation, compilation, testing, and routine integration.
The human should be able to change their mind without starting over.

Success is judged first by the *experienced loop* (idea -> credible
interpretation -> observable candidate -> play/explore -> persistent revision),
not by how impressive any one internal tool is.

## Three explanations for this vision

**General intent (human README introduction):** The first section above is
the stable top-level purpose: a playful, open-ended TV-centered environment
for ambitious games, persistent creative projects, Android exploration,
and offshore Compute. A person can read that and know why the project exists
without examining Android code or build mechanisms.

**Functional intent (revisable sets and supersets):** Sections 1–3 describe
the critical families of capabilities, their promises, their intersections,
and meaningful alternatives. They answer what the experience should do
and invite questions such as *"why a separate Compute channel?"* or
*"why should projects persist rather than produce one-off demos?"*
A human may change one of these choices without resetting the whole vision.
These are intentions, not proof of implemented behavior.

**Implementation and evidence (agent-facing, paper-like provenance):** Section
5 links to existing authoritative implementation documents, Android code,
research precedents, and CI evidence. When asked *"why implement A rather
than B?"*, a high-level agent first answers in terms of the selected
functional promise, then retrieves the actual source-backed choice,
alternatives, constraints and tests. Citation proves where an idea came from,
not that it works here. A newly onboarded agent should follow these links,
not replace existing decisions based on its training preferences.

These are three **navigable levels of explanation**, not three bureaucratic
copies of the same implementation. A new implementation may change only
the third level; a new user preference may revise the second; a revised
holistic purpose can change the first. Each change propagates only where
its actual implications require it. See Logical Cloud LC-16.11–16.14.

## Layer 2 — accepted refinements (2026-11-04)

These supersede the earlier broad choices. **Compute has a bounded device-and-capability discovery direction** (including GitHub, hardware and virtual agents), while its larger independent purpose remains unresolved. Neither a heavyweight universal executor nor a required cloud service is implied.

| Functional set | Current human intent | Implementation uncertainty left to agents |
| --- | --- | --- |
| **B — Portability / REA frontier** | A research-scale, gradually refined knowledge base of which applications and games can be experienced on the TV, how and with what constraints; unknowns can spawn original research | Source-grounded feasibility investigations, honest incompatibility findings, and eventual adaptation workflows |
| **A — Creative Playroom** | Immediate fun for family, friends and downtime: enjoyable, persistent games and ideas iteratively improved by agents using mostly existing technology | Good game experience, independent changeable assets, quick revision, and unchanged existing work |
| **C — Human capability / Android Dojo** | Fundamentally improve the human's intuition, question formation, creativity, experimentation and agent direction toward “What can't we do with this TV?” through ungraded projects from scripts to system internals | Which experiences change the human's mental model, surface unsuspected possibilities and inspire better questions; no completion scores as proxy |
| **Modular projects and assets** | Independent GitHub projects may evolve artwork/animations outside game code; eventual dynamic loading with smart caching and rollback | Exact manifest, trust, content delivery and cache boundaries; no universal loader is assumed implemented |
| **TV-first interaction** | TV is primary; eventual iPad companion offers bidirectional low-latency control/inspection, useful especially for desktop-like apps | Raw versus semantic controls, authorization, stream protocol and integration |
| **D — Agentic computing kernel** | Real productive work through TV as a control surface; discover and compose physical/virtual capabilities, including GitHub storage/workflows and agent workers, without requiring human infrastructure management | Systems-engineering integration and real work completion; registry discovery alone is insufficient, and unattended privileged actions are out of scope for this lab |

**Android Dojo stack range:** a tiny scripted experiment may be as intellectually deep as a native or OS-level experiment. [Stack map and problem catalog](ANDROID_DOJO_STACK.md) document the actual Fire OS 7 privilege boundaries, without prescribing progression from easy to hard.

**C-0 — Human capability amplification:** The success of Android Dojo is not the number of challenges completed, apps built, or terms memorized. It is whether the human can now predict more, imagine more, ask better agent questions, and choose more informative experiments about this TV. Challenges and agents are subordinate means. The [Possibility Garden](../app/src/main/java/dev/jadan/fire/IdeaGardenActivity.java) is an early instrument for discovering and returning to the human's questions, not a substitute for genuine learning.

**B uses Logical Cloud's original factor-variable method:** portability is a constrained choice of experience, implementation, execution, controls, presentation, artifacts and placement rather than a yes/no property of a particular binary. The [algebraic portability model](PORTABILITY_FACTOR_SPACE.md) and initial `execution × control` queries preserve evidence and honest uncertainties.

**Distinct purposes and stopping criteria:** A = present-day fun and creative iteration; B = REA-style asymptotic feasibility frontier; C = human understanding and idea-generation; D = real work through an agentic computing substrate. Cross-project reuse is valuable but **none of these is primarily an engineering toolkit for the others**. Keep each experience enjoyable or useful on its own.

**Layer 2 communication decision (2026-11-05):** The existing [Fire Inbox](
https://fire-inbox-jadan.brinyduck2.chatgpt.site/) is the preferred shared
human/agent message front door. **HTTPS** provides durable questions,
proposals, opinions, acknowledgments, decisions and result retrieval;
**WSS** is the intended live bidirectional extension, with HTTPS catch-up
when disconnected. Agents and devices can address one another by logical
recipient and project ID. **GitHub** remains authoritative for produced
source, game assets, tests, builds and releases—not the Inbox. The human
owns meaningful intent decisions; the Inbox shows agents' proposals and
verified results separately. No auth/MCP requirement is added to this
public, non-sensitive prototype. The exact message broker and WSS route
are not yet verified on the live Site. See the [canonical Logical Cloud
coordination contract](https://github.com/JadanPoll/Logical_Cloud/blob/main/docs/coordination-transport.md).

Two cross-cutting promises: (1) useful experiences survive unrelated agent
work, disconnections and failed optional experiments; (2) discoveries in Dojo,
Playroom and portability research can be referenced without duplicating work.
These are intent constraints, not evidence that all the relevant infrastructure exists.

**Move to implementation rather than more Layer 2 decomposition:**
[First bounded executable slice](FIRST_SLICE.md) — tiny native 2D probes,
offline portability lookup and an ungraded host-side Android Dojo ledger.
Unknown software is *unassessed*, never automatically declared impossible.
Do not equate building or listing a route with actual physical-TV usability.

## 1. Critical sub-big-pictures

### 1A. Ambitious play — games are possible, not restricted to tiny demos

**Purpose:** The owner can explore and play ambitious games, choosing a
technically sensible route for the particular experience rather than being
restricted to one engine or implementation style.

**Promise sought:** An agent can tell what kind of game experience is meant,
investigate a credible route, and produce an honest, playable next candidate.
Possible routes include an existing game/app, a compatible native engine,
a browser game, emulation where lawful/appropriate, or remote/cloud execution
when it genuinely improves the result. These are implementation options,
**not user-selected product limitations**.

**Selected interpretation:** Build an agent-queryable portability and adaptation
knowledge base for existing games **and applications**. It tracks execution,
interaction and evidence independently. Unknown entries invite research,
not automatic rejection; successful adaptations become reusable recipes.
Original 2D probes remain the separate Playroom objective.
A particular game's compatibility and local/remote route are implementation
questions for the agents; ask which existing game *experience* matters first
when it would materially change the plan.

**Boundary:** Performance, device capabilities, content rights, online
services, and controls may constrain a specific title. Keep those local to
that project; do not turn them into a blanket limit on creative exploration.
No claim that general AAA-game compatibility or every engine is implemented.

### 1B. Playful creation — someone can *ask for an idea* and keep improving it

**Purpose:** Even a family member who loves Roblox-like imaginative play
should be able to suggest a cool game or interactive world without learning
programming, get a genuinely playable prototype, and return to it later.

**Promise sought:** One conversation can create a **named, persistent
project**, a playable version, and repeatable subsequent changes. "Make the
obstacles bigger" or "I like the previous character" modifies that project
rather than silently generating a disconnected replacement. Save meaningful
source/artifact identity, variants, user choices, and runnable versions;
preserve prior work and an easy way to return to it. No special similarity to
Roblox's proprietary implementation is required or promised.

**Current first slice:** An intentionally **tiny 2D game catalog** for
revealing engine/input/rendering limitations before attempting large projects.
Future ambition remains instantly playable prototypes that can expand.
The choice of browser/native/streamed preview is usually
for agents to resolve according to the actual idea. Ask about the desired
game experience, not compiler or engine preferences, when a genuine
creative ambiguity remains.

**Boundary:** Child-friendly discoverability and uncomplicated controls are
essential; privileged access, account setup, or data collection are not
prerequisites for ordinary prototyping. Appropriate safety, permissions, and
content restrictions remain applicable.

### 1C. System exploration — learn how Android/Fire OS really behaves

**Purpose:** Instrument the system to *understand* Android and Fire OS, not
necessarily to build another application. Treat low-level observability as
an inviting experimental playground.

**Promise sought:** Native input/focus behavior, lifecycle, process/memory
and performance observations, graphics/audio paths, package management, and
other meaningful system mechanisms become visible when permitted. Show raw
evidence and interpretation side by side, with copyable traces and small
experiments ("press OK and show what the system actually delivered").
Support learning by comparing expected with observed behavior.

**Selected interpretation:** A **problem-first, ungraded Android Dojo**:
interesting problems, navigable topic/glossary entries, concrete tacit skill
checks, and partial progress that can be resumed. Real Fire OS experiments
come before claiming intuition or physical behavior. Input/timing, process
lifecycle and graphics/audio remain useful challenge families.

**Boundary:** Android/Fire OS permissions and supported APIs are real.
Read-only diagnostics and app-local probes come before privileged access.
Do not claim access to internal system telemetry when the OS withholds it,
and do not weaken device security silently. This is about curiosity and
insight rather than requiring an intrusive global service.

### 1D. Compute — the clever offshore extension of the console

**Purpose:** The TV is a friendly *control surface*, not a requirement that
every compiler, agent, renderer, or model run on the TV's CPU. The Compute
channel is a particularly important realization of the Logical Cloud idea:
ask here, have suitable workers do heavy work elsewhere, and inspect or play
the result here or on another device.

**Current status: holistic purpose deferred.** The existing Compute channel
is retained. It might become a library capability for dynamically enhanced
games or remain an exploratory channel; neither has yet been selected as the
compelling independent destination. The broader promise below is a possible
future direction, not a current build requirement.

**Promise sought:** The person can express intent on TV/iPad, choose between
meaningful interpretations where needed, follow useful progress and
alternatives, receive an early playable/inspectable result, and return to
the same project to make changes. The substrate can choose suitable
available execution, build, cache, preview, storage and transport
arrangements without exposing routine infrastructure management to the person.

**Boundary:** This is a *direction*, not a statement that general autonomous
cloud execution or remote compilation already works end-to-end. Current
Compute supports an existing paired host path and a Cloud handoff; see
[Compute and native apps](CONSOLE_COMPUTE_AND_APPS.md). Permissions, device
resources and trust still apply, but uncertainties should usually remain
inside the responsible implementation factors.

## 2. Shared experience contracts and separable factors

These concerns should be factored so an agent can replace one without
rebuilding or re-explaining all others:

| Meaningful factor | Stable user-facing idea | Example replaceable implementations |
| --- | --- | --- |
| Idea/intent and project identity | "This is the game/experiment I keep working on" | Prompt history, named version, experiment branch |
| Interpretation and goal breakdown | Human sees what agents think they mean, and a sensible next outcome | Two meaningful choices, concise plan, reversible default |
| Game/app semantics | What the playable experience actually does | Native game, compatible browser prototype, remote execution |
| Input and device adaptation | The same intended action behaves predictably | Fire remote, touchscreen, keyboard, optional native adapter |
| Experiment/inspection | I can see what the system is doing | On-device diagnostics, ADB trace, test harness, visualization |
| Compute and compilation | Heavy work happens wherever is sensible | Existing CI, agent workspace, paired host, future provider |
| Persistence, variants and return | The idea survives closing/reopening and agent turnover | Project records, source revisions, immutable artifacts |
| Presentation and delight | Useful even for someone who isn't a programmer | Playroom/Apps/Tools, accessible playful UI, previews |

An unfinished factor should not ordinarily block progress against the
reasonably stable contract of another. Agent B can simulate/mock A while
A is still being implemented; integrate and reality-check at meaningful
intersections. A compositional mismatch is ordinarily an iteration problem
before it is evidence of bad architecture.

## 3. How the high-level agent should clarify this vision

Do **not** turn the user into a project manager. For a broad request, produce
a short **holistic interpretation**, its few critical **sub-big-pictures**,
and a sensible **candidate experience/plan**. Where a sub-goal has materially
different interpretations, show two or three *outcome-level* options and
invite the human to select, combine, reject all, or freely correct them.
Recursively clarify only unresolved differences that change what success
will look like. Offer a reasonable default when the next action is reversible.
Stop when the human can recognize that their idea was understood and knows
what kind of result to expect—**intent alignment, not implementation proof**.

An example when a person says "I want to make a cool world and play it":

- **A — Play instantly:** a small playable scene first; extend it over time.
- **B — Build an ambitious game:** an engine-backed project with a longer
  development path and early milestone previews.
- **C — Explore mechanics:** a playful testbed showing how movement, physics,
  audio and the TV remote work.

Agent recommendation might be **A with a path to B**, while keeping C as
an optional inspection view. These are *illustrative hypotheses*, not choices
the owner has accepted. "None of these; I meant something else" is a valid
and valuable answer.

After alignment, agents can scale up/down and work independently, each
recovering only its intent neighborhood, current implementation, precise
pre-existing references and evidence. The human need not manage those workers.

## 4. Sensible high-level path (proposed, not imposed)

**First useful loop:** launch a tiny native Playroom game, make a measured
D-pad input change or small rule change, and compare the actual experience.
Alongside it, query one portability record and resume one Android Dojo
challenge note. A later asset experiment should update one sprite independently
of core game code. This proves small real links rather than a hypothetical
one-prompt-to-AAA-game service.

**In parallel:** preserve and improve the existing low-level input/Android
instrumentation with meaningful experiments; preserve the current native
app/catalog paths and Compute channel. Use the existing quick tests and
emulator for narrow questions, not as a mandatory gate before every idea.

**Expand as ideas demand:** offer routes for more ambitious titles/engines,
multi-device playback, more involved experiments, and remote compute.
Scale agents according to genuine independent work, not administrative
fascination with orchestration.

**Result expected by the person:** a playful home where games and experiments
are discoverable; a new idea can become a prototype; that prototype persists
and can be improved; an advanced project has a clear, truthful route forward;
and the OS can be explored at meaningful technical depth.

**Stop/redirect:** If user-facing progress is blocked by installer trivia,
repeated account/device setup, duplicate context interviews, or unresolved
optional tests, fix the friction or decouple that work. If a desired
experience cannot honestly be demonstrated, return a useful partial preview,
the specific impediment and alternative paths—not generic "not possible".

## 5. Current evidence and boundaries for incoming agents

**Already supported in this repository:** six-channel native Console,
Browser, Playroom web-game pinning, installed independent-app discovery,
Input Lab, the App Catalog, Compute's paired-host and cloud handoff, and
Signal Studio as a stand-alone reference app. Android TV emulator CI has
shown some launch/input flows, but is *not* proof of every Fire OS behavior.

**Not established here:** fully automatic idea -> original persistent game
creation -> remote build/deploy -> iterative game revision for a family user;
universal game-engine compatibility; a general agent coordinator; free
cross-device high-fidelity streaming; unrestricted Android internals access.

**Authoritative local starting points (do not copy their contents):**
- [Console and native-app boundaries](CONSOLE_COMPUTE_AND_APPS.md)
- [Source-backed console, input and prior art](FIRE_CONSOLE_ARCHITECTURE.md)
- [Current test routes and evidence limits](TEST_LOOP.md)
- [Optional app contract and references](FIRE_APPOS.md)
- [Independent app onboarding](../Applications/AGENTS.md)
- [Logical Cloud: intent-set doctrine](https://github.com/JadanPoll/Logical_Cloud/blob/main/docs/constitution/intent-sets.md)
- [Logical Cloud: research-style provenance](https://github.com/JadanPoll/Logical_Cloud/blob/main/docs/constitution/provenance.md)

This document records the owner's stated vision and the agent's provisional
organization of it. It makes no claim that the proposed candidate paths
have been built or that outside implementations were inspected for them.
Any consequential implementation needs its own actually inspected, pinned
source precedents and scope-appropriate evidence.
