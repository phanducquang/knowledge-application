# AI metadata suggestions — Summary & Tags

Explicit owner-controlled authoring assistance, not automatic enrichment. Existing persisted notes only. Independently disabled by default, using the existing backend-only Gemini credential and SDK **1.75.0**. Retrieval, Ask and citations remain unchanged.

## Request and apply

`POST /api/knowledge/{id}/ai/metadata-suggestions` has no writable body. Authentication/CSRF and `CurrentOwner` are authoritative. Enabled mode uses an owner-scoped current note lookup: missing/cross-owner notes return opaque 404. Responses are `Cache-Control: private, no-store, max-age=0`.

```json
{ "summary": "Configure WebClient timeouts deliberately.", "tags": ["WebClient", "Timeout"] }
```

The provider-neutral `KnowledgeMetadataSuggestionClient` receives only title, nullable summary, bounded current Markdown, current tag names and a truncation flag. A short read-only transaction copies the snapshot; NOT_SUPPORTED orchestration suspends ambient transactions. No transaction/connection/row lock spans Gemini. No revisions, other notes, retrieval, owner IDs, share tokens, object keys or attachment binaries are looked up/sent.

The endpoint never writes Knowledge, tags or revisions. Next BFF `POST /api/knowledge-metadata-suggestions` accepts only `{id}`, limits streamed JSON to 512 bytes and checks Host/Origin/fetch-site. Existing server-only transport forwards session/CSRF; browser code receives no backend URL or credentials.

Edit awaits in-flight autosave and flushes the latest dirty draft through the existing serialized/coalescing save. A failure stops before AI; continuous edits may exhaust an eight-drain bound and require another explicit action. New notes require Create first. Opening, typing, autosaving, applying and dismissing make zero metadata generation calls.

The restrained Summary-adjacent section explains the external-provider boundary. **Apply summary**, individual tag **Add**, and **Apply all tags** change local state, then ordinary autosave/checkpoints persist. Tags are additive within the existing 20-tag limit; nothing is deleted automatically. Dismiss writes nothing. Changed drafts show an earlier-suggestion review warning. Duplicate submissions are blocked during save/generation. Abort/obsolete guards do not guarantee provider cancellation/refund. No multi-tab optimistic locking or suggestion history.

## Structured output and input bounds

- Native schema requires exactly `summary`/`tags`, no extra fields, one complete STOP candidate. Truncated generation is rejected.
- Summary: trimmed, non-blank, plain text, **500 UTF-16 units** maximum (persistence allows 2000). Instructions request concise factual text, primary language and preserved technical terms.
- Tags: at most **5 entries before normalization**, each non-null/non-blank and at most **50 UTF-16 units**. Reject control characters, collapse whitespace, strip leading `#`, deduplicate case-insensitively and remove existing tags. Empty new-tag lists are valid.
- Strict duplicate-key/trailing-token/type/count/length validation rejects the whole output. Service validation also protects non-Gemini implementations. UI renders escaped text, never raw HTML.
- CRUD content is currently unbounded. Send a deterministic Unicode-safe prefix of at most **32000 UTF-16 units**, configurable downward, plus bounded title/summary/tags; longer notes set `contentTruncated=true`. Later sections may be omitted. JSON escaping/prompt/schema overhead counts in estimated TPM. No section reranking, RAG or second pass.

The default model supports structured output and a large context according to [Google's model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite); the smaller application bound controls cost/privacy, not provider token capacity. [Structured output documentation](https://ai.google.dev/gemini-api/docs/structured-output). Review capabilities/tier limits when changing models.

JSON-schema support is a provider subset, not proof of semantic correctness or enforcement of every keyword. Length/content validation remains authoritative on the server regardless of provider behavior.

System instructions treat Knowledge as **untrusted reference data**, rejecting embedded commands/fake roles/task overrides. No tools, Search, URL context, code execution, function calls, downloads, multimodal input, conversation or agent loop. Markdown image/URL references may appear as ordinary text but are never fetched.

## Independent configuration and quotas

Defaults are in `metadata-suggestions.properties`, loaded by focused Gemini configuration, without changing the user's local secret-bearing `application.yml`. Normal `app.ai.metadata.*` overrides and environment aliases are supported. No new credential loader.

| Environment | Default |
|---|---|
| `AI_METADATA_ENABLED` | `false` |
| `AI_METADATA_MODEL` | `gemini-3.5-flash-lite` |
| `AI_METADATA_MAX_OUTPUT_TOKENS` | `600` |
| `AI_METADATA_MAX_CONTENT_CHARS` | `32000`, valid 1–32000 |
| `AI_METADATA_BASE_URL` | `https://generativelanguage.googleapis.com` |
| `AI_METADATA_CONNECT_TIMEOUT` / `AI_METADATA_READ_TIMEOUT` | `PT5S` / `PT30S` |
| `AI_METADATA_QUOTA_ENABLED` | `true` |
| `AI_METADATA_RPM_LIMIT` | `10` |
| `AI_METADATA_TPM_LIMIT` | `200000` estimated input tokens |
| `AI_METADATA_RPD_LIMIT` | `400` |
| `AI_METADATA_TOKEN_ESTIMATE_CHARS_PER_TOKEN` | `2.5` |
| `AI_METADATA_RPD_RESET_ZONE` | `America/Los_Angeles` |

Disabled mode requires no credential/provider and does not affect health. Enabled mode requires the existing valid Gemini configuration. One explicit successful Suggest = **one generation request, zero embedding requests**. Shared transport retains bounded timeouts, no redirects, SDK attempts=1 and no connection-failure retries.

Persistent `AiQuotaLimiter` uses independent `metadata-generation-minute`/`metadata-generation-day` and a separate advisory reservation key. RPM/estimated TPM/timezone RPD persist across replicas/reconstruction; failures count conservatively. Existing suffix retention works without migration/special cleanup. Defaults are application budgets, not Google policy; separate feature groups do not guarantee an upstream project-wide ceiling when they share model quotas.

Disabled: **503 `AI_METADATA_DISABLED`**. Local quota denial, 429, connectivity/timeout/non-2xx, invalid output: **503 `AI_METADATA_UNAVAILABLE`**. Stable sanitized messages, no provider bodies/causes or automatic retry. No raw prompt/output logging or persistence.

## Privacy, validation and limitations

Explicit requests send selected current private text/metadata to **Google Gemini**. This is not entirely local AI; review provider terms/tier data handling before enabling. Only user-applied metadata enters ordinary persistence. Keep local secrets/payload dumps/generated artifacts uncommitted.

Original feature tests used capturing fakes, SDK HTTP loopback fixtures and isolated PostgreSQL Testcontainers. Gradle disables metadata/Ask/embeddings and clears credentials in normal Test tasks. The original feature delivery and CI made no live Gemini calls; the later separately approved quality-evaluation attempt is recorded in [evaluation evidence](METADATA_EVALUATION.md), with an incomplete full-corpus baseline and no retry. Coverage includes configuration, validation, failures/no-retry, independent quotas/retention, owner/CSRF, no-write/no-transaction and explicit save/request/apply semantics. Browser tooling was unavailable; fixture Edit HTTP and focused BFF/no-write checks supplement tests but do not establish visual desktop/mobile interaction QA or browser-driven autosave persistence.

Generated metadata can be inaccurate, prefix truncation may omit key late sections, and user review remains required. No real-note quality claim, background enrichment, new-note draft system, suggestion history or public/unlisted AI. The [synthetic quality benchmark](METADATA_EVALUATION.md) measures summary concepts/new tags with visible/full-note diagnostics and an opt-in live baseline; real/private-corpus quality remains unevaluated.

Traceability identifiers are `metadata-prompt-v1`, `metadata-schema-v1`, and `prefix-32000-v1` (configured content limit is recorded). Pure preparation is shared between snapshot loading and evaluation; instructions/schema/defaults and production semantics are unchanged. Public contract fingerprints detect accidental changes independently of model configuration. Evaluation does not enable automatic tagging or summary replacement.

## Milestone verification

Final API `./gradlew test` and `./gradlew clean build` passed **367 tests**, including **36 new metadata tests**, with zero failures/errors/skipped. Web `npm test` passed **97 tests** (10 new), and `npm run lint`/`npm run build` passed. `git diff --check` passed. Existing owner/OIDC/CSRF/visibility/sharing/attachments/revisions/Ask/embedding/quota/retention tests remain intact, including health UP with AI disabled. No migration, dependency upgrade or retrieval/default changes.

Local synthetic HTTP fixture checks through actual Next transport: Edit 200; Suggest 200/private-no-store, exactly one fixture request and no Knowledge write/change; disabled/unavailable 503 with stable codes; cross-origin 403. Fixture servers were stopped afterward. **Zero external Gemini calls** throughout validation; no live generation/quality evaluation. Desktop/mobile interaction/visual QA remains unverified because no browser was available.
