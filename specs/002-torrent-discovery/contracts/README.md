# Contracts: Torrent Discovery

Interface contracts for `:domain:discovery` (pure Kotlin) and the integration boundary with
the existing Add flow. Signatures are the intended contracts; implementations live in
`:data:discovery`. Types reference `core:model` only where noted; no Android/Room/engine
types appear here.

- [torrent-search-repository.md](./torrent-search-repository.md) — repository contract
- [torrent-search-provider.md](./torrent-search-provider.md) — provider adapter contract
- [discovery-handoff.md](./discovery-handoff.md) — handoff into the existing Add flow

See also [../data-model.md](../data-model.md) for entity shapes.