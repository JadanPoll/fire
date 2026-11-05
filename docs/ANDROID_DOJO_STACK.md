# Android Dojo — how far up and down the stack can we play?

**Question:** How low-level can a Fire OS 7 challenge go, and how script-like
can it be while still teaching deep Android intuition?

**Answer:** These are *independent axes*. A short HTML/JS or remote Python
program may explore deep scheduling, input, rendering or network phenomena,
provided it demands predictions and actual device observations. A native C
program may teach almost nothing if the challenge is rote. Dojo is an
**ungraded problem graph**, not a progressive programming-language ladder.

## Stack as a navigable axis, not mandatory prerequisites

The canonical source is
[research/android_dojo/catalog.json](../research/android_dojo/catalog.json).
**Playroom → Android Dojo → EXPLORE THE STACK** displays these levels and
links directly to overlapping problem statements, glossary concepts and
experiment journals.

| Abstraction lens | Examples | Fire OS 7 boundary |
| --- | --- | --- |
| Script, Web, declarative | HTML/Canvas, JS, manifests, externally run Python generating data | WebView/Android app and transport sandbox; external Python does not magically run on Fire TV |
| Android frameworks | Java/Kotlin Activity, View, KeyEvent, lifecycle, graphics, storage | Ordinary permissions, own process/window/data |
| Native code and ABI | NDK C/C++, JNI, `.so`, CPU, native memory, supported OpenGL ES APIs | Native code remains in the **app sandbox**, not root |
| System diagnostics | authorized ADB, logcat, dumpsys, optional test instrumentation | Fire OS/build-dependent shell/debug capability; diagnostics aren't arbitrary system controls |
| Kernel/firmware/driver | Linux boundary, SELinux, driver interfaces, boot architecture, emulators | **Research-only** for the ordinary Fire device unless capabilities are independently demonstrated on an authorized test environment |

Fire Console currently targets **Fire OS 7 / Android 9 / API 28**. Do not
infer that every Android API or diagnostic works on every Fire device.
ADB needs authorization; kernel/driver/root/bootloader work is not asserted
possible on this Fire TV. Device-specific capability evidence should feed
the [Compute capability map](COMPUTE_CAPABILITY_REGISTRY.md).

## Problem-first challenges that traverse the stack

- **Why can ten lines of animation code stutter?** Start with JavaScript
  canvas, then compare a native Android View; measure frame intervals,
  scheduling and any graphics constraints.
- **Can a tiny C routine be slower than Java?** Compare batched native
  operations with repeated JNI crossings and examine how measured cost
  depends on the boundary, not just language.
- **What can a regular app know versus an authorized ADB session?** Compare
  app-local observations, `logcat` and `dumpsys`; document the permission
  boundary without inventing privileged access.
- **Can a tiny Python script drive a meaningful TV experiment?** Generate
  data or a new asset outside the device and observe what the TV actually
  receives, caches and renders. No pre-existing dynamic asset system is
  claimed.
- **Where does native Android stop having authority?** Follow an API or
  syscall toward the kernel using documentation, open-source code, scoped
  observations and, where appropriate, an emulator.

These challenges can be partially explored or revisited in any order.
Glossary tags identify useful knowledge; tacit checks identify practical
competence; neither is a gate. The learner can remain at JavaScript,
move down to JNI, or return to a simpler representation whenever useful.

## Depth of investigation: an orthogonal set of operations

1. **Use:** obtain one small observable result.
2. **Predict:** describe what should happen when a variable changes.
3. **Measure:** collect traces rather than trust an impression.
4. **Explain:** determine which platform mechanism supports the behavior.
5. **Transfer:** reuse the discovered rule in another challenge, e.g.,
   a remote-control issue or Playroom's Snake input.
6. **Falsify:** record the smallest counterexample to the current model
   and keep the observed result for the next agent.

These operations can be performed at any abstraction level, and the user
need not complete them all or in order. An agent should propose the cheapest
informative next step, not require a language/tool migration for its own sake.

## Source-grounded capability boundaries

- [Amazon — Fire OS 7](https://www.developer.amazon.com/docs/fire-tv/fire-os-7.html):
  Android 9 / API 28; feature parity is conditional, not total identity.
- [Amazon — ADB on Fire TV](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html):
  device-level setup and authorization.
- [Android — NDK](https://developer.android.com/ndk/guides):
  app-local C/C++ support and JNI.
- [Android — dumpsys](https://developer.android.com/tools/dumpsys)
  and [logcat](https://developer.android.com/tools/logcat):
  bounded system diagnostics, sometimes requiring higher permissions.
- [Android — WebView JavaScript](https://developer.android.com/develop/ui/views/layout/webapps/webview):
  scripts and app interaction; never expose an unrestricted JavaScript
  bridge to untrusted web content.

## Evidence and stopping rule

This change implements **catalog metadata and a TV stack browser**, not new
NDK code, a device-rooting procedure, an on-TV Python runtime, or a native
profiler. Existing app/journal data is unchanged. The cheapest useful next
hardware experiment is to compare scripted versus native animation timing
on the same Fire TV, report the device/OS/build and measured variance, and
correct the explanatory model only when actual observations demand it.

The motivation is REM/RSM/FKEN: the hierarchy helps find interesting routes
through knowledge, not funnel users into a rigid sequence of coursework.
