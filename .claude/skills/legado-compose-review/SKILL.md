---
name: legado-compose-review
description: Review existing Legado Jetpack Compose code for architecture, behavior, maintainability, and project convention issues. Use when Codex is asked to audit, review, inspect, evaluate, or find problems in Legado Compose screens, routes, ViewModels, contracts, dialogs, sheets, navigation, or early Compose implementations, especially for MVI/UDF, StateFlow/SharedFlow, Clean Architecture, MainActivity navigation, legacy Activity compatibility, and View-era mixed-pattern drift.
---

# Legado Compose Review

## Overview

Review existing Compose code before rewriting it. Focus on concrete defects, architectural drift, behavior risks, and missing verification, especially in early Compose screens that may predate the current MVI/UDF, Clean Architecture, and `MainActivity` navigation expectations.

Read `references/review-checklist.md` for the project-specific checklist and severity guidance.

## Workflow

1. Define the review scope.
   - Identify the exact screen, route, ViewModel, contract, or package under review.
   - State whether the user wants review only or review plus fixes. If unclear, review first and do not edit code.
   - Treat unrelated legacy View code as context, not as part of the review, unless it affects the Compose surface.

2. Build context from code.
   - Read the `*Screen`, `*ViewModel`, `*Contract`, host Activity/route, DI registration, and any repositories/usecases used by the feature.
   - Read the old View implementation only if it is still a compatibility caller or behavior reference.
   - Compare against nearby current examples such as `BookInfo*`, `Search*`, `MainActivity`, and shared components under `ui/widget/components`.

3. Review by risk, not style preference.
   - Prioritize behavior regressions, state duplication, lifecycle bugs, navigation bugs, business logic in UI, direct data access from UI/presentation, recomposition hazards, and missing compatibility handling.
   - Flag style-only issues only when they conflict with established project conventions or make future migration harder.
   - Do not require broad refactors for a small screen unless the current code creates real behavior or maintenance risk.

4. Output findings first.
   - Use code-review style: list findings before summaries.
   - Include file and tight line references.
   - Explain the concrete impact and the smallest credible fix.
   - If using Codex review directives, emit one `::code-comment{...}` per finding.
   - If no issues are found, say that clearly and mention remaining test/manual verification gaps.

5. Suggest fixes only after findings.
   - Group fixes into small, reviewable steps.
   - For architecture drift, separate compatibility-preserving fixes from larger cleanup.
   - Recommend `legado-compose-migration` only when the finding implies a migration or rewrite workflow.

## Review Priorities

- **P0/P1**: behavior breakage, lost navigation/result behavior, unsafe lifecycle collection, stale state, data corruption, crashes, or compatibility entry points that no longer work.
- **P2**: architecture drift that will cause duplicated logic, hard-to-test behavior, recomposition bugs, or incorrect ownership of state/effects.
- **P3**: convention drift, maintainability issues, weak naming, missing previews/tests, or small cleanup that should not block behavior.

## Boundaries

- Do not rewrite code during a review unless the user asks for fixes.
- Do not demand pure architecture where a compatibility boundary is necessary for unreworked View screens.
- For new Compose-first code, expect standard Android architecture: `MainActivity` route ownership, ViewModel-owned state, UDF/MVI user actions, `StateFlow`/`SharedFlow`, repositories/usecases, and UI without business logic.
- For migrated code, allow a thin retained Activity only for Android `Intent` compatibility with unreworked View callers.
- Distinguish MVI `FeatureIntent` user actions from Android `Intent` launch/extras when both appear.

## Reference

Read `references/review-checklist.md` for detailed checks. When a review turns into implementation, also read `../legado-compose-migration/references/project-patterns.md` if available.
