# Compute and AppOS — consolidation of the experimental Fire Console

## Parent goal

An agent can independently build a **real native application**—anything
from a document editor to a graphics renderer or GBA-class game—then link
it into Fire Console without learning our test-era UI or replicating
remote-control work. Fire Console is a **home and a thin collection of
optional adapters**, not an engine framework.

Independence and cooperation are *both* first-class. Do not force an app to
adopt our abstractions merely because we have built them.

## Two legitimate app modes

1. **Native pass-through (default).** Build any normal Android TV APK using
   Java, Kotlin, GameActivity + NDK C/C++, Godot, Unity, libretro or other
   compatible engine. Expose a Leanback launcher. Android owns its process,
   native audio/video, storage permissions, background rules and input.
   Fire Console's Apps channel discovers it automatically. The curated
   App Catalog is an *optional* installation/update distribution path;
   `appos.json` is optional and currently specific to repository-local CI.
   Android app signing and permissions remain in force.
2. **Native + optional remote normalization.** Link
   `AppOS/Remote` and map semantic actions to an app's existing controls.
   Duplicate OK/down/up, long-press timing, repeat suppression and focus
   loss are handled once. This is **in-process**, not an injected
   cross-app global remapper. It does not replace the game's renderer or
   audio engine. The reference is Signal Studio.

Native engines have direct access to the Android facilities their own
app has permission to use. Avoid a mandated plugin ABI, fake universal
AppOS filesystem, compulsory service process, proxy renderer or separate
GBA emulator implementation.

## What was deleted / moved

The top-level Home is intentionally **six channels**, in two rows:
Inbox, Browser, Playroom, **Compute**, Apps and Tools. The Playroom channel
hosts user games, including the GBA bridge. The installed Canvas APK remains
under Apps; Voice diagnostics remains under Tools; neither warrants
permanent Home real estate.

- **Deleted:** Dev Bridge's second GitHub APK download, checksum, installer
  and retry implementation. `CatalogActivity` is now the single verified
  Console installer/update mechanism.
- **Deleted:** `MainActivity`'s second raw key-event ring buffer and
  share-code. `InputLabActivity` remains the input diagnostics source.
- **Moved to Advanced:** old WebView Browser Lab, raw input lab, hardware
  precheck, browser handler diagnostics and extra browser fallbacks.
  No Browser cache or state is cleared.
- **Retained:** real installed TV apps, user-pinned games, Fire Browser,
  Codex executor protocol, remote job recovery/cancellation, local terminal,
  Voice test, Input Lab, Catalog and Android's installer.
- **Added:** optional shared remote adapter, proven on Signal Studio.

These deletions are about common-mode maintenance and user intent, not
merely hiding obsolete buttons. There is no new background service or
generic agent runtime.

## Compute: first offshore-compute channel

The Compute Home tile uses cloud/server artwork and opens the **existing
CodexDeskActivity**, now presented as Compute rather than a second app
package with duplicated job logic. The visible flow is:

1. Type/dictate an intention into a draft.
2. **RUN ON PAIRED HOST** uses the existing HTTPS `/v1/jobs` Codex
   executor after explicit pairing. Or **COPY DRAFT & OPEN CODEX** opens
   Codex Cloud in Silk without needing any executor setup.
3. Observe accepted job ID, live status, result and an explicit
   **REFRESH / RECOVER**, **STOP JOB**, and **COPY RESULT**.
4. Advanced contains browsing, terminal and hardware diagnostics. Failed
   operations do not block drafting the next intention.

Compute **is not** another Codex binary, cloud service, remote desktop
viewer, model subscription, or account system. Remote hosts/devices are
future providers, not new UI channels. This first provider is the
existing trusted `codex_executor.py` with the versioned HTTPS API.

The app stores a draft, last job ID and a *successful* HTTPS host origin
locally to reduce re-entry. It does **not** store the pairing bearer token:
this repository's intentionally public lab signing identity makes
persistent credentials unsafe. The host still needs a trusted private
HTTPS proxy/tunnel and a logged-in Codex CLI. Neither is automatically
provisioned by this change. The browser fallback is explicit and never
pretends a copied draft was submitted as a Codex job.

See `docs/CODEX_EXECUTOR_SETUP.md` for the actual host contract.

## Platform constraints / future decisions

Android's ordinary per-app window owns its KeyEvents; a normal app
cannot remap another app's input globally. This makes in-process
adapters and direct pass-through valuable and a generic remote broker
unnecessary. Amazon MediaSession can serve media controls, but is not a
general remote-key injection escape hatch.

Do not build remote-device streaming, generalized service arbitration,
unreviewed package loading, background observability, automatic pairing,
GPU quotas or arbitrary IPC until a real end-to-end workload establishes
their necessity. Latent resource protection and Switchboard ownership
become appropriate only when multiple actual consumers justify them.

## Concrete acceptance (not simulation)

- An independent app with **no Fire SDK** appears in Apps and works using
  normal Android TV focus/control input.
- Signal Studio still navigates and exits with the adapter, and duplicates
  never cause double actions.
- Compute keeps a draft through a browser/Console round trip; unpaired
  users can open Codex Cloud without reaching a setup dead end.
- A separately provisioned trusted executor accepts one real task,
  reports a job ID, allows status recovery, and preserves the result.
- Fire Browser data, pinned web games and existing installed application
  data are unchanged.
- A Fire OS physical remote confirms the last few timing/IME assertions.

If a candidate addition does not remove agent-specific glue or demonstrably
reduce whole-flow friction, it should not be added.
