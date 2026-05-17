# Chapter Translation Plan 2

## Goal

Continue from `plan.md` and fix the real reading experience issues:

- Move chapter translation from reader overflow menu to reader bottom actions.
- Make translation display state chapter-scoped.
- Show long-running translation progress through progressive mixed content.
- Add display-only book metadata translation for bookshelf/book info/search/explore.
- Add a hidden per-book glossary for better LLM name/place consistency.

Legend: `[ ]` not started, `[~]` adjusted or needs migration, `[x]` done.

## Decisions

- Original chapter cache remains `<chapter>.nb`.
- Completed translated chapter cache remains `<chapter>.<targetLanguage>.nb`.
- `translationCaches` remains a request/short-text cache. Do not change its primary key, indexes, schema, migration, or DB version for this plan.
- Short metadata cache key is `targetLanguage@normalizedSource`. It ignores provider/model/prompt/endpoint; same original text + same target language shares one result.
- Reader display state is keyed by `bookUrl + chapterIndex`, not a single global boolean. It may survive Activity recreation through the existing Koin singleton; process restart can default to original.
- Target language is global app configuration. Do not add per-chapter language switching.
- Metadata translation is display-only. Do not mutate `Book`, `BookShelfItem`, `SearchBook`, database rows, navigation keys, callbacks, or search parameters.
- Short-text translation and chapter translation use separate gateway APIs. Short text uses a simple batch request; chapter translation uses literary prompt, optional JSON formatter, and glossary handling.
- Progressive replacement may cause minor page-position drift. Use existing paragraph/chunk order and `resetPageOffset = false`; do not add complex anchoring/diffing in this phase.
- Hidden glossary is internal. No user-facing editor/config is needed. Existing glossary entries win on source-term conflicts in the first version.

## Code Anchors

- Reader: `ReadBookActivity.kt`, `ReadMenu.kt`, `view_read_menu.xml`, `book_read.xml`
- Translation core: `TranslationManager.kt`, `TranslateChapterUseCase.kt`, `ChapterTranslator.kt`, `LlmTranslateClient.kt`, `ContentChunker.kt`
- Cache: `BookHelp.kt`, `TranslationCacheRepositoryImpl.kt`, `TranslationCacheDao.kt`, `TranslationCache.kt`
- Settings: `TranslationConfig.kt`, `TranslationConfigScreen.kt`, `TranslationConfigViewModel.kt`, `PreferKey.kt`
- Metadata UI: `BookItem.kt`, `BookInfoScreen.kt`, `SearchBookItem.kt`, optional legacy `SearchAdapter.kt`

## 1. Reader Bottom Button And Chapter State

### Problem

Translation actions currently live in `book_read.xml` overflow and `TranslationManager.translationMode` is global, so state can leak between chapters.

### Target

- Bottom action button shows `翻译`.
- In `Original`, tapping it loads existing translated file or starts translation.
- In `Translating`, tapping it opens a bottom sheet with progress/actions.
- In `Translated`, tapping it opens a bottom sheet with `返回原文` and `重新翻译`.
- Cold process start can show original. Activity recreation may retain per-chapter state naturally.

### Changes

1. Remove overflow actions.
  - Remove `menu_translate_chapter`, `menu_translate_original`, `menu_retranslate_chapter` from `book_read.xml`.
  - Remove their handling from `ReadBookActivity.onCompatOptionsItemSelected`.

2. Add bottom translation action.
  - In `ReadMenu.getAllButtons()`, put `translation` where `auto_page` currently is.
  - Move `auto_page` to the end of the configurable button list.
  - Add `onTranslationClick()` to `ReadMenu.CallBack` and implement it in `ReadBookActivity`.
  - Update `ToolButtonConfigDialog.getAllButtonIds()` / `getButtonInfo()` so old saved configs append the new button automatically.

3. Add translation action sheet.
  - New `TranslationActionDialog.kt`.
  - New `dialog_translation_actions.xml`.
  - Use existing `BaseBottomSheetDialogFragment` style.
  - Call back to `switchCurrentChapterToOriginal()` and `retranslateCurrentChapter()`.

4. Replace global display state.
  - New `TranslationChapterState.kt`:

```kotlin
data class TranslationChapterKey(val bookUrl: String, val chapterIndex: Int)

enum class TranslationDisplayState {
    Original,
    Translating,
    Translated
}
```

- `TranslationManager` owns an in-memory map keyed by `TranslationChapterKey`.
- Completed translation file existence is not display state. A chapter may have `<chapter>.zh.nb` and still display original until user taps translate.
- `targetLanguage` and content hash stay in cache/generation logic, not reader display-state key.

5. Update menu state.
  - Add `ReadMenu.updateTranslationButton(state, current, total)`.
  - Call it from `upMenu()` / `upMenuView()` and progress callbacks.

### Files

- `app/src/main/res/menu/book_read.xml`
- `app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
- `app/src/main/java/io/legado/app/model/translation/TranslationManager.kt`
- `app/src/main/java/io/legado/app/model/translation/TranslationChapterState.kt`
- `app/src/main/java/io/legado/app/ui/book/read/TranslationActionDialog.kt`
- `app/src/main/res/layout/dialog_translation_actions.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`

## 2. Progressive Mixed Chapter Display

### Problem

Whole-chapter translation can take a long time. A modal progress dialog makes the reader feel blocked, and content does not visibly change until success.

### Target

Reader has three display states:

- `Original`: original content.
- `Translating`: translated chunks replace original chunks as they finish.
- `Translated`: final `<chapter>.<targetLanguage>.nb` content.

### Changes

1. Reuse current chunk cache.
  - Keep using `translationCaches` for request-level chunk results.
  - Add batch chunk lookup only if needed; otherwise call existing `getChunk(...)` per chunk.
  - No new Room table.

2. Add partial assembler.
  - New `PartialTranslationAssembler.kt`.
  - Input: `List<TranslationChunk>` + `Map<Int, String>`.
  - Output: chapter content in original order, using translated chunk when present and original chunk otherwise.

3. Add progress snapshot.

```kotlin
data class TranslationProgressSnapshot(
    val key: TranslationChapterKey,
    val current: Int,
    val total: Int,
    val mixedContent: String,
    val state: TranslationDisplayState
)
```

4. Translation flow.
  - Resolve current book/chapter/original `.nb`.
  - Split with `ContentChunker`.
  - Enter `Translating`.
  - Render any already cached chunks immediately.
  - After each chunk success, save chunk, rebuild mixed content, call `ReadBook.contentLoadFinish(..., resetPageOffset = false)`, and update button progress.
  - On full success, save `<chapter>.<targetLanguage>.nb`, clear request chunk rows for the chapter cache key, enter `Translated`.
  - On failure, keep successful chunk rows. Button can return to `Original` so the next tap resumes from cached chunks.

### Notes

- Remove modal `AlertDialog` progress. Use button/progress state, optional bottom sheet progress, and completion/failure/cancel toasts.
- Throttle content refresh to roughly 300-500ms if chunks complete too quickly.
- Do not apply mixed content unless visible `bookUrl + chapterIndex` still matches the progress snapshot.
- Do not add complex page-position anchoring unless manual testing shows severe jumps.

### Files

- `TranslationManager.kt`
- `TranslateChapterUseCase.kt`
- `ChapterTranslator.kt`
- `TranslationCacheRepositoryImpl.kt`
- `TranslationCacheDao.kt` optional
- `PartialTranslationAssembler.kt`
- `TranslationChapterState.kt`
- `ReadBookActivity.kt`
- `ReadMenu.kt`

## 3. Display-Only Book Metadata Translation

### Problem

Bookshelf, book info, explore, and search results may show non-Chinese book names/authors/intros. We want translated display text without changing real metadata.

### Target

- Add setting `是否翻译书籍信息`.
- When disabled, all helpers return original text.
- Blank, numeric-like, punctuation-only, and already-Chinese text for `zh` return original.
- UI first paints original text and updates when translation arrives.

### Cache

Use existing `translationCaches` with:

```text
cacheKey = targetLanguage + "@" + normalizedSource
originalContentHash = MD5(source)
llmTargetLanguage = targetLanguage
provider = current provider, diagnostic only
translatedContent = translated short text
```

Rules:

- Lookup by `cacheKey`; ignore provider/model/prompt/endpoint.
- Keep current single-column `cacheKey` primary key.
- Do not change Room entity, schema, migration, index, or DB version.
- Add DAO helpers for short text because existing chunk lookup is provider-specific.

### ShortTextTranslator

Public API stays single-text:

```kotlin
suspend fun getTranslateShort(
    source: String,
    targetLanguage: String = TranslationConfig.llmTargetLanguage,
    enabled: Boolean = TranslationConfig.translateBookInfoEnabled
): String
```

Internal behavior:

- Normalize and skip first.
- Check `cacheKey = targetLanguage@normalizedSource`.
- Queue misses in a process-local queue keyed by `(normalizedSource, targetLanguage)`.
- Share one `Deferred` for duplicate visible text.
- Debounce about 80-150ms; max wait about 250ms.
- Batch up to 20 short texts per request with concurrency 1-2.
- On batch failure, return original text; cache partial successes when available.

Provider behavior:

- OpenAI-compatible short text uses a separate `translateShortBatch(...)` API.
- It uses a short prompt and simple id-preserving output. It does not use chapter glossary prompt, chapter parser, or chapter JSON formatter requirement.
- Google may use multi-`q`; otherwise keep the queue/batch API but issue limited-concurrency single-item requests internally.

### Compose/View Usage

- Add `TextLanguageHeuristics.kt`.
- Add `TranslatedShortText.kt` or `rememberTranslatedShortText(...)`.
- The Compose helper must key work by `source + targetLanguage + enabled`, reset immediately to original when keys change, and never call suspend translation directly from the composable body.
- Legacy `SearchAdapter` is optional; if used, bind with lifecycle-aware coroutine and ignore stale bind results.

### UI Touch Points

- Bookshelf `BookItem.kt`: display-only `book.name`, `book.author`, visible intro.
- Book info `BookInfoScreen.kt`: header name, author text preserving `author_show`, intro summary.
- Search/explore `SearchBookItem.kt`: display-only `book.name`, `book.author`, `book.intro`.
- Do not translate latest chapter titles, tags, kinds, navigation callbacks, shelf-state lookup keys, or search arguments in the first pass.

### Files

- `TranslationConfig.kt`
- `TranslationConfigScreen.kt`
- `TranslationConfigViewModel.kt` only if cache display/clear behavior changes
- `PreferKey.kt`
- `strings.xml`
- `strings.xml` zh-rCN
- `ShortTextTranslator.kt`
- `TextLanguageHeuristics.kt`
- `TranslatedShortText.kt`
- `BookItem.kt`
- `BookInfoScreen.kt`
- `SearchBookItem.kt`
- `SearchAdapter.kt` optional
- `app/src/main/java/io/legado/app/di/appModule.kt`

## 4. Hidden Per-Book Glossary

### Problem

LLM chapter chunks are translated independently, so names, places, and proper nouns can vary across chunks or chapters.

### Target

For each book and target language, keep a hidden glossary:

```text
source term -> translated term
```

OpenAI-compatible chapter translation asks for valid JSON:

```json
{
  "dictionary": [
    {"source": "Alice", "trans": "爱丽丝"}
  ],
  "output": "..."
}
```

`output` is the translated chunk. `dictionary` contributes up to 20 new proper-noun pairs. If dictionary parsing is damaged but output is valid, keep output and drop dictionary. If output cannot be safely extracted, retry/fail the chunk and never write raw structured text into chapter content.

### Storage

Avoid Room changes. Store compact JSON under:

```text
book_cache/<book>/translation_dictionary.<targetLanguage>.json
```

Rules:

- Merge by exact `source`.
- Existing translation wins in the first version.
- Do not add advanced conflict reconciliation.
- Drop blank, numeric-only, punctuation-only, and very long terms.
- Cap stored terms around 200 per book/language.
- Attach only useful recent terms to prompts, about 50 max.

### Components

- `TranslationTerm.kt`
- `TranslationDictionaryStore.kt`
- `LlmTranslationResultParser.kt`
- Chapter translation gateway returns:

```kotlin
data class LlmTranslateResult(
    val output: String,
    val dictionary: List<TranslationTerm> = emptyList()
)
```

This is for chapter translation callers only. Short metadata translation stays on its separate simple batch API.

### Prompt And Flow

- Built-in OpenAI chapter prompt includes target language, existing glossary, text, JSON output requirement, paragraph/order preservation, and no more than 20 dictionary pairs.
- Request JSON response formatting for OpenAI-compatible chapter translation when supported, for example `response_format = {"type":"json_object"}`.
- Provider compatibility issues around JSON formatter are implementation/debugging work; this plan only requires retry/failure when safe output cannot be parsed.
- First version forces effective concurrency to 1 for OpenAI-compatible glossary mode. Google Translate and future modes can keep configured concurrency.

Flow:

1. `TranslateChapterUseCase` passes `book` and `targetLanguage` into `ChapterTranslator`.
2. `ChapterTranslator` loads glossary before each chunk request.
3. `LlmTranslateClient` sends glossary and requests JSON output when supported.
4. Parse `output` and `dictionary`.
5. Save only `output` to chunk cache and final chapter file.
6. Merge dictionary candidates into `translation_dictionary.<targetLanguage>.json`.

### Files

- `domain/gateway/LlmGateway.kt`
- `model/translation/LlmTranslateClient.kt`
- `model/translation/ChapterTranslator.kt`
- `domain/usecase/TranslateChapterUseCase.kt`
- `model/translation/TranslationDictionaryStore.kt`
- `model/translation/TranslationTerm.kt`
- `model/translation/LlmTranslationResultParser.kt`
- `ui/config/translation/TranslationConfig.kt`
- `di/appModule.kt`

## Milestones

### Milestone 1: Reader Button And Correct State

Status: `[ ]`

Deliverables:

- Remove translation actions from reader overflow.
- Add default-visible bottom `翻译` button; move auto-page to the end of configurable buttons.
- Add translation bottom sheet.
- Replace global `translationMode` with per-chapter display state.

Verification:

- Translate chapter A, move to chapter B, confirm B shows original state.
- Return to chapter A; retained translated state is acceptable, but B state must not leak.
- `./gradlew :app:compileAppDebugKotlin`

### Milestone 2: Progressive Mixed Content

Status: `[ ]`

Deliverables:

- Remove blocking modal progress.
- Render mixed original/translated content as chunks finish.
- Save final `<chapter>.<language>.nb`.
- Keep successful chunk request cache for retry after failure.

Verification:

- Use a multi-chunk chapter and confirm visible updates before full completion.
- Interrupt and retry; successful chunks should not be re-requested.
- Add/update focused unit test for `PartialTranslationAssembler`.
- `./gradlew :app:compileAppDebugKotlin`

### Milestone 3: Book Metadata Display Translation

Status: `[ ]`

Deliverables:

- Add `是否翻译书籍信息`.
- Add `ShortTextTranslator`, skip heuristics, provider-agnostic short-text cache, and batch short-text gateway.
- Translate only visible name/author/intro fields in bookshelf, book info, search/explore.
- Keep all callbacks/navigation/search/shelf lookup metadata original.
- Update cache-clear wording to mention translated chapters, chunk/request cache, and short metadata display cache.

Verification:

- Switch off: original text and no requests.
- Switch on: non-Chinese visible metadata translates; Chinese/numeric-like text stays original.
- 40-50 uncached short phrases should produce about 2-3 LLM batch requests.
- Duplicate visible phrases share one in-flight request and one cache row.
- Add focused tests for `TextLanguageHeuristics` and short-text grouping/deduping.
- `./gradlew :app:compileAppDebugKotlin`

### Milestone 4: Hidden Glossary

Status: `[ ]`

Deliverables:

- Add file-backed glossary store.
- Add structured chapter response parser.
- Add chapter `LlmTranslateResult(output, dictionary)`.
- Include existing glossary in OpenAI-compatible chapter prompts.
- Store only translated `output`, never dictionary/raw structured text.

Verification:

- Translate chunk 1 with a proper noun and confirm dictionary file is written.
- Translate chunk 2 and confirm prompt includes prior terms.
- Damaged dictionary with valid output still displays output.
- Invalid/missing output does not save raw response text.
- Add focused tests for parser and dictionary merge.
- `./gradlew :app:compileAppDebugKotlin`

### Milestone 5: Cleanup And Documentation

Status: `[ ]`

Deliverables:

- Update `plan.md` status if replaced by this plan.
- Keep `plan2.md` statuses current.
- Remove obsolete strings/menu ids after migration.
- Confirm no new Room version is needed.
- Update `translation_clear_all_summary` and related cache text.

Verification:

- `rg "menu_translate_chapter|menu_translate_original|menu_retranslate_chapter" app/src/main`
- `rg "translationMode" app/src/main`
- `./gradlew :app:compileAppDebugKotlin`
