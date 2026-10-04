# Metadata suggestion quality evaluation

Developer/operator benchmark, not background enrichment or an evaluation UI. Uses synthetic notes only; never reads application Knowledge, revisions, attachments, sessions or owner identifiers. Production SDK stays **1.75.0**. Suggest remains explicit, disabled by default and user-applied. No migration, model switch, prompt tuning, input-strategy tuning, LLM judge, tools, search or retries.

## Corpus and ground truth

`apps/api/src/test/resources/metadata-eval/corpus.json` is **metadata-eval-v1**: 20 curated synthetic Markdown notes across 16 topics (Spring Boot, WebClient, Reactor Netty, PostgreSQL, pgvector, Redis, Kafka, Nginx, Docker, Kubernetes, OAuth/OIDC, Flyway, GitHub Actions, MinIO/S3, Next.js, Testcontainers). English, Vietnamese and Vietnamese with English technical terms are declared source-language groups, not detected language correctness.

Each fixture declares title/current summary/existing tags, canonical expected tags, a small explicit alias list, required summary concepts and accepted phrases, evidence phrases, forbidden claims/tags and a rationale. No fuzzy matching or generated ground truth. Canonical/alias collisions, missing tags/concepts/evidence, duplicate IDs and invalid fields fail offline validation. Ground truth is reviewable, not a complete universe of valid tags/facts.

Short is ≤800 UTF-16 units; medium 801–32000; long >32000. Four long cases use deterministic, explicitly synthetic repeated Markdown operational appendices in the source language, retaining authored decisions near the beginning, at ~18000, after ~34000, or both beginning and end. The repetition deliberately isolates position; it is **not representative of every real long document**. Two long cases contain after-32k ground truth. Generated text is derived only from checked-in fixtures; no database exports.

## Production contract traceability

`MetadataSuggestionInputBuilder` is the same pure Unicode-safe prefix/tag sorting component used by the production snapshot loader and benchmark. Evaluator receives the full synthetic note to score it, but sends only the prepared prefix. The actual production `GeminiKnowledgeMetadataSuggestionClient` handles native schema, unchanged instructions, output validation, current-tag removal, timeout, no-retry transport and quota reservation.

Identity records report/metrics/corpus versions, corpus fingerprint, provider/model/SDK, **metadata-prompt-v1**, **metadata-schema-v1**, prompt/schema fingerprints, **prefix-32000-v1** (configured limit reflected in its identity), content/summary/tag/output limits and timestamp. Fingerprints are of public synthetic corpus/prompt/schema only, never secrets. Bump explicit versions if semantics change. A compatible model change requires configuration only, not corpus/code edits.

## Transparent metrics

- Tags: normalize with strip + Unicode NFKC + locale-independent upper/lower fold; resolve only explicit aliases. No fuzzy semantic equivalence. Production whitespace/# normalization runs first. Relevant suggested concepts recovered / returned tag count = precision; recovered / expected **new** tags = recall; F1 = harmonic mean, zero when both P/R are zero. Multiple aliases of one expected concept recover it only once. Existing canonical/alias concepts are excluded from the recall denominator. No suggestions yields zero precision/recall/F1; all-existing-tag fixtures would need separate N/A handling and are not in v1.
- Benchmark-only generic-tag rate, explicit forbidden-tag counts and mean returned tag count are separate diagnostics, not production blacklists. Undeclared but reasonable tags can lower precision; inspect them manually.
- Summary: declared accepted-phrase matches / required concepts = coverage, not exact reference-summary matching. Forbidden phrase flags are an incomplete hallucination diagnostic; substring/negation ambiguity may create false positives. Characters are UTF-16 units, words whitespace-separated; covered concepts per 100 chars is only a conciseness diagnostic.
- Evidence presence is checked across the actual title/current summary/prefix/current tags. Report VISIBLE_TO_MODEL versus OUTSIDE_CURRENT_INPUT as visible/invisible concept/tag lists. Show visible-only and full-note recall/coverage, plus visible required-concept fraction as the **evidence visibility ceiling**, not a guarantee or hard mathematical ceiling (a model can guess unseen facts). Empty visible expected sets are N/A and excluded from visible macro averages.
- Aggregates are macro means of per-case values (F1 is mean per-case F1, not F1 of aggregate P/R); forbidden counts are summed. Nearest-rank p50/p95 summary length and provider latency. Case counts make small language/category/length/position groups explicit.
- Usage: total attempted/reserved provider requests, fake calls, conservative estimated input tokens including serialized JSON/system/schema overhead, valid structured-output rate and live duration/latencies. Not actual provider billing/token usage. Adapter sanitization intentionally does not distinguish 429 versus timeout versus malformed output in reports. Failures stop immediately; partial cases are reviewable but have **no aggregate quality baseline**.

**Concept matching is not semantic correctness, full entailment verification, language verification or proof of zero hallucination.** There is no opaque overall score or uncalibrated CI quality threshold.

## Human review

Ignored `live-review.md` contains each title, language/topic/position, canonical/alias/evidence ground truth, current tags, generated summary/new tags and per-case metrics. Read the corresponding source fixture by ID for full evidence.

Ratings stay blank: summary factuality, coverage, conciseness, usefulness, language fit; tag specificity, usefulness, consistency. Use 1–5 (1 poor, 3 usable with edits, 5 strong), with written explanations for unsupported claims, missing facts, terminology, inconsistent aliases and language changes. No automatic human ratings or second generation/judge call.

## Offline and live execution

```bash
cd apps/api
./gradlew metadataEval
```

Always fake-only: never loads application.yml/credentials, starts Spring or constructs a provider/database. Stable deterministic reports at `build/reports/metadata-eval/report.txt` and `report.json`; offline timestamp is epoch, no timing measurements. Fake output deliberately includes one generic tag and forbidden claim to exercise diagnostics. Normal Test tasks force metadata/Ask/embeddings off and clear credentials. Tests use only fakes/SDK loopback/isolated PostgreSQL. `test`, `check`, `build`, `clean build`, Web validation and CI never invoke live mode, even with ambient credentials. All build reports are already gitignored.

Live evaluation makes real Google calls with **synthetic content only**, using the existing secure local Gemini configuration without moving/printing its key. Independently gated by dedicated JavaExec task, exact `METADATA_EVAL_LIVE=true`, validated existing metadata/key/quota configuration and official HTTPS Gemini origin. Manual process enables metadata validation only; production feature flags remain unchanged.

```bash
# Only after separate approval for a new bounded live run:
METADATA_EVAL_LIVE=true ./gradlew metadataEvalLive
```

Safety defaults/hard maxima: `METADATA_EVAL_LIVE_MAX_REQUESTS=20`, `METADATA_EVAL_LIVE_MAX_ESTIMATED_INPUT_TOKENS=150000`, `METADATA_EVAL_LIVE_MAX_WAIT_SECONDS=180` (max 600). Whole-corpus request/token/per-request-minute/day preflight runs before constructing the adapter or making calls. No silent budget expansion. One note = one generation request, zero embeddings. Existing `AI_METADATA_MODEL`, content/output budgets, timeouts and metadata quota values are respected; quota protection must be enabled.

Own disposable PostgreSQL Testcontainer contains **only the existing V9 quota table**, not Knowledge/application tables. Actual `AiQuotaLimiter` persists independent metadata minute/day reservations there; exact token accounting is checked after the run. Read-only local-quota pacing may wait a bounded interruptible next-minute interval **before** a request, never retry a provider failure. Production counters are neither read nor reset; project/model quotas are still real and shared externally. Concurrent application usage is not coordinated with this isolated quota state. Any 429/timeout/5xx/invalid output stops without retry, prompt tuning or an optimization loop. Do not rerun after substantive provider usage without new approval.

Live reports: `live-report.txt`, `live-report.json`, `live-review.md` in the same ignored directory. Incomplete configuration/budget failures emit a sanitized marker; failed runs do not claim a full baseline. Do not use normal application logs for synthetic outputs/raw provider bodies. No workflow changes or CI secrets.

## Future model/contract comparison

1. Preserve an approved baseline JSON/review outside `build` **before** `clean` or the next run; generated outputs are not committed.
2. Separately approve a candidate run using `AI_METADATA_MODEL=<compatible-candidate>` with the same opt-in task and corpus. Do not change production defaults just to evaluate it.
3. Compare report identity first: same corpus version/fingerprint and metrics/report schema; record model, prompt/schema versions/fingerprints, input strategy/content limit and output budget differences explicitly. For a model-only comparison keep other controls unchanged. Corpus changes need a new version, not silent baseline replacement.
4. Compare P/R/F1, generic/forbidden diagnostics, full/visible concept coverage, case-level omissions, language/category/position groups, latency and usage. Package-private `MetadataEvaluation.compare` demonstrates guarded separate deltas in offline tests; JSON is the reusable operator contract, no extra comparison dashboard/task.
5. Review human rubric before approving/rejecting a production model/strategy change. This milestone measures one baseline, not a calibrated threshold or real/private-corpus quality claim.

## Baseline and validation evidence

One approved run on **2026-10-04** used **gemini-3.5-flash-lite**, SDK **1.75.0**, unchanged prompt/schema/prefix/output budget. **INCOMPLETE**: 18 conservative attempted requests, 17 validated outputs, 39071 estimated input tokens attempted (66027 planned for all 20). The run stopped at `long-middle` without retry; both after-32k cases were never attempted. Sanitized [identity/usage attempt record](metadata-evaluation-attempt.json) is intentionally committed; generated reports/outputs remain ignored. This is **not a completed full-corpus quality baseline** and cannot be compared as one.

Generation/pacing duration **33.874s**, Gradle task **44s** including setup; attempted request latency mean/p50/p95 **1194/1157/1758ms**. Valid-output rate **17/18 = 94.44%**, not a claim that the failing response was specifically malformed (the production boundary sanitizes quota/HTTP/parse failures). We cannot identify 429 versus another provider/output failure from this sanitized exception. Do not infer a root cause or rerun to fill the gap without new live approval: substantial quota was already consumed.

Exploratory observations on **only the 17 completed cases**, **not** full-corpus aggregate metrics/baseline: macro tag precision **.4873**, recall **.8235**, F1 **.6104**; generic-tag rate **0**, declared forbidden tags/claims **0**; concept coverage/visible coverage **.9706**. Full-note and visible coverage coincide in this subset because it contains no invisible ground truth. Do not interpret zero diagnostic flags as zero hallucination. These figures must not become CI thresholds or a candidate-model comparison baseline.

Case review highlights (human ratings remain blank): OIDC tags missed declared CSRF/session concepts; several technically plausible extra tags lowered closed-ground-truth precision. Nginx expresses “time between read operations”, but that phrase is outside the frozen aliases, so its .5 concept score is a known false negative. The Next.js mixed-language fixture received an English summary; other inspected Vietnamese/mixed summaries retained Vietnamese. The long-early case covered all declared visible concepts; **middle/late provider quality is not established**. Offline visibility analysis still shows ceilings of 0 for long-late and 1/3 for early+late; this is input evidence, not a live omission finding. No post-run ground truth, prompt, model or prefix tuning was applied.

Final offline validation: `./gradlew test`, `./gradlew clean build` pass; final clean build **408 API tests**, **41 new benchmark tests**, zero failures/errors/skipped. `./gradlew metadataEval` completes 20 fake cases and writes deterministic reports (rerun after clean); `npm test` **97 pass**, `npm run lint`/`npm run build` pass; `git diff --check` pass. **Zero external Gemini calls in all normal validation**, live input synthetic-only. No Web source change/browser QA, migration, dependency upgrade or application.yml edit. Feature implementation tests remain intact.

Tooling/offline evaluation are complete; the full synthetic live baseline is **still pending**, real/private notes unevaluated. **Recommended next direction: safely diagnose the failed provider/quota/output boundary, then obtain separate approval to establish a complete baseline with the same frozen corpus/contract.** Do not tune quality or enable background metadata from this incomplete run. Background/automatic generation and prompt/input-strategy tuning remain future, separately reviewed work.
