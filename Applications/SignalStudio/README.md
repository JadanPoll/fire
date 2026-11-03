# Signal Studio — independently packaged Playroom experiment

The first **Fire AppOS** reference application is adapted from
[Manifold Console's Playroom Studio](https://github.com/JadanPoll/Manifold_Console/tree/main/Playroom/Studio).
It visualizes sine/triangle/square signals using the same five-field recipe
vocabulary. It does **not** import Manifold's Python/Tk GUI or run an agent.

- `appos.json`: identity, Android launcher and capability *declarations*
- `src/main/assets/example.recipe.json`: constrained, nonexecutable recipe
- `SignalMath.java`: pure signal algorithm
- `SignalActivity.java`: TV-oriented UI using the **optional shared Remote Adapter** instead of hand-written Fire key timing
- `src/test`: deterministic reference and invalid-workload tests

## Interaction

D-pad UP/DOWN selects Waveform, Frequency, Gain or Smoothing.
LEFT/RIGHT changes the selected parameter; OK resets that parameter to the
bundled recipe's defaults. BACK exits through Android's normal Activity
lifecycle. The calculation is performed **only on state changes**, not in a
mandatory high-frequency redraw loop; there is no background service,
network request, microphone permission or sensor claim.

## Develop and publish

From the repository root, with Android SDK and Gradle installed:

```sh
python3 tools/check_appos_contract.py
gradle :remoteinput:testReleaseUnitTest :signalstudio:testReleaseUnitTest :signalstudio:assembleRelease
```

GitHub Actions builds `firetv-signal-studio.apk`, produces a SHA-256 in the
rolling release notes, and publishes the matching `appos.json`. Fire Console
App Catalog installs the APK through the existing explicit Android installer.
Once installed, **Apps** discovers it using the ordinary Leanback launcher
registry. Fire Console's Java implementation need not change.

## Evidence boundary

Successful compilation proves that the APK builds, not that its focus,
frame timing, button mapping or redraw are verified on physical Fire OS.
This reference opts into the shared input library, but other native Android TV apps need not. The app has its **own Android UID and data directory**; the v1 manifest
doesn't grant or enforce privileges. All APKs use the current lab signing
identity, which is public and unsuitable for production trust.

Acceptance on Fire TV: install from App Catalog, launch from Apps or the TV
launcher, adjust all four parameters with the remote, repeatedly press Back
and relaunch, and verify Browser and Fire Console keep their own state.
