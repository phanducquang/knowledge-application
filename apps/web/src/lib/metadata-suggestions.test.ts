import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { MetadataError, MetadataSession, addSuggestedTags, fetchMetadataSuggestion, flushMetadataDraft, mapMetadataSuggestion } from "./metadata-suggestions.ts";
import { MetadataRequestError, readMetadataId } from "./metadata-request.ts";

const suggestion = { summary: "A concise technical summary", tags: ["Docker", "PostgreSQL"] };
test("explicit-only generation: mount/typing/autosave/apply never generate; flush precedes one Suggest", async () => {
  const actions: string[] = [];
  const session = new MetadataSession(async () => { actions.push("generate"); return suggestion; });
  assert.equal(actions.length, 0);
  const draft = { title: "typing", summary: "", tags: [] as string[] }; draft.title = "typed";
  actions.push("autosave"); assert.equal(actions.includes("generate"), false);
  await session.submit(async () => { actions.push("flush"); return JSON.stringify(draft); });
  assert.deepEqual(actions, ["autosave", "flush", "generate"]); assert.equal(draft.summary, ""); assert.deepEqual(draft.tags, []);
  draft.summary = session.getSnapshot().result!.summary; draft.tags = addSuggestedTags(draft.tags, suggestion.tags);
  assert.equal(actions.filter(a => a === "generate").length, 1); session.dismiss(); assert.equal(session.getSnapshot().result, null);
});
test("loading/duplicate suppression even while flushing, success and stale draft marker", async () => {
  let release!: (value: string) => void; let calls = 0;
  const session = new MetadataSession(async () => { calls++; return suggestion; });
  const active = session.submit(() => new Promise(resolve => { release = resolve; }));
  assert.equal(session.getSnapshot().busy, true); await session.submit(async () => "ignored"); assert.equal(calls, 0);
  release("saved-version"); await active; assert.equal(calls, 1); assert.equal(session.getSnapshot().busy, false);
  assert.notEqual(session.getSnapshot().sourceDraft, "edited-after-request");
});
test("dismiss invalidates obsolete response and prevents overlapping provider request until it settles", async () => {
  let release!: (value: typeof suggestion) => void; let calls = 0;
  const session = new MetadataSession(() => { calls++; return new Promise(resolve => { release = resolve; }); });
  const first = session.submit(async () => "first"); await Promise.resolve(); session.dismiss();
  await session.submit(async () => "second"); assert.equal(calls, 1); release(suggestion); await first;
  assert.equal(session.getSnapshot().result, null); assert.equal(session.getSnapshot().busy, false);
});
test("failed save has zero generation and safe error; disabled/unavailable never auto retry", async () => {
  let calls = 0; const session = new MetadataSession(async () => { calls++; throw new MetadataError("AI_METADATA_DISABLED"); });
  await session.submit(async () => { throw new MetadataError("SAVE_REQUIRED"); }); assert.equal(calls, 0);
  assert.equal(session.getSnapshot().error, "SAVE_REQUIRED"); await session.submit(async () => "saved"); assert.equal(calls, 1);
  assert.equal(session.getSnapshot().error, "AI_METADATA_DISABLED"); await Promise.resolve(); assert.equal(calls, 1);
});
test("individual/all tags are additive, case deduped and capped at persistence maximum", () => {
  assert.deepEqual(addSuggestedTags(["docker"], ["Docker", "PostgreSQL"]), ["docker", "PostgreSQL"]);
  assert.equal(addSuggestedTags(Array.from({ length: 19 }, (_, i) => `t${i}`), suggestion.tags).length, 20);
});
test("serial autosave flush waits in-flight draft then persists newest coalesced draft", async () => {
  const actions: string[] = []; let dirty = true; let inFlight: Promise<{ ok: boolean }> | null = Promise.resolve({ ok: true });
  const key = await flushMetadataDraft({ pending: () => inFlight, blocked: () => false, dirty: () => dirty, saving: () => false,
    failed: () => false, key: () => "newest saved draft", flush: async () => { actions.push("flush"); inFlight = null; dirty = false; } });
  assert.equal(key, "newest saved draft"); assert.deepEqual(actions, ["flush"]);
  await assert.rejects(flushMetadataDraft({ pending: () => Promise.resolve({ ok: false }), blocked: () => false,
    dirty: () => true, saving: () => false, failed: () => true, key: () => "", flush: async () => assert.fail("must not save again") }), MetadataError);
});
test("focused browser POST transports only id, no backend URL/content/owner and no retry", async () => {
  let calls = 0;
  assert.deepEqual(await fetchMetadataSuggestion(42, undefined, async (url, init) => {
    calls++; assert.equal(url, "/api/knowledge-metadata-suggestions"); assert.equal(init?.method, "POST"); assert.equal(init?.cache, "no-store");
    assert.equal(init?.credentials, "same-origin"); assert.deepEqual(JSON.parse(String(init?.body)), { id: 42 });
    return new Response(JSON.stringify(suggestion));
  }), suggestion); assert.equal(calls, 1);
  await assert.rejects(fetchMetadataSuggestion(42, undefined, async () => new Response('{"code":"UNKNOWN","message":"private payload"}', { status: 503 })),
    e => e instanceof MetadataError && e.code === "AI_METADATA_UNAVAILABLE" && !e.message.includes("private"));
});
test("response map drops provider fields and validates all bounds/types", () => {
  assert.deepEqual(mapMetadataSuggestion({ ...suggestion, ownerId: "secret", prompt: "private" }), suggestion);
  for (const value of [null, {}, { summary: " ", tags: [] }, { summary: "x".repeat(501), tags: [] }, { summary: "x", tags: [null] },
    { summary: "x", tags: ["x".repeat(51)] }, { summary: "x", tags: Array(6).fill("Tag") }]) assert.throws(() => mapMetadataSuggestion(value), MetadataError);
});
test("BFF rejects cross-origin, huge/unknown/request-owned content and invalid ids", async () => {
  const req = (body: unknown, extra = {}) => new Request("http://localhost:3000/api/knowledge-metadata-suggestions", { method: "POST",
    headers: { "Content-Type": "application/json", Origin: "http://localhost:3000", ...extra }, body: JSON.stringify(body) });
  assert.equal(await readMetadataId(req({ id: 1 })), 1);
  for (const body of [{ id: 0 }, { id: "1" }, { id: 1, ownerId: "foreign" }, { id: 1, content: "private" }, { id: "x".repeat(600) }])
    await assert.rejects(readMetadataId(req(body)), MetadataRequestError);
  await assert.rejects(readMetadataId(req({ id: 1 }, { Origin: "https://evil.example" })), e => e instanceof MetadataRequestError && e.status === 403);
});
test("editor uses existing serialized save/apply flow, safe text and private focused transport", () => {
  const source = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");
  const editor = source("../components/knowledge/knowledge-editor.tsx"); assert.match(editor, /flushMetadataDraft/); assert.match(editor, /flushSaveRef.current/);
  assert.match(editor, /applySummary=.*changeDraft/); assert.match(editor, /applyTags=\{changeTags\}/);
  const ui = source("../components/knowledge/metadata-suggestions.tsx");
  for (const label of ["Apply summary", "Apply all tags", "Dismiss suggestions", "Saving & suggesting", "The note changed"]) assert.ok(ui.includes(label));
  assert.match(ui, /disabled=\{!id \|\| state.busy\}/); assert.doesNotMatch(ui, /dangerouslySetInnerHTML|setTimeout|localStorage|GEMINI_API_KEY/);
  const api = source("api/metadata-suggestions.ts"); assert.match(api, /server-only/); assert.match(api, /backendRequest/); assert.match(api, /method: "POST"/);
  assert.match(source("../app/api/knowledge-metadata-suggestions/route.ts"), /private, no-store/);
});
