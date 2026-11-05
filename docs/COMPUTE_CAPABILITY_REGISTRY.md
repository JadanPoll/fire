# Compute capability registry v0 — devices are physical or virtual

**Intent:** A person can tap a device-like card in Compute and see hardware
facts or enumerated capabilities. The same registry shape may describe a
Fire TV, a Raspberry Pi, GitHub, a Codex-equipped workstation or an agentic
Python session. GitHub's *project*, *versioned assets*, *Actions workflow*
and *published artifact* are distinct addressable resources/capabilities,
not different machines the user must administer.

This is a discovery prototype, **not** a cloud provider, job router, or
general Compute product commitment. Existing Compute / paired Codex controls
stay intact. The Console displays its *bundled snapshot* from the
authoritative `research/compute/devices.json`; it does not yet contact
an external registry. Virtual platforms are labeled "provider not configured",
and static offerings are **not** device presence or workflow execution.

## Open on TV

**Compute → DEVICES / CAPABILITIES → select a device card.**

The Fire Console card includes real Android Build-reported model, API and
ABIs. The GitHub card enumerates versioned storage, asset distribution,
Actions static builds, and workflow execution as *upstream* capabilities.
The Codex and Python virtual agent cards describe candidate capabilities
without asserting a connected backend. Cards are simple native TV focus
targets, not a new launcher or permission broker.

## Prototype standard: GET only, no authentication

For development experiments, run from a Fire repository checkout:

```sh
python3 tools/compute_registry.py validate
python3 tools/compute_registry.py list
python3 tools/compute_registry.py serve --bind 127.0.0.1 --port 8768
```

On the **same host** (an unrelated terminal or local agent), issue:

```sh
curl 'http://127.0.0.1:8768/v1/announce?id=raspberry-pi&name=Raspberry%20Pi&kind=physical&offers=python.execute,compile.native'
curl 'http://127.0.0.1:8768/v1/devices'
```

A GET announcement contains stable `id`, human-readable `name`,
`kind` (physical / virtual_agent / virtual_platform / service), and a
comma-separated list of stable offering IDs. The response includes
`accepted: true`, `verified: false`, and expiry after 300 seconds unless
announced again. The catalog always includes its known seed records;
transient unauthenticated claims are held in memory only. Duplicate
announcements update the transient record. A dynamic record cannot overwrite
a reserved built-in ID such as `github`.

`GET /v1/devices` returns a JSON object with `schema_version: 1` and a
`devices` array, each with `id`, `name`, `kind`, `availability` and
`offers` (`id`, `label`, `class`, `status`). The API is intentionally
small enough for another agent to implement without a common agent SDK.

**This is unauthenticated public metadata only.** Anyone with network
reachability can announce arbitrary non-reserved claims, including another
agent's transient identifier; do not equate this with authenticated identity
or permission. It accepts no commands, workflows, GitHub tokens, IP
addresses, network credentials, secrets, executable payloads or private
files. By default it binds to localhost. If publicly exposed, treat all
announcements as untrusted and expect spam, missing updates and impersonation.
Do not use this as a gateway for privileged or destructive operations.
`GET /v1/announce` is intentionally a toy mutating GET for cheap agent
experiments; a durable production protocol may revise transport semantics.

## Integration status and future experiment

The earlier [Fire Inbox](CONSOLE_COMPUTE_AND_APPS.md) domain is only a
possible future place to expose the registry. **No endpoint has been
deployed there or promised to exist.** The TV's bundled gallery and the
host's HTTP discovery server are **not yet connected**; the simplest next
experiment is to let Fire Console request a reachable read-only HTTPS
`/v1/devices` endpoint, preserving the static cards as an offline fallback.

An agent's eventual routing decision must distinguish:
- **advertised capability** (can be displayed);
- **available provider** (actually reachable);
- **allowed invocation** (separate authority);
- **result** (build, observation or artifact, with measured evidence).

Having an Actions workflow in the GitHub card does *not* cause a workflow
dispatch; that remains a distinct executable tool action with separate
permissions and evidence.

## Why this factors well under Logical Cloud

The physical location is one axis, the resource capability another, the
application intent another. A GitHub workflow may compile a native APK while
an external editor changes sprites, and Fire TV launches the resulting
verified artifact. GitHub's repository branch is a versioned artifact
source; its workflow is an operation; the Fire TV is the user-facing
execution/interaction surface. The contract permits these factors to
evolve independently without making all apps depend on a universal SDK.
