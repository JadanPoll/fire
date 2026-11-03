# Fire Console — paired Codex executor and hidden terminal

## What this actually does

The **Codex** channel can now submit a prompt to a user-operated remote executor running the official Codex CLI. This is a real `codex exec` invocation on a trusted computer, not a fake hosted chat UI and not local native Codex execution on the Fire TV. The Fire TV only displays progress and final agent text, and offers refresh/cancel/recover. Source edits occur in the remote workspace.

**Hidden local terminal:** Focus the **Codex** tile on Fire Console and *long-press OK* to open a terminal using Fire Console's existing Android app UID. A visible fallback is **Codex → OPEN LOCAL TERMINAL (APP SANDBOX)** if Fire OS does not deliver a long click. This terminal runs `/system/bin/sh -c`, without root, ADB, Termux integration, remote shell or shared user-space packages. It has an explicit Run button, Stop, Copy, a per-command 15-second limit and a 24 KB output limit. App-private working directory starts at `files/workspace`.

The terminal is **not a security sandbox against the user**; a shell can operate with the permissions of this app, including its storage. It is deliberately not exported via Android intents or connected to the network. This is an advanced local feature for the device owner, not the mechanism used to accept remote tasks.

## On a trusted computer

1. Install the **official Codex CLI**, following [OpenAI's install instructions](https://learn.chatgpt.com/docs/codex/cli) or, where supported, `npm install -g @openai/codex`.
2. Authenticate locally on that computer with `codex login` (or `codex login --device-auth` on headless machines), then check `codex login status`. A ChatGPT subscription login is supported; using an API key instead uses API billing.
3. Create a **dedicated workspace** for experiments; don't point this bridge at your home directory or a workspace with sensitive data.
4. Generate a random pairing secret locally: `python3 -c "import secrets; print(secrets.token_urlsafe(32))"`. Store it in the local `FIRE_CODEX_TOKEN` environment variable, not in GitHub or an APK. Run `python3 tools/codex_executor.py --workspace /absolute/path/to/experiments`. The script binds **only 127.0.0.1:8769**.
5. Provide the Fire device a trusted **HTTPS** endpoint connected to this local port using a private TLS reverse proxy/tunnel you administer. Do not expose plaintext HTTP or ADB to the internet. Configure access controls at the edge in addition to the pairing token where feasible.
6. In Fire Console → **Codex**, enter only the HTTPS origin (e.g. `https://codex.example.net`) and the pairing token; select **TEST EXECUTOR PAIRING**. The pairing token exists in Activity memory, not SharedPreferences or the published APK. Enter a prompt and select **RUN DRAFT WITH CODEX ON PAIRED WORKSPACE**.

The executor requires Python 3.9+ and an installed `codex` executable. It does not install Codex or log in for you. No long-running service, public domain, remote workstation, or tunnel has been provisioned by this repository.

## Network and task semantics

Protocol v1, exclusively behind HTTPS:
- `GET /v1/health`: authenticated capability probe
- `POST /v1/jobs`: `{"prompt":"..."}`; starts one job
- `GET /v1/jobs/{uuid}`: read progress/result
- `GET /v1/jobs/latest`: recover last job if POST succeeded but its response got lost
- `DELETE /v1/jobs/{uuid}`: request cancellation

A session is limited to a single running job, 8,000-character prompt, five-minute execution window, bounded JSON output and 50 recent job records, all in memory. After a server restart, jobs are not recoverable. **If a POST times out, choose Recover Latest Job before resubmitting.** Cancellation may not undo edits already written by Codex.

The executor invokes `codex --ask-for-approval never exec --json --sandbox workspace-write --skip-git-repo-check PROMPT` with a fixed executable and fixed working directory. This is intentionally a **restricted, non-interactive** profile: prompts cannot override paths, choose binaries, request root, disable the sandbox, or gain network/ADB capabilities. The agent can still edit files inside the allowed workspace; use a dedicated sandboxed OS user or disposable VM for untrusted tasks.

There is no browser-based arbitrary shell endpoint; the private terminal and the remote Codex runner are separate trust domains.

## Validation

```sh
python3 -m unittest discover -s tools -p 'test_codex_executor.py' -v
python3 tools/check_console_manifest.py
# GitHub Actions: :app:testReleaseUnitTest and :app:assembleRelease
```

Tests verify basic token rejection, localhost protocol, fake Codex JSON output, recovery and terminal Activity export contract. They do **not** prove real Codex authentication, on-TV long-press delivery, tunnel reachability or actual device performance. For those, run the above setup with a real authenticated Codex CLI and test on the physical Fire TV, observing status and result.

## Direction: factorized Manifold-style system

Keep one **capability graph** across local and offshore execution, rather than building unrelated tools:

| Capability | Local Fire | Remote trusted executor | Safety/consent |
| --- | --- | --- | --- |
| Codex | prompt/status/approval UI | Codex CLI and editable workspace | explicitly paired; sandboxed |
| Terminal | app-UID commands | deliberately absent from v1 API | local physical control only |
| Assets | native app-private catalog (future) | content-addressed store, export/import (future) | per-asset user approval |
| GitHub | browser and pinned repos | checkout, build and provenance (future) | scoped credentials on executor |
| PDFs | Android viewer/import (future) | parse/index/render (future) | local-first sync |
| Games | existing Playroom HTTPS pins | artifact build/store (future) | versioned manifests |
| ADB/telemetry | explicit bounded device diagnostics | privately tunneled collector (future) | user approval, no public port |

The next increment should be an **asset library** with SHA-256 identity, title/type/provenance/creation time, import/export, and a `games` manifest that pins a particular asset version instead of only a URL. The offloading and return path should be task-oriented: task ID, selected capability, input assets, result assets, evidence, and reconciliation after dropped connections. Avoid giving a remote worker a generic Android command-execution endpoint.

## Source constraints

- [Official Codex CLI reference](https://learn.chatgpt.com/docs/cli/reference) for `exec --json`, `--sandbox workspace-write` and authentication.
- [Android Activity/exported contract](https://developer.android.com/guide/components/activities/intro-activities) for unexported local terminal.
- Existing `docs/ARCHITECTURE.md`, `docs/FIRE_CONSOLE_ARCHITECTURE.md`, `docs/NATIVE_CODEX.md` remain authoritative for original package/scope and Fire OS limitations.
