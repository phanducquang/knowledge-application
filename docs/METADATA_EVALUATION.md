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
- Usage: current-invocation attempts, actual adapter generation attempts, quota reservations, fake calls and conservative estimated input tokens including serialized JSON/system/schema overhead. Reused cases and historical estimated tokens are separate; neither is charged to new usage. Valid structured-output rate and duration/latencies describe this invocation, not a composite run. Not actual provider billing/token usage. Failures stop immediately; partial cases are reviewable but have **no aggregate quality baseline**.

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

Safety defaults/hard maxima: `METADATA_EVAL_LIVE_MAX_REQUESTS=20`, `METADATA_EVAL_LIVE_MAX_ESTIMATED_INPUT_TOKENS=150000`, `METADATA_EVAL_LIVE_MAX_WAIT_SECONDS=180` (max 600). Request/token/per-request-minute/day preflight runs before constructing the adapter or making calls, counting only outstanding cases on resume. No silent budget expansion. One note = one generation request, zero embeddings. Existing `AI_METADATA_MODEL`, content/output budgets, timeouts and metadata quota values are respected; quota protection must be enabled.

Own disposable PostgreSQL Testcontainer contains **only the existing V9 quota table**, not Knowledge/application tables. Actual `AiQuotaLimiter` persists independent metadata minute/day reservations there; exact token accounting is checked after the run. Production counters are neither read nor reset; project/model quotas are still real and shared externally. Any 429/timeout/5xx/invalid output stops without retry, prompt tuning or an optimization loop. Do not rerun after substantive provider usage without new approval.

### Rolling RPM pacing and safe diagnostics

Fixed wall-clock minute counters alone can allow a burst across a minute boundary. Evaluation now adds monotonic minimum spacing: `effectiveRpm = min(METADATA_EVAL_LIVE_MAX_RPM, metadataQuota.requestsPerMinute)` and `intervalMs = ceil(60000 / effectiveRpm) + METADATA_EVAL_LIVE_PACING_SAFETY_MS`. Evaluation max RPM defaults to **10** (1–60000), safety margin to **250ms** (1–10000); default spacing is **6250ms** if the configured metadata quota permits 10 RPM. These are evaluation controls, not changes to production quota defaults or hardcoded Google model limits.

Read-only DB quota precheck/wait happens first, then monotonic spacing, then the adapter's existing atomic quota reservation immediately before its one native SDK request. The attempt timestamp is marked again after reservation, so a slow reservation cannot shorten the next actual request interval. No sleep after reservation and no provider retries. Both waits share one cumulative monotonic wait budget, with interruptible sleep chunks ≤30s; exhaustion/interruption stops safely. Fake-clock tests cross minute boundaries and check rolling 60-second windows, long latency and slow reservations without real waits.

Safe allowlisted failure categories: `LOCAL_QUOTA`, `PACING_BUDGET`, `PROVIDER_RATE_LIMIT`, `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`, `INVALID_STRUCTURED_OUTPUT`, `OUTPUT_VALIDATION`, `CONFIGURATION`, `SAFETY_BUDGET`, `UNKNOWN_SAFE`. Reports contain category, case ID, current-invocation attempted-case number and optional HTTP status only. Typed SDK HTTP 429 or typed `RESOURCE_EXHAUSTED` maps to rate limit; HTTP 408/504 and typed transport timeout causes map to timeout. Invalid JSON/schema/candidates map to structured output; parsed output violating local limits maps to output validation. Arbitrary exception messages are not pattern-matched, retained or printed. Public API error contracts remain unchanged.

The previous incident's user dashboard showed **16/15 RPM**, with TPM/RPD below their displayed limits: strong RPM/burst evidence, not proof of the failed request's exact cause or a permanent provider policy. External project/concurrent usage, provider timing and independently shared quotas remain outside this isolated evaluator's coordination. Pacing reduces burst risk; it cannot guarantee no provider quota failure.

### Explicit resume and report preservation

`METADATA_EVAL_RESUME_FROM=<explicit-report-path>` opts into resume; no implicit file discovery. Loader supports **metadata-report-v1 and v2**, bounded/strict JSON, rejects duplicate/unknown case IDs, and checks corpus version/fingerprint, provider/model/SDK, prompt/schema versions/fingerprints, input strategy/content limit, summary/tag limits and output budget before calls. Case fixture metadata must match; v2 also checks the prepared-input fingerprint. Only stored `VALID` suggestions that still pass current normalization/validation can be reused. FAILED, missing, malformed or invalid suggestions remain outstanding. Stored metrics are untrusted and always recomputed using current deterministic metrics; metrics-version changes do not force regeneration. Reports are local unsigned artifacts, not cryptographic proof that a provider generated an output.

Reuse skips pacing, adapter calls and quota reservation entirely. Request/token budgets and current usage count only newly outstanding/attempted cases; corpus totals and estimated historical reuse costs remain separate. Provenance is `REUSED_FROM_PRIOR_RUN`, `GENERATED_THIS_RUN`, `FAILED_THIS_RUN` or `UNATTEMPTED`, with source report fingerprint/original generation timestamp/run ID for reused outputs. All corpus cases remain represented after failure, including later cached cases. Incomplete runs have no aggregates; a fully populated resumed report is **COMPLETE but composite**, never misrepresented as one uninterrupted generation run. Human review preserves provenance and blank ratings.

New v2 live reports use `build/reports/metadata-eval/live-runs/<runId>/live-report.json`, `live-report.txt` and `live-review.md`, created without overwrite. Before-result failures emit a sanitized marker in a fresh run directory. The original v1 files at `build/reports/metadata-eval/live-*` remain readable and untouched. Gradle `clean` preserves those live files and `live-runs/**` byte-for-byte while removing ordinary compiled/build artifacts. All reports remain gitignored; keep an additional external backup for long-term baselines. No raw provider bodies/application logs, workflow changes or CI secrets.

Read-only compatibility/count check (no key/config/provider/DB, no report writes):

```bash
METADATA_EVAL_RESUME_FROM=build/reports/metadata-eval/live-report.json ./gradlew metadataEvalResumeCheck
```

The original v1 report was checked offline: **17 reusable / 3 outstanding**. Fake resume and isolated quota tests established exactly three new calls/reservations and zero additional cost for those 17 cases. Following separate approval, the execution milestone used this command **once** to complete the baseline (not during the reliability milestone):

```bash
METADATA_EVAL_LIVE=true \
METADATA_EVAL_RESUME_FROM=build/reports/metadata-eval/live-report.json \
METADATA_EVAL_LIVE_MAX_REQUESTS=3 \
METADATA_EVAL_LIVE_MAX_RPM=10 \
METADATA_EVAL_LIVE_PACING_SAFETY_MS=250 \
./gradlew metadataEvalLive
```

That one-run authorization is now consumed; do not rerun the original incomplete report. Any future live work requires new approval and must explicitly select its report/configuration. The completed v2 report is under the run directory recorded below. Normal tasks never invoke live/resume live.

## Future model/contract comparison

1. Back up an approved baseline JSON/review outside `build` for long-term retention; new live paths and `clean` preserve previous local metadata live reports, but generated outputs are not committed.
2. Separately approve a candidate run using `AI_METADATA_MODEL=<compatible-candidate>` with the same opt-in task and corpus. Do not change production defaults just to evaluate it.
3. Compare report identity first: same corpus version/fingerprint and metrics/report schema; record model, prompt/schema versions/fingerprints, input strategy/content limit and output budget differences explicitly. For a model-only comparison keep other controls unchanged. Corpus changes need a new version, not silent baseline replacement.
4. Compare P/R/F1, generic/forbidden diagnostics, full/visible concept coverage, case-level omissions, language/category/position groups, latency and usage. Package-private `MetadataEvaluation.compare` demonstrates guarded separate deltas in offline tests; JSON is the reusable operator contract, no extra comparison dashboard/task.
5. Review human rubric before approving/rejecting a production model/strategy change. This milestone measures one baseline, not a calibrated threshold or real/private-corpus quality claim.

## Baseline and validation evidence

### Original incomplete attempt (historical)

One approved run on **2026-10-04** used **gemini-3.5-flash-lite**, SDK **1.75.0**, unchanged prompt/schema/prefix/output budget. **INCOMPLETE**: 18 conservative attempted requests, 17 validated outputs, 39071 estimated input tokens attempted (66027 planned for all 20). The run stopped at `long-middle` without retry; both after-32k cases were never attempted. Sanitized [identity/usage attempt record](metadata-evaluation-attempt.json) is intentionally committed; generated reports/outputs remain ignored. This is **not a completed full-corpus quality baseline** and cannot be compared as one.

Generation/pacing duration **33.874s**, Gradle task **44s** including setup; attempted request latency mean/p50/p95 **1194/1157/1758ms**. Valid-output rate **17/18 = 94.44%**, not a claim that the failing response was specifically malformed (the production boundary sanitizes quota/HTTP/parse failures). We cannot identify 429 versus another provider/output failure from this sanitized exception. Do not infer a root cause or rerun to fill the gap without new live approval: substantial quota was already consumed.

Exploratory observations on **only the 17 completed cases**, **not** full-corpus aggregate metrics/baseline: macro tag precision **.4873**, recall **.8235**, F1 **.6104**; generic-tag rate **0**, declared forbidden tags/claims **0**; concept coverage/visible coverage **.9706**. Full-note and visible coverage coincide in this subset because it contains no invisible ground truth. Do not interpret zero diagnostic flags as zero hallucination. These figures must not become CI thresholds or a candidate-model comparison baseline.

Case review highlights (human ratings remain blank): OIDC tags missed declared CSRF/session concepts; several technically plausible extra tags lowered closed-ground-truth precision. Nginx expresses “time between read operations”, but that phrase is outside the frozen aliases, so its .5 concept score is a known false negative. The Next.js mixed-language fixture received an English summary; other inspected Vietnamese/mixed summaries retained Vietnamese. The long-early case covered all declared visible concepts; **middle/late provider quality is not established**. Offline visibility analysis still shows ceilings of 0 for long-late and 1/3 for early+late; this is input evidence, not a live omission finding. No post-run ground truth, prompt, model or prefix tuning was applied.

Original quality-evaluation milestone validation: `./gradlew test`, `./gradlew clean build` passed with **408 API tests**, **41 new benchmark tests**, zero failures/errors/skipped. `./gradlew metadataEval` completed 20 fake cases; `npm test` **97 passed**, lint/build and `git diff --check` passed. Its separately approved live attempt is described above, not part of normal validation.

Reliability milestone validation: `./gradlew test`, `./gradlew metadataEval`, `./gradlew clean build` passed; final clean build **473 API tests**, **65 additional test cases**, zero failures/errors/skipped. Coverage includes rolling/minute-boundary pacing, cumulative wait/interruption, typed SDK failures/no retry, resume identity/output rejection, deterministic rescoring/provenance, exactly three fake calls for 17 reused cases, actual isolated PostgreSQL reservations and incomplete accounting diagnostics. Web **97/97 tests**, lint/build and `git diff --check` passed. Read-only v1 resume compatibility confirmed 17 reusable/3 outstanding. Protected-file SHA checks confirmed original live reports survive `clean` byte-for-byte, frozen corpus unchanged and local `application.yml` untouched. No Web source/browser QA, migration or dependency change.

Reliability tooling includes rolling pacing, typed safe diagnostics and explicit resume. **ZERO external Gemini calls and NO live resume occurred in the reliability implementation milestone.** Its separate execution milestone is recorded below.

### Completed synthetic baseline — one approved resume

On **2026-10-04**, one separately approved bounded resume accepted the original v1 report and completed **20/20 VALID**, **COMPLETE**, **composite/resumed**. Model **gemini-3.5-flash-lite**, SDK **1.75.0**, corpus **metadata-eval-v1**, prompt **metadata-prompt-v1**, schema **metadata-schema-v1**, input **prefix-32000-v1**, output budget **600**; all generation identity/fingerprints remain unchanged. Report **metadata-report-v2**, metrics **metadata-metrics-v1**. Run ID: `53844290c9090428fd06a7d36b76b2ad1196409a79f9a95f3c0327510f0ee6a9`, timestamp `2026-10-04T14:03:56.997626Z`.

Ignored local JSON/text/human-review files are in `apps/api/build/reports/metadata-eval/live-runs/<runId>/`. The original three live files were not overwritten. **17 reused suggestions are byte-equivalent as serialized suggestion values to the original valid outputs**; only `long-middle`, `long-late`, `long-early-late` were generated. Exactly **3 new SDK generation attempts, 3 persisted quota reservations, 40440 estimated new input tokens**, no failures/retries/second invocation. Effective pacing **10 RPM**, spacing **6250ms** (250ms margin), isolated quota-only PostgreSQL Testcontainer, no real/private Knowledge or application database.

New structured-output validity **3/3 = 100%**; final valid case count 20 includes historical reuse, not 20 new requests. Current generation/pacing duration **13.946s**, Gradle task **23s**. New-case latencies: middle **2304.88ms**, late **1377.63ms**, early+late **1351.64ms**; mean/p50/p95 **1678.05/1377.63/2304.88ms**, not all-20 historical latency. Recorded historical attempts **18 + 3 = 21**, including the original failed attempt; estimated historical attempted tokens **39071 + 40440 = 79511**. Reused valid-output historical cost **25587** plus new **40440** equals **66027** for the 20 valid cases. These estimates/recorded attempts are not actual billing or provider token usage; historical costs do not reserve new quota.

Full-corpus deterministic macro metrics (not human quality scores):

| Metric | Completed composite result |
| --- | ---: |
| Tag precision / recall / F1 | .446667 / .750000 / .558095 |
| Visible tag precision / recall / F1 | .470175 / .824561 / .593484 |
| Generic-tag rate / forbidden-tag count | 0 / 0 |
| Required/full-note concept coverage | .891667 |
| Visible concept coverage | .973684 |
| Mean visible required-concept fraction | .916667 |
| Forbidden-claim count | 0 |

Visible metrics exclude N/A sets (19 applicable cases); full-note metrics include all 20. Means are per-case macro values, not pooled concept counts. Zero diagnostics are not proof of no hallucinations or generic tags: the frozen generic-tag list does not flag labels such as `lab`, `checklist`, `background context`. Mean summary length **226.05** UTF-16 units, p50/p95 **218/284**; mean returned tags **4.6**.

All four long cases are VALID with a 32000-unit visible prefix:

| Case | Provenance | Full / visible concept coverage | Tag recall | Observation |
| --- | --- | --- | --- | --- |
| long-early | Reused | 1 / 1 | 1 | Visible pool cap, waiting and queue facts covered. |
| long-middle | New | 1 / 1 | 2/3 | Middle PostgreSQL index/owner/EXPLAIN facts are inside the prefix and recovered; `database indexing` does not match frozen Composite Index aliases. |
| long-late | New | 0 / N/A | 0 | All declared Kafka/delivery/idempotency facts are after 32k; output summarizes visible lab background. This is input exclusion, not failure to recover supplied Kafka facts. |
| long-early-late | New | 1/3 / 1 | 1/3 (visible 1) | Visible response timeout 3 seconds recovered; Circuit Breaker/Resilience4j facts are outside the prefix. |

Language observations: English fixtures received English summaries; both Vietnamese fixtures and six of seven mixed-technical fixtures received Vietnamese prose with technical terms. The mixed Next.js fixture received English, a language-fit observation, not a prompt change. Newly generated middle/early+late outputs retained Vietnamese; late remained English. Language-group concept coverage en/vi/mixed is **.863636/1/.904762**, based on phrase matching, not automated language correctness or representative population estimates.

**Benchmark-calibration candidates, not score overrides:** Nginx's supported “time between read operations” phrase is outside frozen concept aliases (.5 coverage); `Timeouts` is not the accepted `Timeout` tag. `Database Index`/`database indexing` versus Composite Index in postgres-index/long-middle deserves a taxonomy-granularity review, but broader terms must not automatically become equivalent. Useful unlisted operational tags (e.g. Consumer Offsets, queue depth, pendingAcquireTimeout) can reduce closed-list precision. Conversely, omitted CSRF/Session Cookie, Cache Invalidation, CI and Attachment Lifecycle tags may be genuine specificity/coverage gaps; do not relabel every miss as a benchmark defect. No aliases/expected tags/forbidden lists or metrics were edited after observing results.

**Human review still pending**: all rubric ratings remain blank. Synthetic repeated appendices are position probes, not representative real/private notes. The report combines two invocations, not one uninterrupted run; prefix omission and closed ground truth constrain interpretation. No production tuning, source/Gradle changes, model/prompt/schema/input-strategy/quota changes, background generation, migration or other AI calls. Execution validation used read-only `metadataEvalResumeCheck`, the single approved live command, provenance/usage/protected-file checks and `git diff --check`; full test/build suites were not rerun for documentation-only edits. Original reports, frozen corpus and local secret-bearing `application.yml` remain unchanged/uncommitted.

**Recommended next direction:** review the blank human rubric and separately scope/version benchmark taxonomy/alias calibration; preserve this frozen complete composite baseline. Real/private notes remain unevaluated. No further live run, quality tuning or automatic metadata generation is authorized by this execution milestone.

## Metadata benchmark calibration v2 — offline only

The v1 completed composite baseline above is **frozen historical evidence**. Its `corpus.json`, generation outputs, aliases/phrases/forbidden lists and fingerprint `9f64eb96cf6b55b4ef62261ae755dff4a7b9895c5dec36c5bc8a1e4124b7d1db` remain unchanged. A regression test pins that fingerprint. Calibration is a new evaluation, not a new generation: **the same 20 suggestions** from the existing complete report were re-scored offline, reproducing v1 metrics before applying v2.

`apps/api/src/test/resources/metadata-eval/calibration-v2.json` is a versioned overlay, not a duplicate set of note contents or an in-place v1 edit. It materializes **metadata-eval-v2**, preserving all note inputs/current tags, canonical required tags, full/visible evidence, late concepts and forbidden diagnostics. V2 corpus fingerprint: `51006485302757badca90f9d3c0f2c0b8f5ea594c78d3a219833ee4ee26c997a`. Evaluation fingerprint: `236b0367d1312f256463871682978accbd077c78d3461fb0856ecc377df2271f`, covering the materialized corpus, decision/rule data and explicitly versioned scorer semantics. Metrics are **metadata-metrics-v2**; comparison/review schema **metadata-report-v3**. Generation identity remains v1 / gemini-3.5-flash-lite / SDK 1.75.0 / unchanged prompt, schema, prefix-32000 and output limits. Generation and evaluation identities are separate fields, never relabeled as v2 generation.

### Methodology and decision audit

All 20 outputs were inspected against authored fixture content and the deterministic synthetic appendices. **69 individually identified decisions** include type, fixture/target/value, evidence, rationale and confidence. They are review assistance, not human quality ratings. Every case-specific rule must reference evidence present in the fixture; duplicate/ambiguous tiers and strict/tier overlap are rejected. Global genericness rules are explicitly evaluation policy, not factual assertions about every note.

- **4 alias additions:** explicit `Timeouts` plural for netty-pool, nginx and long-early-late (webclient already had it), plus conventional `Cross-Site Request Forgery` expansion for OIDC's CSRF. These are semantic equivalents, not arbitrary strings accepted because a model generated them. Existing abbreviation/product aliases remain explicit; no stemming/fuzzy matching.
- **2 alias removals:** `Indexing` is removed from Composite Index in postgres-index and long-middle. Indexing is broader than a multi-column index. Neither `Indexing`, `Database Index` nor `database indexing` becomes exact credit; tests demonstrate that v2 can lower scores for the removed broad alias even though these frozen outputs did not use it.
- **2 concept phrase additions:** nginx read-gap accepts `between read operations` and `gaps between read operations`, independently equivalent to the fixture's gaps-between-reads decision. No loose standalone words, embeddings, entailment judge or LLM scoring.
- **9 generic-list additions, no removals:** `lab`, `lab notes`, `lab notebook`, `checklist`, `background context`, `synthetic operations`, `Configuration`, `Performance`, `Security`. These identify nondiscriminative lab scaffolding or coarse cross-cutting labels for this small technical corpus, not a universal blacklist or production normalization rule. Genericness is orthogonal to topical relevance.
- **52 source-backed tier rules:** broad labels (Database Index, Authentication/Security/Session Management, Caching, Networking/Java in the timeout fixture, etc.) do not replace required specifics; additional labels (Consumer Offsets, Startup Probe, Management Endpoints, Flyway/Schema validation, named CI tools, queue depth/pendingAcquireTimeout, etc.) may be useful without being required recall targets. Context matters: Java is a broader code-language label for WebClient, but an explicit additional runtime label for the CI fixture. Redis's Performance label is conservatively unsupported/unreviewed because that fixture specifies expiry/invalidation/staleness, not performance evidence; human judgment may disagree.

### Strict metrics versus separate review diagnostics

Five tiers are **EXACT_CANONICAL**, **ACCEPTED_EQUIVALENT**, **RELEVANT_BUT_BROADER**, **RELEVANT_ADDITIONAL**, **IRRELEVANT_UNSUPPORTED**. Strict precision/recall/F1 keep v1's unique required-concept credit over returned tag count/expected **new** tags; only canonical or explicit equivalents count. Multiple equivalent labels cannot inflate recovery. Existing tags are removed by unchanged production normalization before frozen-output validation, and existing canonical/alias concepts remain excluded from the required new-tag denominator; production does not gain semantic alias removal.

Separate macro diagnostics count label-level source relevance, broader, additional and unsupported/unreviewed rates; canonical/equivalent/broader/additional all count as source-related there. **RelevantTagRate is NOT accuracy, strict precision, a usefulness score or human approval**: generic/background tags may be related to source yet poor navigation labels. Unknown labels default unsupported/unreviewed, not proven irrelevant. This bounded case-specific taxonomy is incomplete, calibrated in-sample and not a general semantic tag classifier.

Review-assistance findings distinguish alias/phrase calibration, visible required-tag misses, broader/additional labels, unsupported/unreviewed labels, input-limit exclusions and human-review needs. Late-only evidence does not produce a model-miss/under-specific diagnosis. An ASCII-only summary on a Vietnamese/mixed fixture is merely `LANGUAGE_MISMATCH_CANDIDATE`; it is not language detection, an automatic correctness verdict or a prompt change. The known mixed Next.js output remains English, requiring human language-fit judgment; other observed English/Vietnamese behavior is unchanged.

### Re-score invocation and input safety

```bash
cd apps/api
METADATA_EVAL_BASELINE_REPORT=build/reports/metadata-eval/live-runs/53844290c9090428fd06a7d36b76b2ad1196409a79f9a95f3c0327510f0ee6a9/live-report.json \
./gradlew metadataEvalCalibrate
```

An **explicit** completed local synthetic report is mandatory; no discovery, reconstruction, live fallback or regeneration. Bounded strict JSON and existing generation identity/fixture/input/output validation require 20 current valid suggestions and COMPLETE status. Stored metrics are untrusted and recomputed. Missing/incompatible/duplicate/invalid inputs fail safely with no raw exception/cause/path/credential dump. Source report is never overwritten. The task never reads application.yml, constructs a Gemini adapter/Spring context/database or reserves quota, even with enabled ambient AI flags/keys; subprocess loopback tests exercise that boundary. Normal tests use generated fake reports, not committed live outputs.

Ignored deterministic outputs: `build/reports/metadata-eval/calibration-v2.json`, `calibration-v2.txt`, `calibration-review.md`. Each case contains unchanged summary/tags, v1/v2 expected aliases/concepts/matches/coverage, separate broader/additional/unreviewed tiers, decisions/reasons, metric deltas and blank HUMAN_ONLY rubric. The source-output fingerprint is `f9da02bbb9399bad8d331c060edadf2c828ef5ea847b8e00bce7463cbc549821`; it ties both scorer views to the identical suggestions. Repeated runs are deterministic; generated reports remain uncommitted.

### Observed same-output v1/v2 comparison

| Macro metric | Frozen v1 | Calibrated v2 | Benchmark delta |
| --- | ---: | ---: | ---: |
| Strict tag precision | .446667 | .456667 | +.010000 |
| Strict tag recall | .750000 | .766667 | +.016667 |
| Strict tag F1 | .558095 | .570595 | +.012500 |
| Visible strict precision | .470175 | .480702 | +.010526 |
| Visible strict recall | .824561 | .842105 | +.017544 |
| Visible strict F1 | .593484 | .606642 | +.013158 |
| Full-note concept coverage | .891667 | .916667 | +.025000 |
| Visible concept coverage | .973684 | 1 | +.026316 |
| Generic-tag rate | 0 | .137500 | +.137500 |
| Forbidden tags / claims | 0 / 0 | 0 / 0 | unchanged |

**Every delta is benchmark calibration, NOT model improvement.** Strict/coverage changes occur only in nginx: plural-equivalent Timeout recognition moves P/R/F1 .4/.666667/.5 → .6/1/.75; explicit read-gap phrase moves full/visible coverage .5 → 1. Alias removal and the other additions do not change strict scores for these particular frozen outputs. Generic diagnostics change only netty-pool, redis-vi, nginx, oidc, minio, long-middle, long-late, long-early-late due to the nine explicit list additions; no model output changed.

Separate v2 diagnostics: source-related label rate **.990000**, broader rate **.311667**, additional-relevant rate **.221667**, unsupported/unreviewed rate **.010000**, all macro per-case rates. Across 92 returned labels the tiers are **38 canonical, 5 equivalent, 28 broader, 20 additional, 1 unsupported/unreviewed**; 12 generic-list matches. Pooled counts differ from macro rates. Strict misses in OIDC (CSRF/session), Redis (invalidation), CI, MinIO lifecycle and composite-index specificity remain, even when broad/additional labels are useful. Zero forbidden flags still do not prove zero hallucination; phrase matching is not factuality/entailment certification.

Long-note coverage is unchanged: early **1/1** full/visible, middle **1/1**, late **0/N/A**, early+late **1/3 / 1**. Late full-note concepts were not removed to improve scores. Broad/generic scaffolding diagnostics now flag all four late-only tags and two early+late tags, but cannot recover unseen Kafka/Circuit Breaker/Resilience4j facts or establish model quality on excluded input.

### Human worksheet and limitations

**Human rubric scoring remains PENDING USER REVIEW.** Generated review fields are null/blank for factuality, coverage, conciseness, usefulness, language fit, tag specificity/usefulness/consistency and human notes. Machine review-assistance classifications are stored separately and never converted into 1–5 scores.

Persistent blank [JSON worksheet](metadata-review-template-v2.json) contains all 20 case IDs under `metadata-human-review-v1`, corpusVersion metadata-eval-v2, generationCorpusVersion metadata-eval-v1. Copy it to a secure long-term local file **outside build and Git**, fill evaluation/output fingerprints from the report, and only a human may supply integers 1–5 or null and notes. Future import can key by corpusVersion/caseId plus those fingerprints; **no importer or UI is implemented**. Regenerated build worksheets are not durable storage for completed human ratings.

Remaining limitations: observed-output in-sample calibration, bounded/unexhaustive taxonomy and inherited alias assumptions, exact-list genericness, substring/negation false positives, heuristic language cues, synthetic repetitive appendices and unsigned local report provenance. Redis Performance, coarse security/authentication labels, broader index names and the mixed Next.js language choice still need human judgment. Real/private notes are not evaluated. This milestone makes **ZERO external Gemini calls** and changes no production source, model, prompt/schema/input strategy, quota/defaults, SDK, migrations, background behavior or Web source. Model comparison, prompt tuning and input-strategy tuning are **NOT STARTED**; background metadata remains FUTURE.

Recommended next milestone: user-supplied human rubric review, then separately scoped held-out benchmark review if needed; preserve both v1 and v2 histories. Do not start generation, model comparisons or production tuning automatically.

### Executed offline validation

`./gradlew test` and `./gradlew clean build` both passed; the final clean-build XML results contain **502 tests, zero failures/errors/skips**, including **29 calibration tests**. `./gradlew metadataEval metadataEvalCalibrate` passed against the explicit completed local report after clean: 20 fake offline cases and the same 20 frozen generated suggestions respectively, with zero external calls/quota reservations. The three regenerated calibration artifacts are byte-identical to their pre-clean versions. The v1 corpus, original/completed live reports and local `application.yml` remain byte-identical; live reports and generated calibration artifacts stay ignored/uncommitted.

Web validation also passed: `npm test` **97/97**, `npm run lint`, `npm run build`. `git diff --check` passed. Production and Web sources are unchanged. No `metadataEvalLive`, external Gemini request, CI monitoring or human rubric scoring was performed for this milestone.
