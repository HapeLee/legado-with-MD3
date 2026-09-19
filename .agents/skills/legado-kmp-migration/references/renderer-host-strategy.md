# Renderer and Host Strategy

Read this reference when a Feature may use more than one UI stack or when a non-Kotlin host consumes
shared Kotlin behavior.

## Separate three decisions

1. **Shared behavior:** domain, repositories, use cases, state snapshots, commands and reducers.
2. **Host integration:** Gradle/JVM, Apple framework, Windows DLL/C ABI, or process/IPC.
3. **Renderer:** Android Material/Miuix, Compose Material/Fluent, WinUI 3, SwiftUI/UIKit, or a
   specialized platform island.

Do not infer one decision from another. A KMP core does not require CMP, and Compose on one host
does
not require every host to share the same Screen.

## Renderer-neutral presentation

Prefer immutable state snapshots, stable IDs, commands/intents, effects, domain values and explicit
capability/error states. Keep Composable lambdas, `Modifier`, Material/Fluent types,
`StringResource`, icons/painters, navigation UI objects, Android Context, Swift/WinRT objects, Room
entities and DI containers out of the boundary.

`@Stable` belongs to a Compose-facing contract or adapter. If WinUI 3 or SwiftUI also consumes the
state, keep the pure state free of renderer annotations and adapt it at the Compose boundary.

## WinUI 3 choices

WinUI 3 cannot consume a Kotlin/JVM Gradle module like a Kotlin host. Choose the bridge explicitly.

### Kotlin/Native DLL and C ABI

Use a `mingwX64` shared library when in-process calls and one packaged process matter, and only when
the required dependency closure supports the target.

- Export a small facade, not repositories, Flow, sealed hierarchies or generic Kotlin APIs.
- Use opaque handles with explicit create/dispose.
- Define ownership for every returned string or buffer.
- Map async work to request IDs plus callbacks/polling; define callback thread and cancellation.
- Version the ABI and compile a native consumer in CI.
- Do not force JVM-only Room, Rhino or jsoup dependencies into the DLL; split a native-compatible
  core or choose IPC for those capabilities.

### JVM sidecar and IPC

Keep the existing JVM data/runtime stack in a separately packaged process and expose a versioned
local protocol when reuse is more important than in-process integration.

- Define protocol DTOs and version negotiation.
- Define startup, readiness, shutdown, crash recovery and upgrade behavior.
- Restrict local access; do not unintentionally expose an unauthenticated network service.
- Propagate cancellation and structured errors.
- Package and test both processes as one product.

Use one bounded read/write Feature to compare cold start, call latency, state mapping, database and
runtime reuse, memory ownership, crash isolation, installer complexity and test ergonomics before
choosing the product-wide bridge.

## iOS native renderer

Export a deliberately small Apple framework facade. SwiftUI may observe a tested shared state host
or own an `ObservableObject` that calls shared repositories/use cases. Verify Swift names,
optionality, collections, async/Flow bridging, lifecycle, cancellation and memory ownership with a
Swift consumer test.

## Selective CMP

Share a CMP Screen only when real hosts intentionally share its visual and interaction model.
Resources and design-system components belong to that renderer. If Desktop uses WinUI 3 and iOS
uses SwiftUI, Android Compose can remain an Android renderer even when its code is technically
portable.
