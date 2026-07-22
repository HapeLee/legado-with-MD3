---
name: legado-debug-tool
description: Reproduce, diagnose, and regression-test Legado Android bugs on a connected phone or emulator with the project Debug Tool, extending the tool when a new screen, precondition, action, system UI, fault injection, or observation pattern is encountered. Use when the user mentions debug-tool, asks to reproduce or locate an Android bug, provides a crash/UI/back-navigation/theme-save/export report, wants logcat/screenshots/video evidence, or needs a deterministic scenario added under tools/android. Also use before fixing a reported device-only bug whose real navigation stack, settings, lifecycle, system picker, or resource state matters.
---

# Legado Debug Tool

## Goal

Establish observable evidence before changing product behavior. Reproduce the user's real flow, distinguish tool failures from app failures, identify the root cause, explain the intended repair, then turn the reproduction into a passing regression scenario.

Read `../../../tools/android/README.md` completely before taking debug actions. Treat it and `tools/android/debug.sh list` as the current capability reference; do not copy a stale action list into the investigation.

## Non-negotiable rules

- Do not treat the reporter's diagnosis as fact. Convert it into a hypothesis and force the suspected exception or state when necessary.
- Do not edit `src/main` business behavior before reproducing the bug or obtaining equivalent deterministic evidence.
- Read-only code inspection is allowed to find the correct entry flow, observable state, and injection point.
- Reproduce the real task stack. If a screen is normally opened from a parent, launch the parent and navigate into it; launching a child Activity as the task root makes Back exit the app by design.
- Prefer semantic UI nodes, lifecycle state, app-private artifacts, hashes, and logs over fixed coordinates or sleep-only assertions.
- Count a system picker action only after clicking its final confirmation button. Creating a document opens DocumentsUI; it does not write the final file until “保存” is clicked.
- Keep fixtures, providers, launch helpers, and fault injection in `src/debug` or `tools/android`. Do not add diagnostic shortcuts to release code.
- Do not classify a failed scenario as an app bug until its preconditions and intended actions actually ran.
- Treat every new bug pattern as a Debug Tool capability gap to close. Do not finish with a one-off sequence of shell commands when the flow can be represented as a reusable entry, action, fixture, injection mode, assertion, or artifact.
- Use ad hoc ADB commands only to explore an unknown UI or provider. Before diagnosing or fixing product code, fold the discovered procedure back into `runner.py`, a scenario JSON, tool tests, and `tools/android/README.md`.

## Workflow

### 1. Establish the environment

Run:

```bash
adb devices
tools/android/debug.sh list
```

Use the only ready device automatically; pass `--device` when multiple devices exist. If the debug APK or `src/debug` changed, run once without `--skip-build`. Otherwise reuse the installed APK.

### 2. Translate the report into a scenario

Write down four things before running or editing:

1. Real entry screen and navigation stack.
2. Preconditions: settings, stored data, resource state, process restart, or system UI.
3. User actions in order.
4. Observable outcome that separates pass from failure.

Map these to one entry, domain actions, and assertions. Reuse an existing scenario when it covers the flow. If not, add a small registered handler plus a JSON scenario; do not create a generic coordinate DSL.

Use `preferences` for primitive String/Boolean settings applied before launch. Use a Debug-only fixture or provider for files, databases, content URIs, delayed failures, and other controlled state.

### 2a. Extend the tool for a new scenario

If the current capability list cannot express the report, improve the tool before touching product code:

- Add an entry when a new real launch surface or task stack is required.
- Add a domain action when a new user operation must be repeatable.
- Add a fixture, `preferences` mapping, or Debug-only provider when a new precondition must be injected.
- Add an assertion when the outcome cannot be proven by existing observations.
- Add an artifact collector when screenshots, logs, video, private files, or exported data are not yet retained.
- Add a system-UI helper when DocumentsUI, Photo Picker, permission UI, or another package participates in the flow.

Register capabilities through `ENTRIES`, `ACTIONS`, and `ASSERTIONS`; avoid scenario-name conditionals and avoid growing `execute()` for one bug. Add or update Python tests for validation and helper behavior. Document the new scenario and injection mode in `tools/android/README.md`.

The first successful manual reproduction is discovery, not completion. A reusable scenario that another fresh session can run without oral instructions is the completion criterion.

### 3. Run a baseline before fixing

Examples:

```bash
tools/android/debug.sh run source-edit/predictive-back
tools/android/debug.sh run theme/export-current-baseline --skip-build
```

For intermittent behavior, run independent full sessions rather than only repeating an action in one process. Record every session ID. Do not merge tool/setup failures into the app failure rate.

### 4. Inspect raw evidence

Start with `.debug-runs/<session>/summary.json`, then inspect as relevant:

- `session-logcat.txt`: logs for observed app PIDs and session markers.
- `logcat.txt`: full device log for system providers, DocumentsUI, Photo Picker, and Toast evidence.
- `crash.txt`: uncaught app crash evidence; zero bytes means no captured fatal exception.
- `transition.mp4`: navigation, predictive Back, picker, animation, and transient UI evidence.
- screenshots: stable state before, during, and after actions.
- `export.zip`: validate with `unzip -t`, inspect `manifest.json`, entries, sizes, and hashes.

For saved themes or other private data, use `adb exec-out run-as <debug-package>` to inspect the actual file and manifest. Do not accept a Toast as proof that persistence succeeded.

### 5. Classify failures correctly

Treat these as tool/environment failures until corrected:

- entry readiness failed or zero intended actions ran;
- a stale DocumentsUI/Photo Picker task stayed above the app;
- the automation never found or confirmed the picker filename;
- the pulled output was read before its size became stable;
- a deep Activity was launched without its real parent;
- an assertion checked a transient Toast after it disappeared.

Treat a result as app evidence only when the scenario proves the preconditions, performs the intended actions, and captures the resulting state or exception.

### 6. Test the hypothesis, not the wording

When a report names `openInputStream`, permissions, a race, or another suspected cause:

- establish a valid baseline first;
- inject the exact failure class or resource lifecycle separately;
- delay failure until immediately before the target action when previews may read the same resource;
- log an explicit arm marker and access marker so ordering is provable;
- compare missing files, invalid/revoked content URIs, restart state, and normal resources independently.

Never imitate only an error string when the underlying provider or lifecycle behavior can be reproduced.

### 7. Explain the repair before editing

Report:

- exact reproduced flow;
- whether it passed or failed and how often;
- artifact links;
- confirmed root cause versus remaining inference;
- the smallest root-level repair and its tradeoffs.

If the user asks to review the proposed fix first, stop here. Do not apply a speculative patch.

### 8. Fix and convert to regression

After authorization to fix:

- change the ownership or lifecycle that caused the failure, not only the final exception site;
- preserve cleanup and rollback behavior;
- change the reproducing scenario from expected failure to expected success when appropriate;
- keep controlled negative scenarios for failures the app is still expected to report safely;
- rerun the same real UI flow on the device and validate produced artifacts.

### 9. Verify and clean up

Run the relevant project test plus the tool tests:

```bash
python3 -m unittest discover -s tools/android/tests -v
```

Build/install and run the final device scenario without `--skip-build` after product or Debug APK changes. At the end, stop Gradle and Kotlin daemons according to repository rules; confirm no large build process remains.

## Handoff format

Lead with the outcome. Include the reproduction flow, root cause, fix, tests, and clickable absolute links to `summary.json` and `transition.mp4`. Explicitly say whether product code was changed, whether changes are committed, and whether the worktree is clean.
