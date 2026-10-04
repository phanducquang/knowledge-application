# Retrieval quality evaluation — offline synthetic

This milestone establishes a repeatable **retrieval** benchmark, not an assessment of Gemini, generated answers, factual correctness or claim entailment. Reports explicitly state **provider semantic quality NOT measured**. No production retrieval/configuration/schema/UI changes are made. Google Java GenAI SDK stays **1.75.0**.

## Run

From `apps/api`, with Java 17 and Docker available:

```bash
./gradlew retrievalEval
```

No application server, OAuth session, API key, developer PostgreSQL or MinIO is required. One disposable PostgreSQL 17/pgvector Testcontainer uses the existing project image and applies unchanged Flyway V1–V9. The corpus is loaded once, all queries run twice against the unchanged snapshot, and deterministic report identities are compared. The container is discarded afterward. No running application database, private notes or real quota counters are accessed.

The full evaluation also runs in ordinary `./gradlew test` and `./gradlew clean build`. The dedicated task always reruns even if Gradle considers its inputs unchanged. It filters to the integration evaluator; independent metric/fixture/provider unit tests run in the normal suite. It first writes the original 24-note/40-query reports, then the [RAG chunk-selection matrix](#rag-chunk-selection-matrix). No new dependency, CI workflow or runtime provider is introduced.

## Corpus and declared ground truth

Version `retrieval-eval-v1` lives in `apps/api/src/test/resources/retrieval-eval/corpus.json`, with one companion `webclient-playbook.md`. **24 synthetic notes** produce **31 chunks** with the unmodified default chunker (version 1, 4000 characters/200 overlap). Notes include Markdown headings, paragraphs, lists and fenced Java/YAML/Dockerfile examples. Subjects cover WebClient/HTTP/Nginx/proxies, Redis, PostgreSQL indexes, Docker, Kafka, OIDC/CSRF, Kubernetes, Java/Jackson, Next.js, S3/MinIO, pgvector and GitHub Actions. Similar-topic notes deliberately compete; the long playbook produces eight chunks. There is no real user content, credential, account identifier or exported database data.

**40 queries**: 38 positive, 2 explicitly negative; 33 have chunk ground truth. Each declares a stable ID, text, category, query/target language label, rationale, relevant note slugs and optional `(slug, marker)` chunk relevance. Ground truth is manually declared, never inferred from retrieval, vocabulary matches or category labels.

| Category | Queries |
| --- | ---: |
| exact_keyword | 10 |
| configuration | 7 |
| semantic_paraphrase | 5 |
| bilingual | 5 |
| code | 3 |
| technical_synonym | 3 |
| multi_topic | 3 |
| ambiguous | 2 |
| negative | 2 |

Language labels: `en` 32, `vi` 3, `en->vi` 2, `vi->en` 3 (arrows denote query-to-source language). The two negative queries are included in those counts. These tiny, controlled groups are diagnostic examples, **not statistically representative estimates of multilingual quality**.

Markers such as `[eval:client-config]` identify relevant source sections. Each declared marker resolves to **all real default-chunker chunks containing it**, including an overlap duplicate if present; the denominator is the set of distinct `slug#chunkIndex` identities. Changing a chunker can therefore change chunk relevance, which is why chunker/configuration identity is reported. Marker text is removed before vector generation. A note hit is not automatically a relevant chunk hit; optional absent chunk truth is explicitly unscored.

Setup fails for empty notes/queries, duplicate slugs/query IDs, unknown expected notes, blank/oversized queries, empty positive note truth, mislabeled negatives, duplicate truth, malformed/unresolvable markers, unsafe fixture resource paths and impossible evaluator dimensions/diagnostic horizon. Unit tests exercise invalid fixtures and metric math separately.

## Offline embedding boundary

`OfflineEmbeddingClient` is a **test-only** implementation of the existing `EmbeddingClient`: model identity `offline-synthetic-v1`, **64 dimensions**, 26 explicit binary technical vocabulary/synonym features, accent/case normalization and L2 normalization. English/Vietnamese examples include `cache`/`bộ nhớ đệm`, `timeout`/`latency` and `readiness`/`sẵn sàng`. A constant 0.05 fallback feature keeps unknown/negative vectors finite/nonzero. Remaining dimensions are unused. It does not inspect expected slugs, query IDs, categories or language labels and does not implement natural-language reasoning, trained embeddings or local ML.

Documents use actual `EmbeddingSource.input` title/summary/chunk prefixes. The test constructs the existing strategy/hash helper with an offline model/endpoint and disabled provider properties. Its existing marker contains the legacy literal `provider=gemini:symmetric-text-v1`; that string is **source-hash identity, not a live-provider declaration**. The report separately records `provider=offline` and the entire actual strategy marker. No production strategy is rewritten to accommodate evaluation, and vectors go only into the disposable container.

The evaluator instantiates repository/chunker/offline client directly, **without a Spring application context**, Gemini adapters, SDK clients, generation, quota reservations, network transports or environment-credential lookup. All Gradle test tasks additionally force embedding/Ask off and clear `GEMINI_API_KEY`, `GOOGLE_API_KEY` and the Gemini property. Existing SDK adapter tests retain their loopback mocks. **Zero external Gemini calls**; Docker image infrastructure is not a model/provider request. There is no opt-in live mode in this harness.

## Real production retrieval, unchanged

- `KnowledgeEmbeddingRepository.replace` persists validated complete current sets using the real source hash/strategy and atomic replacement.
- `findNearestKnowledge` performs exact pgvector cosine ranking and selects one best chunk per Knowledge **before** the 20-note limit. No evaluator Java ranking/nearest-neighbor or copy of ranking SQL is used.
- `findRagChunks` applies the **2 chunks/note cap before the 8-chunk global limit**. This measures raw Ask retrieval candidates, **not** later `AskContext` source-count/serialized-character trimming or answer generation.
- Existing `findNearestChunks(..., 100)` supplies distances only, keyed to the already returned results. It never reorders or filters Semantic/RAG ranks. The current 31 eligible chunks fit its bounded diagnostic horizon; setup rejects a larger corpus instead of silently losing distances.

Five extra unscored, closely matching decoys exercise foreign-owner, stale-source, incompatible-model, incompatible-dimensions and incomplete-set exclusions. There are 29 total database notes, but reported benchmark `noteCount=24` excludes these isolation fixtures. Every returned source must belong to the 24 eligible notes; Semantic results must be unique; RAG chunks must be distinct with at most two per note. The long-playbook regression verifies two chunks plus other notes survive the global limit. Test quota rows remain empty. No ANN index, tuned SQL, threshold, hybrid ranking or production schema migration is added.

Note IDs, insertion order and `createdAt`/`updatedAt` are fixed, not random/wall-clock ranking signals. Existing SQL tie-breaks remain authoritative: Semantic ties use updated time/note ID descending; RAG ties use note/chunk ID ascending. Do not interpret high recall produced by an accidental tie as evidence of semantic understanding.

## Metric definitions

For each positive query with expected set `R`, HitRate@K is 1 if any relevant identity occurs in the first K positions, else 0; Recall@K is the number of **distinct** relevant identities in those positions divided by `|R|`. Aggregates are macro means over eligible queries, not pooled note/chunk counts. K larger than the returned list uses the full list. No hit gets reciprocal rank 0; otherwise RR is `1 / first relevant rank`.

- **Semantic Search:** note HitRate/Recall @1/3/5; **MRR@20**, using the first relevant note in the actual bounded 20-note result set. Unexpected repeated note results are reported and collapsed **before** calculating ranks/top-K; the production uniqueness regression separately fails on duplicates. An absent relevant rank is JSON `null`, not rank zero.
- **RAG note coverage:** HitRate/Recall @1/3/5/8 over raw chunk positions; repeated chunks of a note consume positions but cannot double-count a relevant note. RR/MRR is explicitly bounded to 8, not comparable directly to Semantic MRR@20.
- **RAG chunk coverage:** independent HitRate/Recall @1/3/5/8 and RR@8 for the 33 queries with declared/resolved chunk truth. The five other positive queries still contribute note metrics, not fabricated chunk scores.
- **Context diversity:** per query, `uniqueNoteCount`, `retrievedChunks`, `uniqueNoteCount / retrievedChunks` (0 for empty retrieval), and note slugs at the maximum per-note cap. Summary means include all queries, including negatives. Repeated notes in RAG are expected cap diagnostics, not Semantic duplicate failures.
- **Negatives:** exclude from Recall/HitRate/MRR means; report the nearest ranked sources/distances separately. Production has no calibrated no-result distance cutoff, so empty relevance sets must not be divided by zero or interpreted as arbitrary nearest-neighbor failures.

Every case includes declared truth, ranked output/distances, relevant ranks and missing identities at every K. Both modes include overall/category/language metrics with query, positive, negative and chunk-eligible sample counts. An ineligible aggregate is `null`, never a made-up zero quality score.

## Reports and comparability

- Human readable: `apps/api/build/reports/retrieval-eval/report.txt`
- Machine readable: `apps/api/build/reports/retrieval-eval/report.json`

`RetrievalEvaluation.Report` is the stable schema (currently `schemaVersion=1`): `evaluatedAt`, corpus header, configuration, separate `semanticSearch`/`ragContext` aggregates/groups/cases. Ordered maps/lists keep serialization repeatable. The runtime timestamp is metadata only; `deterministicIdentity()` replaces it with the epoch for exact repeated-run comparison. Reports identify corpus/model/provider/dimensions/algorithm, chunker/hash strategy and retrieval limits. Comparisons require matching configuration and fixture versions; a future fixture or vocabulary edit must bump its version, not silently claim comparable quality. Generated reports stay under ignored `build/`; **do not commit them**.

## Initial local evidence

The offline corpus reports Semantic HitRate@1 **0.8158**, Recall@1 **0.6535**, HitRate/Recall@3 and @5 **0.9737**, MRR@20 **0.8965** (38 eligible queries). RAG note HitRate@1 **0.8684**, Recall@1 **0.7061**, HitRate/Recall@3/@5/@8 **1.0000**. RAG chunk HitRate@1 **0.7576**, Recall@1 **0.7273**, HitRate/Recall@3/@5/@8 **0.9697** (33 eligible queries). RAG mean unique notes **7.75** for **8** chunks; diversity **0.96875**, mean sources at cap **0.25**. Semantic duplicate-note cases: **0**.

Two diagnostics matter more than the aggregate:

- `q-unmapped` uses an intentionally unrecognized paraphrase for Redis TTL. Semantic ranks it **15**, outside top 5 (semantic_paraphrase Recall@5 **0.8**, n=5). RAG happens to rank it **2** because of the different SQL tie-break, despite distance about **0.9647**: this is a vocabulary/tie artifact, not understanding.
- `q-playbook` finds the long note at rank **1**, but misses its relevant exhaustion section `webclient-playbook#4` under the two-chunk cap. RAG includes chunks 2/3, so note-level success hides chunk-level failure. The configuration group's chunk Recall@8 is **6/7**, despite note Recall@8 being 1.

These numbers are intentionally **not** hardcoded quality pass thresholds. Correctness invariants and a few controlled ranking regressions are asserted; misses remain visible. No vocabulary/chunker/ranking/threshold tuning is performed to improve this fixture's score.

## Previous retrieval-foundation verification

Ran `./gradlew test`, `./gradlew retrievalEval` and `./gradlew clean build` successfully. The final clean build passed **298 API tests, zero failures/errors/skipped**, including **30 new evaluator/metric/fixture/offline-provider tests**. The standalone evaluation task completed successfully in **35 seconds** locally (including Gradle/Testcontainers setup), generating both reports; the final clean build regenerated the same metric identity. Full test and clean build took about **1m43s** and **1m48s** respectively on this machine; these are observations, not performance guarantees.

Web `npm test` passed **87 tests**; `npm run lint`, `npm run build` and `git diff --check` succeeded. **No browser QA was performed or required** because Web/API runtime behavior is unchanged. Reports were inspected for corpus/eligible counts, separate metrics, misses, negative diagnostics and the offline/provider-quality disclaimer. Only test sources/synthetic resources, Gradle test-task configuration and documentation changed; no private data, generated report, environment file, dependency upgrade, migration or production ranking change is committed. Zero external Gemini calls. GitHub Actions monitoring belongs to another agent.

## RAG chunk-selection matrix

RAG chunk-selection evaluation is complete. **Production defaults are unchanged by this milestone:** `ASK_MAX_CHUNKS=8`, `ASK_MAX_CHUNKS_PER_KNOWLEDGE=2`, chunker `4000/200`; SDK stays 1.75.0, quotas, runtime SQL and Flyway V1–V9 are unchanged. This is an offline structural investigation, **Provider semantic quality NOT measured**. No live mode/model comparison, generation evaluation, hybrid/query rewriting/reranking/ANN or evaluation UI is added.

### Long-note fixtures and relevance

`chunk-selection-additions.json` adds four synthetic Markdown guides: Kubernetes deployment operations, PostgreSQL performance, Nginx reverse proxy operations and Redis cache strategy. Each has six substantive sections, operational paragraphs, supporting procedures and lists. The existing eight-chunk WebClient playbook remains intact. **Five independent long notes** are now exercised, four newly added; the new guides produce six chunks each at baseline. Smaller chunks can split sections; indexes always come from the real MarkdownChunker, never manually fabricated.

The matrix corpus is `retrieval-eval-v1+chunk-selection-v1`: **28 notes, 60 queries, 58 positive/2 negative, 53 chunk-scored queries**, **55 baseline chunks**. The original corpus/query/ground truth/vocabulary files are unchanged and evaluated independently before loading additions. All matrix variants use the identical combined corpus, including all original 40 queries. Legacy numeric scores must not be compared directly to this expanded baseline: new notes are additional competing retrieval candidates. Relevance is the explicitly declared supporting-note/section set; this evaluates recall, not an exhaustive precision judgment of every generally related guide.

Twenty added queries cover early/middle/late evidence and multiple sections in one note; existing multi-note questions remain. Declared position-case counts are **early 10, middle 6, late 9** (a multi-section query can count in two positions; 25 declared markers across 20 queries). A position declaration anchors to the *first* matching chunk in the **4000/200 baseline**, with `floor(3 * chunkIndex / chunkCount)` mapping to early/middle/late. Validation rejects unknown/wrong declarations as well as missing markers, duplicate IDs/slugs and unknown expected notes. For each variant, all marker-containing chunks are resolved anew and actual relative positions are also reported. Overlap can duplicate a marker into more than one chunk; this changes the chunk denominator, not the declared source evidence. Separate assembled **marker recall** prevents confusing that artifact with losing all evidence.

### Bounded experiment

**48 configurations**: per-note caps `1/2/3` × total RAG limits `6/8/10/12` × four sensible paired chunk variants:

| Max chunk characters | Overlap |
| ---: | ---: |
| 2000 | 150 |
| 3000 | 200 |
| 4000 | 200 — BASELINE |
| 4000 | 400 |

The explicitly marked full baseline is `c4000-o200-l8-p2`. Strategy/configuration objects are test-only; `application.yml` is never rewritten for experiments. One isolated PostgreSQL/pgvector Testcontainer and a small test connection pool serve the entire legacy+matrix run. Knowledge IDs/timestamps are fixed; source data is loaded once, and complete chunk sets are replaced per chunking strategy. Owner/stale/model/dimension/incomplete decoys are rebuilt against **each** strategy so incompatible old configuration cannot mask an exclusion defect.

The full matrix runs twice, rebuilding the index/snapshot each time, and report identities/serialized JSON must match excluding `evaluatedAt`. Nearest-chunk and relaxed-cap diagnostics are cached **only inside one unchanged strategy/index snapshot**. Every one of the 48 configurations still calls the actual `findRagChunks(owner, strategy, vector, totalLimit, perNoteCap)` for every query. A relaxed `limit=100` query provides attribution and source text, never replacement ranking. Actual limited-query results must equal that production SQL prefix; an uncached baseline run must match cached diagnostics exactly. No Java nearest-neighbor/ranking SQL copy is introduced. Eligible chunks must fit the existing diagnostic horizon of 100, otherwise setup fails.

### New diagnostic definitions

- Chunk and note HitRate/Recall @1/3/5/8 remain separate macro means. MRR's horizon is the variant's total limit. For limit 6, `@8` is **censored at 6**, not eight returned chunks. All variants are compared on the same query set; no quality threshold makes tests fail because one variant is not best.
- `supportingChunkRecallGivenRelevantNoteRetrieved`: per query, keep only expected chunks whose note is present in the **full** selected result, then compute selected/eligible coverage; macro-average over nonempty eligible queries. Report conditional query and eligible-chunk counts. Missing notes are listed separately; negatives/absent chunk truth remain unscored.
- `noteHitChunkMissCount`: queries with at least one eligible expected chunk from an already present relevant note and at least one such chunk missing. Lists expose affected query IDs. This does not silently blame absent notes on chunk selection.
- `capPressureChunks`: missing expected chunks whose present note has reached the actual per-note cap. Each expected chunk also has its PostgreSQL-derived rank **within that note**, rank after cap (`null` when excluded), selection flag and reason `SELECTED`, `PER_NOTE_CAP` or `TOTAL_LIMIT`. The relaxed production query distinguishes cap exclusion from an eligible chunk beyond the global limit.
- Size classes use **baseline** fixture counts: short 1–2, medium 3–4, long ≥5. Position/size tables are micro selected/expected chunk counts, not query macro recall. Long-only position buckets avoid inflating early coverage with one-chunk notes; actual variant positions are separately available. Empty size groups are absent, not fake zero-quality values.
- Source diversity retains unique notes, retrieved chunk count, their ratio, notes at cap and repeated-note cases (expected for RAG).
- Raw selected chunk characters: per-query sum, mean, nearest-rank p50/p95 and max. Cost estimates reuse `AiQuotaProperties.estimate` with the default **2.5 characters/token**. They are proxies, not tokenizer results or exact Gemini billing.
- `AskContext.assemble` is additionally exercised with each variant's cap/limit and unchanged **6-source/24000 serialized-character budgets**. It reports assembled chars/chunks/sources and exact declared-marker coverage in **included** text, detecting evidence lost to source filtering/trimming. No answer/provider call is made. Serialized-context token estimates exclude question, system/schema/prompt and output; actual generation TPM reserves their overhead too.
- Indexing proxy: actual corpus chunk count, mean/max chunks per note, summed `EmbeddingSource.input` title/summary/chunk characters, conservative tokens and per-note native batch count at the production default 16 inputs. Repeated title/summary and overlap increase this input. The fixed Gemini adapter task prefix and request overhead are excluded. These are storage/reindex/background-quota proxies, not measured requests, runtime or live charges.

### Observed comparison

Selected rows below summarize the complete 48-row matrix in the generated report. Numbers are from the combined corpus, not the historical 24-note report.

| Variant (chars/overlap, limit, cap) | Chunk Recall@1 / @3 / @5 / @8 | Chunk MRR | Conditional recall | Note-hit/chunk-miss | Mean raw chars | Diversity |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| **4000/200, 8, 2 — BASELINE** | .6226 / .8962 / .9057 / .9057 | .7767 | .9057 | 6 | 8362.4 | .8604 |
| 3000/200, 6, 3 — best observed | .6226 / .8868 / .9245 / .9245 | .7799 | .9245 | 5 | 6886.6 | .7833 |
| 4000/200, 8, 3 | .6226 / .8868 / .9245 / .9245 | .7799 | .9245 | 5 | 8938.6 | .7875 |
| 4000/200, 12, 2 | .6226 / .8962 / .9057 / .9057 | .7767 | .9057 | 6 | 12680.0 | .8389 |
| 2000/150, 8, 2 | .4623 / .6509 / .6792 / .6792 | .5739 | .6792 | 18 | 7459.1 | .8271 |
| 4000/400, 8, 2 | .5755 / .8082 / .8428 / .8428 | .7767 | .8428 | 14 | 8946.0 | .8604 |

Note Recall@8 is **1.0** in all rows above. Baseline chunk HitRate @1/3/5/8 is **.6604/.9245/.9245/.9245** (53 eligible queries). The configuration category's baseline chunk Recall@8 is **.8750**, versus **.9375** with cap 3 at 4000/200. JSON preserves every category/language's metrics and sample counts, not just these selected rows.

`bestObserved` transparently maximizes Chunk Recall@8, then minimizes mean raw chars, then uses variant name for a deterministic tie. It is **not** a production settings selector. 3000/200 and 4000/200 produce identical chunks/inputs on this fixture, so the best label's 3000 value is a tie-break artifact, not evidence that 3000 is a better live chunk size. Its limit-6 `@8` is censored. Its gain over baseline is **+0.0189 Recall@8**, **+0.0031 MRR**, **−1 miss case**, but diversity is **−0.0771**. It improves only **1/5 independent long-note fixtures**.

### Root causes, not only aggregate recall

`q-playbook` still expects `webclient-playbook#4`. The actual within-note distance ordering puts chunks **2, 3, 4** first, so the relevant section's within-note rank is **3**. Cap 1/2 excludes it for **every** total limit and all four chunking pairs; cap 3 selects it in all 16 cap-3 configurations. Total limit 8→12 cannot admit a chunk eliminated by cap 2. This explains the original miss without claiming the fake understands exhaustion semantics.

Five note-hit/chunk-miss cases remain in the best configuration: Kubernetes combined sections, PostgreSQL combined sections, playbook early, playbook late and playbook combined. Requiring multiple supporting sections exposes limits that one-source note recall hides. Baseline has those five **plus `q-playbook`**. Baseline misses comprise seven expected chunks blocked by cap; the winning label removes only one.

For **long notes only**, baseline micro chunk coverage is early **6/10**, middle **5/7**, late **8/9**; the best row changes only middle to **6/7**. Short-note chunks are **34/34** covered. This corpus therefore does **not** demonstrate a universal later-position bias: within-note similarity/ties and multi-section demands matter more. Sample sizes are tiny. In `4000/400, limit6, cap3`, `q-postgres-performance-middle` provides an actual `TOTAL_LIMIT` exclusion: the expected chunk survives its note cap but is ranked beyond the global cutoff. Other misses remain cap/ranking effects, not proof that all contexts need a larger total limit.

Overlap 400 increases marker duplication. Raw chunk Recall@8 for cap2 drops to .8428, while assembled **marker** recall is .9245; some evidence is present in one copy even though another expected copy is absent. Do not read that denominator change as a pure quality regression or tune to make every overlap copy selected. Smaller 2000 chunks alter where procedural markers land and redistribute vocabulary within one note; the measured structural miss count rises, not a prediction about Gemini semantics.

### Context and indexing tradeoffs

Baseline raw mean/p50/p95/max context chars are **8362.4 / 9662 / 12321 / 15667**, with mean estimated raw tokens **3345.4**. Baseline assembled JSON mean is **8694.4** chars / **3478.2** estimated tokens. The best label uses raw mean **6886.6** (−17.6%) / **2755.1** estimated tokens, but assembled mean is **7638.5** / **3055.8** tokens and fewer distinct sources are retained. Changing **only cap 2→3 at limit8** costs raw **+6.9%** and assembled **+9.6%**, while diversity drops .8604→.7875. Increasing **only limit 8→12** costs raw **+51.6%** with no chunk recall gain; unchanged source limits mean generation actually receives a smaller assembled mean **9894.9** chars, not every retrieved chunk.

| Chunking pair | Corpus chunks | Mean/max per note | Embedding input chars | Estimated tokens | Default-16 per-note batches |
| --- | ---: | --- | ---: | ---: | ---: |
| 4000/200 baseline | 55 | 1.9643 / 8 | 76921 | 30769 | 28 |
| 3000/200 | 55 | 1.9643 / 8 | 76921 | 30769 | 28 |
| 2000/150 | 64 | 2.2857 / 9 | 77796 | 31119 | 28 |
| 4000/400 | 55 | 1.9643 / 8 | 82247 | 32899 | 28 |

2000/150 increases stored chunk rows **16.4%**, full embedding input **1.1%**, and keeps the same per-note request count for this small corpus; larger real notes can cross batch boundaries and consume more RPM/RPD and indexing time. Overlap 400 increases input **6.9%** without changing row count. A production chunk-setting change invalidates source hashes and requires reindexing, so a label tie is not a reason to incur that migration. Larger assembled contexts can increase estimated Ask TPM, latency and Free Tier pressure; proxies cannot determine real latency or account entitlements. **No quota ceiling is automatically increased.**

### Report contract and decision

The original `report.txt`/`report.json` remain available. The same `./gradlew retrievalEval` additionally generates ignored:

- `apps/api/build/reports/retrieval-eval/chunk-selection-report.txt` — baseline, full comparison table, recommendation, per-variant tradeoffs/category/language summaries and compact case diagnostics.
- `apps/api/build/reports/retrieval-eval/chunk-selection-report.json` — schemaVersion 1, corpus/provider identity, declaration counts, explicit baseline, all 48 variants/configs/metrics/deltas/costs/breakdowns and full case-level ranks/truth/exclusions.

Delta fields are signed variant−baseline for Chunk Recall@8, MRR, note-hit/chunk-miss count, mean context chars, diversity and note Recall@8. Report timestamp is excluded from deterministic identity. Case diagnostics contain no raw vectors, real user content or keys. Snapshot caches are test data only, neither persisted nor included in reports.

**Decision: KEEP BASELINE. No production tuning applied.** The best raw-score row adds just one covered section and improves one of five long fixtures; other multi-section misses remain. Cap-only tuning grows context and reduces diversity, a larger limit pays context cost without fixing cap exclusion, 3000 is structurally identical here, and overlap changes truth multiplicity. This does not establish consistent multi-fixture useful-evidence gains with acceptable live costs under all six production-change conditions. There is no magic weighted score, automatic winner application or test failure when baseline is not top. `q-unmapped` remains visible and the vocabulary is untouched.

### Milestone verification

Executed locally for this milestone:

- `./gradlew test`: successful in **3m24s**, **310 tests**, zero failures/errors/skips; 12 new diagnostic unit tests plus the extended real-persistence evaluation test.
- `./gradlew retrievalEval`: successful in **1m31s**, legacy evaluation and all 48 matrix variants, each repeated for deterministic identity.
- `./gradlew clean build`: successful in **6m1s**, recompiling the final long-only position diagnostics and passing all **310 tests** again.
- `npm test`: **87 passing tests**; `npm run lint` and `npm run build`: successful.
- `git diff --check`: successful; generated reports are ignored. No runtime Java, SDK dependency, migration, infra, workflow or Web source change is part of this milestone.

The harness directly constructs only the offline embedding fake; no Gemini adapter or generation client is constructed, even when ambient credentials exist. **Zero external Gemini calls**; the isolated evaluation database's persistent quota counter remains empty. No real user notes are used. No browser QA or live provider evaluation was performed. An existing local `application.yml` change was preserved unchanged and excluded from the milestone commit.

## Manual live Gemini retrieval evaluation

The opt-in `retrievalEvalLive` JavaExec is independent of Test/check/build/CI. It requires both the dedicated-task JVM marker and `RETRIEVAL_EVAL_LIVE=true`, then valid existing backend Gemini settings. No Spring application context, normal datasource, OAuth, attachment storage, answer client or generation request is constructed. Only classpath synthetic fixtures can be loaded; the database helper has no caller-supplied connection string and owns its PostgreSQL/pgvector Testcontainer.

```bash
cd apps/api
RETRIEVAL_EVAL_LIVE=true ./gradlew retrievalEvalLive
```

Existing Spring environment/YAML credential resolution is reused, with no `.env` loader or new key file. The user's intentionally modified `application.yml` remains local, byte-preserved and excluded from commits; never display its diff or dump Binder/provider exceptions. No SDK HTTP payload/auth logging. The CLI prints only model/dimensions, synthetic counts, budgets and sanitized outcomes. Do not share local build artifacts containing local configuration.

The configured model/dimensions and **4000/200 baseline** are mandatory; only the official Gemini origin is accepted. Manual adapter validation is enabled in memory without changing runtime flags. Documents use actual `EmbeddingSource.input`, queries actual query text; the production `GeminiEmbeddingClient` alone adds its existing symmetric task prefix. Each unique input is sent once via native batches at the configured size. Run-local vector caches have no provider fallback. Documents reserve global/background quotas; queries global quotas in the isolated DB. Normal application quota rows are untouched, although real provider account quota is consumed.

Preflight checks planned requests/estimated tokens, including task-prefix overhead and per-batch rounding; each batch is guarded again. Hard ceilings: **20 requests / 60000 estimated input tokens**, reducible through `RETRIEVAL_EVAL_LIVE_MAX_REQUESTS` / `RETRIEVAL_EVAL_LIVE_MAX_ESTIMATED_INPUT_TOKENS`. These are safety limits, not Google entitlements. Native batching may span fixture notes, preserving independent per-chunk title/summary/content inputs. Counts are adapter request attempts (one SDK batch request each with retries disabled), with successful counts separately recorded.

Only local minute exhaustion can wait: `RETRIEVAL_EVAL_LIVE_MAX_WAIT_SECONDS` defaults to **180 total**, allowed 0–600. Read-only prechecks inspect the same quota windows/estimator; the adapter still atomically reserves. Sleep advances to the next minute, is interruptible, without tight polling. Single-batch oversize or daily exhaustion fails immediately. Any provider 429/rate boundary, timeout/5xx or bad/missing/mismatched vectors stops permanently; **no provider retry**. A 429 and final local reservation denial share the existing sanitized production exception, so reports preserve that ambiguity. Incomplete runs replace only live reports with sanitized status/usage, no partial quality metrics or raw error body.

After embeddings, **12** local-only configurations (limits 6/8/10/12 × caps 1/2/3) reuse live vectors. Production SQL supplies all ranks. Semantic note metrics and RAG note/chunk metrics retain category/language, conditional recall, note-hit/chunk-miss, diversity/cap pressure, long-note positions and actual AskContext budgets. `q-playbook`, `q-unmapped`, all ground truth and en/vi/en→vi/vi→en cases remain unchanged. Negative nearest-note/distance/rank diagnostics remain unscored; no threshold or quality-score build gate is invented.

Separate ignored `live-report.json` / `.txt` record status, corpus/chunk/query identity, provider/model/dimensions/strategy, batch size, hard budgets, document/query attempts/inputs, successful calls, estimated tokens and duration; live and freshly measured **same-expanded-corpus** offline baselines/12 variants are included. Offline files are not overwritten. Compare ranks/ground-truth metrics, **never absolute cosine distance scales** across models. Context/indexing diagnostics retain the default-2.5-char/token proxy; live request usage uses the configured quota estimator. No credentials, raw vectors or real Knowledge appear in reports.

**Measurement only:** no production model/dimensions/chunker/cap/limit/ranking/quota changes, migration, SDK upgrade, CI live task, generation/UI/hybrid/reranking/query rewrite/ANN. A successful synthetic run is not proof of answer grounding or private-corpus quality. Production tuning requires a separate reviewed milestone.

### Approved live run — observed results

One approved run succeeded using **`gemini-embedding-2`, 768 dimensions**, SDK **1.75.0**, **28 synthetic notes / 55 baseline chunks / 60 queries** (58 positive, 2 negative, 53 chunk-scored). Chunking stayed **4000/200**. Exactly **55 document inputs + 60 distinct query inputs** were embedded once, in native batches of **16**: **4 document + 4 query requests = 8 successful requests**. Estimated input tokens, including task-prefix overhead/per-batch rounding, were **33455**, below the hard **20-request/60000-token** ceilings. Ephemeral persistent day counters independently matched **8 global / 4 background requests / 33455 global estimated tokens**. Two bounded local-minute waits occurred, no provider failures/retries or generation calls. Evaluator duration **121000ms**; Gradle task **2m3s**. Database discarded afterward; normal application data/quota rows never accessed.

Same-expanded-corpus macro comparison (Semantic MRR@20; RAG MRR@8):

| Path / provider | HitRate@1 / @3 / @5 / @8 | Recall@1 / @3 / @5 / @8 | MRR |
| --- | --- | --- | ---: |
| Semantic — LIVE GEMINI | .7241 / .9483 / 1.0000 / — | .6178 / .9483 / 1.0000 / — | .8397 |
| Semantic — OFFLINE SYNTHETIC | .7414 / .9828 / .9828 / — | .6552 / .9770 / .9828 / — | .8546 |
| RAG notes — LIVE GEMINI | .7241 / .9310 / .9828 / 1.0000 | .6178 / .9310 / .9828 / 1.0000 | .8368 |
| RAG notes — OFFLINE SYNTHETIC | .7414 / 1.0000 / 1.0000 / 1.0000 | .6552 / .9885 / 1.0000 / 1.0000 | .8563 |
| RAG chunks — LIVE GEMINI | .6604 / .9057 / .9623 / .9811 | .6415 / .8774 / .9528 / .9717 | .7931 |
| RAG chunks — OFFLINE SYNTHETIC | .6604 / .9245 / .9245 / .9245 | .6226 / .8962 / .9057 / .9057 | .7767 |

Gemini improves larger-K coverage, not every rank metric: Semantic Recall@1/MRR and RAG note MRR are lower here than the controlled fake. Conditional supporting recall is **.9717 live vs .9057 offline**, with 53 eligible queries/60 expected chunks; note-hit/chunk-miss falls **6→2**. Source diversity **.8667 vs .8604**; live baseline averages 6.9333 distinct notes, 43 repeated-note cases and 1.0667 notes at cap. Raw context mean/p50/p95/max: **7419.2 / 7937 / 10513 / 11579** chars, estimated mean tokens **2968.1**; assembled mean **7733.5** chars / **3093.8** tokens. Included-marker recall .9717: assembly adds no further declared-evidence miss at baseline.

`q-playbook`: expected `webclient-playbook#4` is **within-note rank1/global rank1**, selected by **all twelve live variants**, including cap1/2. Offline within-note rank3/cap2 exclusion is not a general Gemini defect; cap3 is no longer needed for this query. `q-unmapped`: expected `redis-ttl` is **Semantic rank1/RAG chunk rank1**, versus **Semantic rank15** offline on this same corpus. No fixtures, queries, vocabulary or ground truth changed after observing results.

Remaining baseline misses: **`q-postgres-performance-combined`** (late chunk5 within-note rank3, cap2 exclusion) and **`q-playbook-late`** (late chunk7 within-note rank6, cap2/3 exclusion). All relevant notes are present. Long-note coverage: early **10/10**, middle **7/7**, late **7/9**, versus offline 6/10, 5/7, 8/9. Overall gains do not eliminate late-section gaps. Cap3 recovers PostgreSQL's late section, not playbook chunk7; a larger total limit cannot admit cap-excluded evidence. This is declared-fixture evidence, not a universal later-position failure.

All twelve configurations reused vectors, with **zero additional Gemini calls**. Representative rows; full reports preserve every variant/K/category/language/rank/exclusion:

| Limit / cap | Chunk Recall@8 | Conditional recall (full limit) | Note-hit/chunk-miss | Diversity | Mean raw chars |
| --- | ---: | ---: | ---: | ---: | ---: |
| 6 / 1 | .9151 | .9151 | 7 | 1.0000 | 4823.5 |
| 8 / 1 | .9151 | .9151 | 7 | 1.0000 | 6186.7 |
| 6 / 2 | .9717 | .9717 | 2 | .8611 | 5864.9 |
| **8 / 2 — BASELINE** | **.9717** | **.9717** | **2** | **.8667** | **7419.2** |
| 12 / 2 | .9717 | .9717 | 2 | .8819 | 10455.3 |
| 6 / 3 | .9811 | .9811 | 1 | .7833 | 6154.9 |
| 8 / 3 | .9811 | .9811 | 1 | .7813 | 8232.6 |
| 12 / 3 | .9811 | .9811 | 1 | .7986 | 11798.6 |

Limit6 `@8` is censored at6. Best chunk-recall rows use cap3, improving just one additional section with diversity loss. At limit8, cap3 adds **11.0% raw / 13.1% assembled characters** (assembled mean 8748.6); limit12/cap2 adds **40.9% raw characters** without recall gain. **No production defaults changed**, even for cheaper limit6 rows. Measurement is not tuning approval.

Language highlights: en has **51 positive + 1 negative** queries, Semantic Recall@5 1.0, RAG chunk Recall@8 **.9674**. vi has **2 positive + 1 negative**, en→vi **2 positive**, vi→en **3 positive**; those seven positive cases achieve Semantic/RAG note/chunk HitRate/Recall at every K **1.0**. Tiny groups do not establish general bilingual quality. Category highlights: configuration (16) chunk Recall@8 **1.0 vs .875 offline**; multi-topic (9 queries, 8 chunk-scored) **.9375 vs .7500**; code (6) **1.0**, MRR **.7556 vs .5278**. Paraphrase (7 queries, 6 chunk-scored) Recall@8 stays **.8333**; late playbook remains missing. Exact-keyword (10) and technical-synonym (3) chunk Recall@8 are 1.0; ambiguous (2) is note-only, not fabricated chunk scoring. Full group counts/metrics remain in JSON.

Negative nearest diagnostics: `q-garden` → `docker-multistage`, rank1, cosine distance **.3363**; `q-travel` → `minio-uploads`, rank1, **.3250**. They remain unscored; no rejection threshold or numerical fake-distance comparison is inferred.

Before live: **331 API tests, zero failures/errors/skips** (`./gradlew test` **3m21s**, `./gradlew clean build` **3m46s**); `./gradlew retrievalEval` **1m29s**, successful deterministic offline legacy/matrix reports; **87 Web tests**, lint/build and diff check successful. Normal commands made **zero external Gemini calls**. The 21 new offline tests cover gates/config/budgets, one-time embeddings/vector reuse, failures/429/bad vectors, reports, real pgvector selection and persistent quota pacing/interruption. One prolonged interrupted full-suite attempt lost a PostgreSQL socket; final rerun/clean build passed. Flag=false dedicated task failed before provider creation as intended. No browser-visible behavior changed; browser QA not required.

**Next: focused long-note supporting-chunk selection/context-cost evaluation**, separately reviewed before tuning. Note coverage is strong on this tiny corpus; actionable gaps are two late-section cases, not evidence for immediate Hybrid Search or indiscriminate cap growth. Review fixture semantics, within-note ranking and source/context tradeoffs first. No further live run, fixture alteration or production tuning is authorized automatically. Generation grounding, private-corpus quality, calibrated thresholds and multilingual generalization remain unmeasured.

## Limitations and next recommendation

Small synthetic technical examples plus a vocabulary fake cannot predict Gemini semantic/paraphrase/bilingual quality, private-corpus performance or production latency. Exact cosine retrieval has no relevance cutoff; metadata prefixes, tie-breaks, chunk overlap and per-note caps affect outcomes. This is not answer generation, citation entailment, security authorization endpoint testing, live token accounting or end-to-end generation evaluation. Actual AskContext selection/character budgets are exercised, but token figures remain estimates. Existing owner/security/quota/API regression suites cover their established contracts separately.

The approved **Manual Live Gemini Retrieval Evaluation is complete for this synthetic corpus**, not production/private Knowledge. Next is the separately reviewed focused supporting-chunk evaluation above; no further live run/tuning is automatically authorized. Token-aware/heading-aware chunking, hybrid/reranking, calibrated thresholds, exact source navigation and auto tagging/summarization remain future work. Offline controlled vocabulary, marker location/overlap, closed declared relevance and small topic/position groups limit generalization; no answer correctness/per-claim entailment measured. Isolated counters cannot know other clients' real Gemini project usage; run ceilings/no-retry remain essential. Token figures are estimates, not provider billing/tokenizer measurements. Live model results can vary over time; no live CI quality gate exists.
