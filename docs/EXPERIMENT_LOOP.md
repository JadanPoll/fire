# Tightening the experiment loop (REM application)

Source: [The Richer Engineering Method](https://github.com/JadanPoll/Learning_Visualizations_Taxonomy/blob/main/Navigationals/The_Richer_Engineering_Method.md), especially the agentic-fluidity loop, system versus local efficiency, intention-level interfaces, explicit failures, and stopping criteria. Complement with RSM sections 2 (parent purposes), 5 (handoff certificates), 7 (architecture parsimony), and 10 (experiment economy).

## Parent objective

The system should allow the user to move from *an idea* to a *running experiment and visible consequential result*, and then steer the next attempt. **The capability is bidirectional and symmetric:** Fire TV is an active client AND a possible executor of Codex/app-server work; a PC/cloud Codex client may in turn invoke capabilities running on the Fire TV. Choose the physical execution location by actual ABI, RAM, process and trust constraints; never predefine the TV as a passive client. Model inference still occurs remotely unless an actual local model is chosen.

A good demo: "Try pointer motion with less acceleration" → visible accepted task → Codex edits code on a trusted remote host → CI publishes APK → device fetches it with explicit approval → user tests pointer → "that was worse" with attached telemetry → next decision.

Don't optimize one link in isolation. A faster build is irrelevant if requesting the build or reporting results remains awkward.

## Real current capabilities

- Source edits and Android builds through GitHub.
- GitHub Releases provide a stable APK location and checksums.
- Fire app can show device information, local D-pad tests, a catalog, and update/install prompts.
- Fire cannot silently install apps or intercept system-wide input as an ordinary app.
- Alexa Voice Remote's microphone button is owned by Fire OS; it is not a direct microphone sample stream to our APK. **However some Fire TV keyboards support dictation directly into an EditText.** The Codex on Fire screen now exposes a testable keyboard transcription field. See https://github.com/sanjayshr/firetv-dictation-helper and https://community.amazondeveloper.com/t/which-fire-tv-devices-support-keyboard-dictation-mic-key-in-third-party-text-fields/29808. Results vary by device/OS updates.
- Codex Cloud can be opened on Fire TV via browser; there is no verified native on-TV Codex binary or authenticated two-way task API yet. No command intake, authorized native Codex execution, results channel or observed physical-TV success is established.

## Smallest consequential experiments, in order

1. **Physical bootstrap:** Install the APK, open the catalog, launch Remote Canvas, record how input behaves; identify any actual device-specific installation friction.
2. **Voice and hardware preflight:** First try **system-keyboard dictation** in Codex on Fire; verify the text actually reaches the app. Compare with standard Android RecognizerIntent only as a baseline. Scan reported ABIs, available RAM and Android API level for a native Codex/Termux port; distinguish 64-bit userspace from a 64-bit chipset. Do not assume third-party mic-key access or Android 10 binaries on Fire OS 7.
3. **One true two-way Codex task:** From the Fire TV, speak/type "make Remote Canvas paint thicker", choose a local (if ABI/runtime is verified) OR trusted remote Codex executor, and observe task ID/status, diffs, build and result. Equally, Codex on a PC should be able to ask Fire to run a local experiment and return structured observations. Explicit consent for code execution and installs. The current clipboard/Web bridge is only an intermediate manual path.
4. **One evidence return:** Have the TV submit exactly the information that changes the next engineering decision: build/version, observed key sequence, selected outcome (better/worse), and optional screenshot where permitted. Show the report and let the user inspect/edit it before submitting.
5. **Replace input with speech:** Only when text loop and audio input have been proven. Speech transcripts are *proposed intentions*, not commands with automatic high-privilege effects.

## Why the Codex backend is separate

- A Plus/ChatGPT Codex entitlement is not equivalent to API billing or a redistributable API credential.
- Do not embed GitHub or OpenAI tokens in a public APK. Do not check them into GitHub.
- Use a trusted, authenticated relay and a trusted Codex environment. `codex exec` or the official Codex GitHub Action can automate bounded tasks; isolate untrusted code, restrict repository permissions, and require explicit user approval for risky actions.
- GitHub Actions can remain the builder, but that does not by itself supply a remotely callable ChatGPT assistant or a secure command intake.

## Interaction contract

- Primary action: **Ask / Try an idea**. Speak or type should be a replaceable input method, not separate products.
- Always show: **What I understood → What I'm doing → What changed → Run / Inspect → Tell me what you observed**.
- Expose actual errors and copyable logs on demand, without making users pass through diagnostics to do another task.
- Keep every task independent and recoverable. Never trap the user in a failed step or a stale conversation state.
- Return from the experiment to the parent goal when enough information is gained; don't manufacture more tests to justify activity.
- Measure end-to-end intent-to-observation and the number of manual interruptions, *not* merely tokens per turn or CPU build time.

## Candidate fun experiments after loop closes

- Voice-directed TV UI experiments: pointer dynamics, layout, animation, interaction semantics, timing.
- A "build me a tiny game" laboratory with controllable mechanics; observe real D-pad behavior.
- Media/visualization sketches (particles, kinetic typography, visual audio meters if audio is available).
- Cross-device hardware demos (Kinect, Raspberry Pi sensors) streamed as data to the TV rather than requiring the Fire TV to own those devices.

## Stop condition

Stop infrastructure work as soon as a single realistic idea can be issued from either side, applied by an appropriately selected Codex executor (including Fire itself when available), displayed as an installed variant, and evaluated with results flowing in either direction. Only then add voice/automation conveniences that reduce actual measured friction. It's acceptable for the initial step to require a manual approval or a human pasted task.

## Security note

Lab APK signing material is currently public. Any privileged bridge, token storage, personal data, or unattended remote execution requires an independently protected identity and deliberate authorization before enablement.
