import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { fetchSemanticKnowledge, mapSemanticApiResponse, resolveSearchMode, SemanticSearchError, SemanticSearchSession,
  semanticSearchHref, semanticSearchRequestUrl } from "./knowledge-semantic-search.ts";
import { SEARCH_DEBOUNCE_MS } from "./knowledge-search.ts";

const apiNote = { id: 12, title: "Proxy timeouts", slug: "proxy-timeouts", summary: null, visibility: "PRIVATE",
  collection: "Backend", tags: ["Java"], updatedAt: "2026-10-03T00:00:00Z", match: { chunkIndex: 2, text: "<script>source only</script>" } };
const note = mapSemanticApiResponse([apiNote])[0];
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

test("resolves old bookmarks, explicit modes, invalid modes and arrays safely", () => {
  assert.equal(resolveSearchMode(undefined), "keyword"); assert.equal(resolveSearchMode("keyword"), "keyword");
  assert.equal(resolveSearchMode("semantic"), "semantic"); assert.equal(resolveSearchMode("invalid"), "keyword");
  assert.equal(resolveSearchMode(["semantic", "keyword"]), "semantic");
  assert.equal(semanticSearchHref(" cache invalidation "), "/search?mode=semantic&q=cache+invalidation");
});

test("semantic browser URL is same-origin, trimmed, encoded and bounded", async () => {
  assert.equal(semanticSearchRequestUrl("  cache & expiry  ", 20), "/api/knowledge-semantic-search?q=cache+%26+expiry&limit=20");
  const controller = new AbortController();
  const results = await fetchSemanticKnowledge(" cache ", 20, controller.signal, async (input, init) => {
    assert.equal(String(input), "/api/knowledge-semantic-search?q=cache&limit=20");
    assert.equal(init?.signal, controller.signal); assert.equal(init?.cache, "no-store"); assert.equal(init?.method, "GET");
    return json([note]);
  });
  assert.deepEqual(results, [note]);
});

test("maps allowlisted normal metadata and current plain-text match without vector details", () => {
  const mapped = mapSemanticApiResponse([{ ...apiNote, embedding: [1, 0], distance: .2, ownerId: "other", apiKey: "secret" }]);
  assert.equal(mapped[0].href, "/knowledge/proxy-timeouts"); assert.equal(mapped[0].visibility, "Private");
  assert.equal(mapped[0].match.text, "<script>source only</script>");
  assert.ok(!JSON.stringify(mapped).includes("secret")); assert.equal("distance" in mapped[0], false);
});

test("rejects malformed backend response fields, unsafe slugs, duplicates and oversized excerpts", () => {
  for (const invalid of [null, {}, [{ ...apiNote, id: "12" }], [{ ...apiNote, slug: "javascript:bad" }],
    [{ ...apiNote, match: { chunkIndex: -1, text: "x" } }], [{ ...apiNote, match: { chunkIndex: 0, text: "x".repeat(601) } }],
    [{ ...apiNote, updatedAt: "bad" }], [apiNote, apiNote], [{ ...apiNote, tags: [1] }]]) {
    assert.throws(() => mapSemanticApiResponse(invalid), SemanticSearchError);
  }
});

test("validates browser response instead of accepting arbitrary JSON", async () => {
  for (const invalid of [{}, [{ ...note, match: null }], [{ ...note, href: "https://other.example" }], [{ ...note, updatedAtIso: "bad" }]])
    await assert.rejects(fetchSemanticKnowledge("q", 20, undefined, async () => json(invalid)), SemanticSearchError);
});

test("maps disabled/provider/auth errors without exposing upstream body", async () => {
  for (const [code, expected] of [["SEMANTIC_SEARCH_DISABLED", "SEMANTIC_SEARCH_DISABLED"], ["UNKNOWN_UPSTREAM", "SEMANTIC_SEARCH_UNAVAILABLE"],
    ["UNAUTHENTICATED", "UNAUTHENTICATED"], ["ACCESS_DENIED", "ACCESS_DENIED"]]) {
    await assert.rejects(fetchSemanticKnowledge("q", 20, undefined, async () => json({ code, message: "secret Authorization upstream" }, 503)),
      error => error instanceof SemanticSearchError && error.code === expected && !error.message.includes("secret"));
  }
});

test("forwards cancellation without silently retrying", async () => {
  const controller = new AbortController(); controller.abort(); let calls = 0;
  await assert.rejects(fetchSemanticKnowledge("q", 20, controller.signal, async (_input, init) => {
    calls++; assert.equal(init?.signal?.aborted, true); throw new DOMException("Aborted", "AbortError");
  }), error => error instanceof DOMException && error.name === "AbortError");
  assert.equal(calls, 1);
});

test("typing every semantic prefix and switching into semantic costs ZERO requests until submit", async () => {
  const queries: string[] = [];
  const session = new SemanticSearchSession({}, async query => { queries.push(query); return [note]; });
  session.draft("existing keyword"); session.clear(); // mode switch
  for (const value of ["r", "re", "red", "redi", "redis"]) session.draft(value);
  await new Promise(resolve => setTimeout(resolve, SEARCH_DEBOUNCE_MS + 20));
  assert.deepEqual(queries, []); assert.equal(session.getSnapshot().status, "idle");
  await session.submit(20);
  assert.deepEqual(queries, ["redis"]); assert.equal(session.getSnapshot().status, "success");
  session.draft("new unsubmitted query");
  assert.equal(session.getSnapshot().submittedQuery, "redis"); assert.deepEqual(queries, ["redis"]);
});

test("initial semantic deep-link state does not repeat server query on mount", () => {
  let calls = 0;
  const session = new SemanticSearchSession({ draft: "cache", submittedQuery: "cache", status: "success", results: [note] }, async () => { calls++; return []; });
  assert.equal(session.getSnapshot().status, "success"); assert.equal(calls, 0);
});

test("duplicate in-flight submit is suppressed; new query aborts old and ignores stale response", async () => {
  const pending: { resolve: (results: typeof note[]) => void; signal?: AbortSignal }[] = [];
  const session = new SemanticSearchSession({}, (_query, _limit, signal) => new Promise(resolve => pending.push({ resolve, signal })));
  session.draft("first"); const first = session.submit(20); await session.submit(20); assert.equal(pending.length, 1);
  session.draft("second"); const second = session.submit(20); assert.equal(pending.length, 2); assert.equal(pending[0].signal?.aborted, true);
  pending[1].resolve([note]); await second; pending[0].resolve([]); await first;
  assert.equal(session.getSnapshot().submittedQuery, "second"); assert.deepEqual(session.getSnapshot().results, [note]);
});

test("empty index, safe errors and manual retry have distinct state, no automatic retries", async () => {
  let calls = 0;
  const session = new SemanticSearchSession({}, async () => {
    calls++; if (calls === 1) throw new SemanticSearchError("SEMANTIC_SEARCH_DISABLED");
    if (calls === 2) throw new Error("secret"); return [];
  });
  session.draft("cache"); await session.submit(20);
  assert.equal(session.getSnapshot().error, "SEMANTIC_SEARCH_DISABLED"); assert.equal(calls, 1);
  await session.submit(20); assert.equal(session.getSnapshot().error, "SEMANTIC_SEARCH_UNAVAILABLE");
  await session.submit(20); assert.equal(session.getSnapshot().status, "success"); assert.deepEqual(session.getSnapshot().results, []);
  session.clear(); assert.equal(session.getSnapshot().status, "idle"); assert.equal(session.getSnapshot().draft, "cache");
});

test("mode/UI/server wiring preserves keyword debounce, explicit submit, private transport and text-only rendering", () => {
  const source = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");
  const client = source("../components/search/knowledge-search.tsx");
  assert.match(client, /if \(mode === "keyword"\) searchState.search\(nextQuery\)/);
  assert.match(client, /else semantic.session.draft\(nextQuery\)/);
  assert.match(client, /if \(mode === "semantic"\) submitSemantic\(\)/);
  assert.match(client, /aria-pressed=\{mode === value\}/); assert.match(client, /Semantic index is not ready yet/);
  assert.match(client, /semantic.submittedQuery/); assert.match(client, /semanticSearchHref\(query\)/);
  assert.match(client, /id="search-results-heading" className="min-w-0 flex-1[^\"]*overflow-wrap:anywhere/);
  const hook = source("../hooks/use-knowledge-search.ts"); assert.match(hook, /setTimeout/); assert.match(hook, /SEARCH_DEBOUNCE_MS/);
  assert.equal(SEARCH_DEBOUNCE_MS, 180);
  const quick = source("../components/search/quick-search-overlay.tsx"); assert.doesNotMatch(quick, /semantic/i);
  const page = source("../app/search/page.tsx"); assert.match(page, /await requireCurrentUser\(\)/);
  assert.match(page, /if \(initialMode === "semantic"\)/); assert.match(page, /else initialResults = await searchKnowledge/);
  const api = source("api/knowledge-semantic-search.ts"); assert.match(api, /import "server-only"/); assert.match(api, /backendRequest<unknown>/);
  const bff = source("../app/api/knowledge-semantic-search/route.ts"); assert.match(bff, /private, no-store/); assert.doesNotMatch(bff, /NEXT_PUBLIC|CSRF|KNOWLEDGE_API_BASE_URL/);
  const row = source("../components/knowledge/knowledge-list-item.tsx"); assert.match(row, /\{matchText\}/); assert.doesNotMatch(row, /dangerouslySetInnerHTML/);
});
