# ADR-0008: Module boundaries

**Status**: Accepted | **Date**: 2026-11-22

## Context

The app must stay maintainable as it grows, with engine isolation and testable domain logic.

## Decision

Adopt the multi-module Clean Architecture structure in [plan.md](../plan.md) §Module Design.
`:app` is the composition root; `:feature:*` depends on `:domain:*` and `:core:*`; `:data:*`
implements domain contracts; `:engines:*` owns native/runtime integration.

## Alternatives

- **Single-module app**: rejected — UI could reach engines/Room directly.
- **Feature-packed modules without a domain ring**: rejected — business logic leaks into UI.
- **More granular modules than needed**: rejected — empty modules add build cost.

## Trade-offs

- 25 modules is a lot of build configuration.
- Modules are created with their slice, not all up front.

## Consequences

- Dependency direction is enforced by module `build.gradle.kts` and review.
- `:domain:*` has no Android, Room, or engine dependencies.
- `:engines:*` may be merged into `:data:*` if the packaging boundary does not earn its own
  module, but the contract stays in `:domain:*`.
- No circular dependencies; no UI→engine dependency.
