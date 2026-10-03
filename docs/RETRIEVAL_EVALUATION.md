# Retrieval quality evaluation — offline synthetic

This milestone establishes a repeatable **retrieval** benchmark, not an assessment of Gemini, generated answers, factual correctness or claim entailment. Reports explicitly state **provider semantic quality NOT measured**. No production retrieval/configuration/schema/UI changes are made. Google Java GenAI SDK stays **1.75.0**.

## Run

From `apps/api`, with Java 17 and Docker available:

```bash
./gradlew retrievalEval
```

No application server, OAuth session, API key, developer PostgreSQL or MinIO is required. One disposable PostgreSQL 17/pgvector Testcontainer uses the existing project image and applies unchanged Flyway V1–V9. The corpus is loaded once, all queries run twice against the unchanged snapshot, and deterministic report identities are compared. The container is discarded afterward. No running application database, private notes or real quota counters are accessed.

The full evaluation also runs in ordinary `./gradlew test` and `./gradlew clean build`: 24 notes/40 queries are small enough to keep this real-SQL regression gate practical. The dedicated task always reruns even if Gradle considers its inputs unchanged. It filters to the integration evaluator; independent metric/fixture/provider unit tests run in the normal suite. No new dependency, CI workflow or runtime provider is introduced.

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

## Local milestone verification

Ran `./gradlew test`, `./gradlew retrievalEval` and `./gradlew clean build` successfully. The final clean build passed **298 API tests, zero failures/errors/skipped**, including **30 new evaluator/metric/fixture/offline-provider tests**. The standalone evaluation task completed successfully in **35 seconds** locally (including Gradle/Testcontainers setup), generating both reports; the final clean build regenerated the same metric identity. Full test and clean build took about **1m43s** and **1m48s** respectively on this machine; these are observations, not performance guarantees.

Web `npm test` passed **87 tests**; `npm run lint`, `npm run build` and `git diff --check` succeeded. **No browser QA was performed or required** because Web/API runtime behavior is unchanged. Reports were inspected for corpus/eligible counts, separate metrics, misses, negative diagnostics and the offline/provider-quality disclaimer. Only test sources/synthetic resources, Gradle test-task configuration and documentation changed; no private data, generated report, environment file, dependency upgrade, migration or production ranking change is committed. Zero external Gemini calls. GitHub Actions monitoring belongs to another agent.

## Limitations and next recommendation

Small synthetic technical examples plus a vocabulary fake cannot predict Gemini semantic/paraphrase/bilingual quality, private-corpus performance or production latency. Exact cosine retrieval has no relevance cutoff; metadata prefixes, tie-breaks, chunk overlap and per-note caps affect outcomes. This is not answer generation, citation entailment, security authorization endpoint testing, token budgeting or end-to-end context selection evaluation. Existing owner/security/quota/API regression suites cover their established contracts separately.

Recommended next: a separately scoped **source-chunk coverage and relevance evaluation** plan, centered on long configuration notes and genuine paraphrases. Before optimizing the two-chunk cap, compare note-vs-chunk outcomes and downstream context budgets; before claiming multilingual quality, obtain operator approval for a small **synthetic-only live Gemini evaluation** with an explicitly named model/dimensions/strategy, consent, backend-only credentials, bounded quota/cost and separate reports. No live mode/key handling is implemented here. Token-aware/heading-aware chunking, hybrid/reranking, calibrated thresholds, exact source navigation and auto tagging/summarization remain future work, not automatically started.
