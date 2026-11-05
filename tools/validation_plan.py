#!/usr/bin/env python3
"""Route Fire changes to the cheapest credible validation.

This is not a correctness oracle. Pure docs/test-harness changes do not need
Gradle; changes to native code compile only impacted modules. The Android TV
emulator is *not* an automatic gate: request it when runtime evidence matters.
The release publisher still performs the complete build on main.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT_MODULES = ("app", "browser", "canvas", "signalstudio")
TEST_TASKS = {
    "app": ":app:testReleaseUnitTest",
    "browser": ":browser:testReleaseUnitTest",
    "canvas": ":canvas:assembleRelease",
    "signalstudio": ":signalstudio:testReleaseUnitTest",
    "remoteinput": ":remoteinput:testReleaseUnitTest",
}


def classify(paths):
    paths = set(paths)
    modules = set()
    packaging = set()
    all_modules = any(p in ("settings.gradle", "gradle.properties") or
                      p.endswith(".gradle") and "/" not in p or
                      p.startswith("signing/") for p in paths)
    if all_modules:
        modules.update(ROOT_MODULES)
        modules.add("remoteinput")
        packaging.update(ROOT_MODULES)
    for path in paths:
        module = None
        if path.startswith("app/"):
            module = "app"
        elif path.startswith("browser/"):
            module = "browser"
        elif path.startswith("canvas/"):
            module = "canvas"
        elif path.startswith("Applications/SignalStudio/"):
            module = "signalstudio"
        elif path.startswith("AppOS/Remote/"):
            modules.update(("remoteinput", "signalstudio"))
            if path.endswith(".gradle") or "/res/" in path:
                packaging.add("signalstudio")
        if module:
            modules.add(module)
            if (path.endswith(".gradle") or
                path.endswith("/AndroidManifest.xml") or "/res/" in path):
                packaging.add(module)
    tasks = []
    for module in ("remoteinput", *ROOT_MODULES):
        if module not in modules:
            continue
        tasks.append(TEST_TASKS[module])
        if module in packaging and module != "canvas":
            tasks.append(":" + module + ":assembleRelease")
    return {
        "android": bool(modules),
        "modules": sorted(modules),
        "tasks": tasks,
        "scope": "native" if modules else "static",
        "changed": sorted(paths),
    }


def diff_paths(base, head="HEAD"):
    if not base or not all(c in "0123456789abcdefABCDEF" for c in base) or len(base) != 40:
        raise ValueError("Expected a 40-character Git base SHA")
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRT", base, head],
        check=True, stdout=subprocess.PIPE, text=True)
    return [x.strip() for x in result.stdout.splitlines() if x.strip()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="PR base SHA; diff against checked-out PR merge commit")
    parser.add_argument("--all", action="store_true", help="Validate all native modules")
    parser.add_argument("--paths", nargs="*", default=None, help="Explicit changed paths for dry runs")
    parser.add_argument("--github-output", type=Path, default=None)
    args = parser.parse_args()
    if args.all:
        paths = ["settings.gradle"]
    elif args.paths is not None:
        paths = args.paths
    else:
        paths = diff_paths(args.base)
    plan = classify(paths)
    print(json.dumps(plan, indent=2))
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as out:
            out.write("android=" + str(plan["android"]).lower() + "\n")
            out.write("tasks=" + " ".join(plan["tasks"]) + "\n")
            out.write("scope=" + plan["scope"] + "\n")


if __name__ == "__main__":
    main()
