import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { AskAnswer } from "../components/ask/ask-answer.ts";
import { SourceLinkedAnswer, focusCitationEvidence } from "../components/ask/source-linked-answer.ts";
import { AskError, AskSession, fetchAskKnowledge, mapAskResponse, askSourceHref, type AskResult } from "./ask-knowledge.ts";
import { AskRequestError, readAskQuestion } from "./ask-request.ts";
import { buildBackendHeaders, isStateChangingMethod } from "./backend-auth.ts";

const result: AskResult = { status: "ANSWERED", answer: { blocks: [{ markdown: "Use `responseTimeout`.", citationIds: ["C1"] }] },
  citations: [{ id: "C1", source: { id: 1, title: "Timeouts", slug: "timeouts" }, chunkIndex: 2, evidence: "<script>literal note text</script>" }] };
const noContext: AskResult = { status: "NO_CONTEXT", answer: null, citations: [] };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
test("Ask POST is same-origin no-store, one question body, never a question URL", async () => {
  let calls = 0;
  const actual = await fetchAskKnowledge("  private & question\nsecond line  ", undefined, async (input, init) => {
    calls++; assert.equal(input, "/api/ask-my-knowledge"); assert.equal(init?.method, "POST"); assert.equal(init?.cache, "no-store");
    assert.equal(init?.credentials, "same-origin");
    assert.deepEqual(JSON.parse(String(init?.body)), { question: "private & question\nsecond line" }); return json(result);
  });
  assert.deepEqual(actual, result); assert.equal(calls, 1);
});
test("maps allowlisted answer/sources and drops vector, secret and provider fields", () => {
  const safe = mapAskResponse({ ...result, apiKey: "secret", context: "private", citations: [{ ...result.citations[0], source: { ...result.citations[0].source, ownerId: "other" }, distance: 0, embedding: [1], model: "internal" }] });
  assert.deepEqual(safe, result); assert.equal(askSourceHref(safe.citations[0].source), "/knowledge/timeouts");
  assert.deepEqual(mapAskResponse(noContext), noContext);
});
test("rejects invalid status/empty answer, source duplicates, unsafe navigation and response bounds", () => {
  for (const invalid of [null, {}, { ...result, status: "CHAT" }, { ...result, answer: "old string contract" },
    { ...result, answer: { blocks: [{ markdown: " ", citationIds: ["C1"] }] } },
    { ...result, answer: { blocks: [{ markdown: "x".repeat(8193), citationIds: ["C1"] }] } },
    { ...result, citations: [] }, { ...result, citations: [result.citations[0], result.citations[0]] }, { ...noContext, answer: "hallucination" },
    { ...result, citations: [{ ...result.citations[0], source: { ...result.citations[0].source, slug: "javascript:bad" } }] },
    { ...result, citations: [{ ...result.citations[0], evidence: "x".repeat(401) }] }])
    assert.throws(() => mapAskResponse(invalid), AskError);
});
test("disabled/retrieval/generation/auth errors are sanitized without retry", async () => {
  for (const code of ["ASK_DISABLED", "ASK_RETRIEVAL_UNAVAILABLE", "ASK_UNAVAILABLE", "UNAUTHENTICATED", "ACCESS_DENIED", "UNKNOWN_UPSTREAM"]) {
    let calls = 0;
    await assert.rejects(fetchAskKnowledge("q", undefined, async () => { calls++; return json({ code, message: "secret payload" }, 503); }),
      error => error instanceof AskError && error.code === (code === "UNKNOWN_UPSTREAM" ? "ASK_UNAVAILABLE" : code) && !error.message.includes("secret"));
    assert.equal(calls, 1);
  }
});
test("typing and mount are zero requests, explicit submit is one, no browser storage/history", async () => {
  const questions: string[] = [];
  const session = new AskSession(async question => { questions.push(question); return result; });
  for (const question of ["h", "ho", "how", "how\nnext line"]) session.draft(question);
  await new Promise(resolve => setTimeout(resolve, 200)); assert.deepEqual(questions, []);
  await session.submit(); assert.deepEqual(questions, ["how\nnext line"]); assert.equal(session.getSnapshot().status, "success");
  session.draft("unsubmitted"); assert.equal(session.getSnapshot().submittedQuestion, "how\nnext line"); assert.equal(questions.length, 1);
});
test("duplicate active submit is prevented even for changed draft", async () => {
  let resolve!: (result: AskResult) => void; let calls = 0;
  const session = new AskSession(() => { calls++; return new Promise(done => { resolve = done; }); });
  session.draft("first"); const active = session.submit(); assert.equal(session.getSnapshot().status, "loading");
  await session.submit(); session.draft("second"); await session.submit(); assert.equal(calls, 1);
  resolve(result); await active; assert.equal(session.getSnapshot().draft, "second"); assert.equal(session.getSnapshot().submittedQuestion, "first");
});
test("cancel and latest-response guard prevent obsolete success/error replacing current answer", async () => {
  const pending: { resolve: (result: AskResult) => void; signal?: AbortSignal }[] = [];
  const session = new AskSession((_question, signal) => new Promise(resolve => pending.push({ resolve, signal })));
  session.draft("first"); const first = session.submit(); session.clear(); assert.equal(pending[0].signal?.aborted, true);
  session.draft("second"); const second = session.submit(); pending[1].resolve(result); await second;
  pending[0].resolve(noContext); await first; assert.equal(session.getSnapshot().result?.status, "ANSWERED");
  assert.equal(session.getSnapshot().submittedQuestion, "second");
});
test("NO_CONTEXT, all unavailable states and explicit retry preserve draft without automatic requests", async () => {
  let calls = 0; const session = new AskSession(async () => { calls++; if (calls === 1) throw new AskError("ASK_RETRIEVAL_UNAVAILABLE"); return noContext; });
  session.draft("my question"); await session.submit(); assert.equal(calls, 1); assert.equal(session.getSnapshot().error, "ASK_RETRIEVAL_UNAVAILABLE");
  assert.equal(session.getSnapshot().draft, "my question"); await session.submit(); assert.equal(calls, 2);
  assert.equal(session.getSnapshot().result?.status, "NO_CONTEXT");
});
test("question validation makes no request for blanks/oversized values", async () => {
  let calls = 0; const session = new AskSession(async () => { calls++; return result; });
  for (const question of [" ", "x".repeat(2001)]) { session.draft(question); await session.submit(); assert.equal(session.getSnapshot().error, "VALIDATION_ERROR"); }
  assert.equal(calls, 0);
});
test("focused BFF bounds body, rejects cross-origin/unknown/invalid fields and trims question", async () => {
  const request = (body: string, headers: Record<string, string> = {}) => new Request("http://localhost:3000/api/ask-my-knowledge", {
    method: "POST", body, headers: { "Content-Type": "application/json", Origin: "http://localhost:3000", ...headers } });
  assert.equal(await readAskQuestion(request('{"question":"  hello\nworld  "}'.replace("\n", "\\n"))), "hello\nworld");
  for (const body of ["broken", '{"question":" "}', JSON.stringify({ question: "x".repeat(2001) }), '{"question":"q","ownerId":"other"}',
    JSON.stringify({ question: "x".repeat(17000) })]) await assert.rejects(readAskQuestion(request(body)), AskRequestError);
  await assert.rejects(readAskQuestion(request('{"question":"q"}', { Origin: "https://attacker.example" })), error => error instanceof AskRequestError && error.status === 403);
  await assert.rejects(readAskQuestion(request('{"question":"q"}', { "Content-Type": "text/plain" })), AskRequestError);
  // Next's internal URL differs from browser-visible Host in production; legitimate requests still work.
  assert.equal(await readAskQuestion(request('{"question":"q"}', { Host: "127.0.0.1:3000", Origin: "http://127.0.0.1:3000" })), "q");
});
test("generated Markdown supports technical prose/code but never executes HTML/Mermaid/links/images", () => {
  const html = renderToStaticMarkup(createElement(AskAnswer, { answer: "**Bold** and `inline`\n\n- item\n\n```mermaid\nflowchart LR\nA-->B\n```\n\n[click](javascript:alert(1))\n\n[external](https://evil.example)\n\n![image](https://evil.example/x)\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>" }));
  assert.match(html, /<strong>Bold<\/strong>/); assert.match(html, /<code/); assert.match(html, /flowchart LR/);
  assert.doesNotMatch(html, /<script|<img|<a |<svg|onerror|javascript:|https:\/\/evil|dangerouslySetInnerHTML/);
});
test("private route/UI/server transport wires CSRF, plain excerpts and explicit keyboard submit", () => {
  const source = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");
  const page = source("../app/ask/page.tsx"); assert.match(page, /await requireCurrentUser\(\)/); assert.match(page, /force-dynamic/);
  const client = source("../components/ask/ask-knowledge.tsx"); assert.match(client, /event.metaKey \|\| event.ctrlKey/);
  assert.match(client, /onSubmit/); assert.match(client, /disabled=\{loading/); assert.match(client, /SourceLinkedAnswer/);
  assert.doesNotMatch(client, /KnowledgeMarkdown|Mermaid|dangerouslySetInnerHTML|setTimeout|localStorage|sessionStorage|pushState|replaceState/);
  const api = source("api/ask-knowledge.ts"); assert.match(api, /import "server-only"/); assert.match(api, /backendRequest<unknown>/); assert.match(api, /method: "POST"/);
  const transport = source("api/backend.ts"); assert.match(transport, /isStateChangingMethod\(init\?\.method\) \? await csrfToken\(cookieHeader\)/);
  assert.equal(isStateChangingMethod("POST"), true);
  const headers = buildBackendHeaders("JSESSIONID=test-session", { "Content-Type": "application/json" }, { headerName: "X-CSRF-TOKEN", token: "test-token" });
  assert.equal(headers.get("Cookie"), "JSESSIONID=test-session"); assert.equal(headers.get("X-CSRF-TOKEN"), "test-token");
  const bff = source("../app/api/ask-my-knowledge/route.ts"); assert.match(bff, /private, no-store/); assert.match(bff, /readAskQuestion/);
  assert.doesNotMatch(bff, /NEXT_PUBLIC|GEMINI_API_KEY|KNOWLEDGE_API_BASE_URL/);
});
test("structured citation mapping rejects unknown, orphaned, non-deterministic and duplicate chunk linkage", () => {
  const citation = result.citations[0];
  const blocks = (citationIds: unknown[]) => ({ blocks: [{ markdown: "Answer", citationIds }] });
  for (const invalid of [
    { ...result, answer: blocks([]) }, { ...result, answer: blocks(["C999"]) }, { ...result, answer: blocks(["C1", "C1"]) },
    { ...result, citations: [{ ...citation, id: "C9" }] }, { ...result, citations: [{ ...citation, chunkIndex: -1 }] },
    { ...result, citations: [{ ...citation, evidence: " " }] }, { ...noContext, citations: [citation] },
    { ...result, citations: [citation, { ...citation, id: "C2" }] },
    { ...result, citations: [citation, { ...citation, id: "C2", chunkIndex: 3 }] },
    { ...result, answer: blocks(["C2", "C1"]), citations: [citation, { ...citation, id: "C2", chunkIndex: 3 }] },
    { ...result, answer: { blocks: Array(25).fill({ markdown: "x", citationIds: ["C1"] }) } },
    { ...result, answer: { blocks: Array(9).fill({ markdown: "x".repeat(8192), citationIds: ["C1"] }) } },
  ]) assert.throws(() => mapAskResponse(invalid), AskError);
});
test("inline citations reuse numbering, group a note once and render exact evidence safely", () => {
  const citations = [result.citations[0], { ...result.citations[0], id: "C2", chunkIndex: 5, evidence: "Second exact chunk" }];
  const answer = { blocks: [{ markdown: "**First** [fake source](https://evil.example) [99]", citationIds: ["C1", "C2"] }, { markdown: "Again", citationIds: ["C1"] }] };
  const html = renderToStaticMarkup(createElement(SourceLinkedAnswer, { answer, citations }));
  assert.equal((html.match(/aria-label="Source 1: Timeouts"/g) ?? []).length, 2);
  assert.match(html, /aria-controls="ask-evidence-C1"/); assert.match(html, /id="ask-evidence-C1" tabindex="-1"/);
  assert.equal((html.match(/aria-label="Open note: Timeouts"/g) ?? []).length, 1);
  assert.match(html, /href="\/knowledge\/timeouts"/); assert.match(html, /&lt;script&gt;literal note text&lt;\/script&gt;/);
  assert.match(html, /Second exact chunk/); assert.doesNotMatch(html, /https:\/\/evil|<script/);
  assert.equal((html.match(/<button/g) ?? []).length, 3); // Markdown [99] is inert, not a citation control.
});
test("citation activation focuses accessible evidence without URL/history mutation", () => {
  const actions: string[] = [];
  const fake = { getElementById: (id: string) => { actions.push(id); return { focus: () => actions.push("focus"), scrollIntoView: () => actions.push("scroll") } as unknown as HTMLElement; } };
  focusCitationEvidence("C1", fake); assert.deepEqual(actions, ["ask-evidence-C1", "focus", "scroll"]);
  focusCitationEvidence("C2", { getElementById: () => null });
});
