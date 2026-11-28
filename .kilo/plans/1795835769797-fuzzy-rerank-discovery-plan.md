# Plan: Token-set fuzzy matching + progressive ranked streaming (Discover)

Extends the shipped fuzzy re-rank (custom matcher, reorder-only, RELEVANCE-only, no new dep) with:
1. **Token-set matching** — multi-word queries rank right despite word order / extra words.
2. **Progressive ranking + streaming UI** — partial results are typo-ranked and visible during a
   search, not only on the final page.

> Files drift; locate edits by symbol, not line number. Line refs below are current as of this plan.

## Decisions (confirmed)
- Custom matcher; no library / ADR / license change.
- Reorder-only, RELEVANCE-only; other sorts and null/blank query unchanged.
- Stream partial results during `LOADING` with an indeterminate progress item + `animateItem()`.
- Score = `max(wholeStringScore, tokenSetScore)`; tokens shorter than 2 chars match only exactly.

## Algorithm (FuzzyMatcher, `:domain:discovery`)
```
normalize(s) = s.trim().lowercase().replace(Regex("\\s+"), " ")
tokens(s)    = normalize(s).split(" ").filter{ it.isNotEmpty() }.distinct()
charSim(a,b) = if (a.isEmpty()||b.isEmpty()) 0.0 else 1 - damerauLevenshtein(a,b)/max(len(a),len(b))
wholeScore   = charSim(normalize(query), normalize(target))
tokenScore   = if (qt.isEmpty() || tt.isEmpty()) 0.0
               else sum(qt.map { t ->
                 if (t.length < 2) if (t in tt) 1.0 else 0.0
                 else tt.maxOf { charSim(t, it) }
               }) / qt.size
score        = max(wholeScore, tokenScore)
```
Add `data class Prepared(val normalized: String, val tokens: List<String>)`,
`fun prepare(query): Prepared`, `fun score(prepared: Prepared, target: String): Double`; keep
`fun score(query, target)` delegating to `prepare`. Total/exception-free; blank → 0.0.

## Code changes (by symbol)
1. `domain/discovery/.../search/FuzzyMatcher.kt` — add `Prepared`/`prepare`/`score(prepared,target)`
   + private `tokenScore`; `score(query,target)` delegates. Keep Damerau core + `normalize`.
2. `data/discovery/.../mapper/ResultMerger.kt` → `rankByRelevance` (currently ~L79-98): call
   `FuzzyMatcher.prepare(query)` once; score each title via `score(prepared, it.title)` (replace the
   current `FuzzyMatcher.score(query, result.title)` at ~L91). Null/blank branch + non-RELEVANCE
   branches untouched.
3. `data/discovery/.../DiscoveryRepositoryImpl.kt` → `execute`, the `onProgress(...)` call (~L131):
   emit `ResultMerger.combineAndRank(collected, query.filters.sort, query.text).filter { matchesFilters(it, query.filters) }`
   instead of `collected.toList()`. Final page (~L135) unchanged. Only `search` passes `onProgress`;
   `loadMore`/`refresh` use the no-op default (no extra cost).
4. `feature/discovery/.../DiscoverScreen.kt` (current structure: inline `LazyColumn` in the `when`
   `else` branch, ~L127-151; `LOADING/REFRESHING` spinner at ~L119-120):
   a. Extract the inline `LazyColumn` (the `else` branch body, incl. `atTop` chips/status/history,
      `items(state.results, key = { it.resultId })`, and the Load-more item) into a private
      `ResultsList(state, listState, atTop, onEvent)` composable. Keep the `else` branch calling it.
   b. Change `LOADING, REFRESHING` branch to: `if (state.results.isEmpty()) CenterState{ CircularProgressIndicator(); Text("Searching…") } else ResultsList(...)`.
   c. In `ResultsList`, add a leading progress item when `state.isSearching`:
      `item { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Searching" }) }`
      (import `androidx.compose.material3.LinearProgressIndicator`).
   d. Add `modifier = Modifier.animateItem()` to the `ResultCard(...)` call (ResultCard already has a
      `modifier` param; `animateItem` is a `LazyItemScope` member — no import).
   e. Leave the FAB/`atBottom` block and `listState`/`atTop` logic intact.
5. No ViewModel/State change needed: `submit()` already sets `LOADING` and `onProgress` already
   updates `results = visible(progressive)`; streaming is purely repository + screen.

## Tests (reuse existing fakes)
- `FuzzyMatcherTest` (add): word order (`"dark knight"` vs `"The Dark Knight 2008"` ≥ whole-string);
  extra words fully covering query beats unrelated; 1-char query no wild match; punctuation/multi-space/
  duplicate tokens; `score == max(whole, token)`; determinism.
- `ResultMergerTest` (add): `"dark knight"` ranks `"The Dark Knight"` above `"Completely Unrelated Movie"`;
  existing assertions stay green.
- `DiscoveryRepositoryImplTest` (add): add a `titledResult(id, provider, title)` helper; one
  `FakeProvider` returns `[wrongTitle, rightTitle]`; pass an `onProgress` collector to `search`;
  assert the emitted partial is ranked (right first) and equals the final `state.results` order.
- `DiscoverViewModelTest` (add): a dedicated fake repo whose `search` invokes `onProgress` with a
  ranked partial then returns it; assert `state.results` reflects the ranked order (wiring).
- `DiscoverScreenTest` (androidTest, add): `overall=LOADING` + non-empty `results` → a `Result N`
  node `isDisplayed()` and `onNodeWithContentDescription("Searching").assertExists()`;
  `overall=LOADING` + empty `results` → spinner (no result node).

## Edge cases
- Word order / extra words → token coverage 1.0; whole-string still max'd.
- 1-char / punctuation-only / duplicate tokens → guarded; blank query → unchanged path.
- Unicode/RTL/emoji tokens → char-based, no throw.
- Provider failure/timeout mid-search → ranked partial from healthy providers still shown.
- Refresh clears results → spinner until first result; no stale flicker.
- Long titles/many tokens → query prepared once; bounded cost.

## Error handling
- `FuzzyMatcher` total; tokenization blank-safe; no broad catches added.
- Progressive ranking pure/total → cannot break a search.
- `computeOverall` unchanged (statuses + `isNotEmpty`) → EMPTY/PARTIAL/ALL_FAILED/OFFLINE correct.

## Performance / stability / UX
- Prepare query once per `rankByRelevance` and once per `execute`; score per result via `Prepared`.
- All work on `dispatchers.io`; `animateItem()` animates only moved items; stable `key = resultId`
  preserves identity and avoids scroll reset.
- Deterministic (no clock/random) → stable order across progressive updates, cache, refresh.
- A11y: progress item has `contentDescription = "Searching"`; result semantics unchanged.
- Risk: extracting `ResultsList` must preserve `listState`/`atTop` and the FAB `atBottom` condition.

## Validation (exact commands)
- `./gradlew :domain:discovery:test`
- `./gradlew :data:discovery:testDebugUnitTest`
- `./gradlew :feature:discovery:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- (device) `./gradlew :feature:discovery:connectedDebugAndroidTest`

## Out of scope / rollout
- No provider/query rewrite, "did you mean", history autocomplete; no filtering floor; no fuzzy under
  non-RELEVANCE sorts; no change to load-more/refresh semantics beyond streaming display.
- No schema/migration; no new dependency/ADR/license change.

## Implementation note
Source + tests only. Switch to an implementation-capable agent to execute.