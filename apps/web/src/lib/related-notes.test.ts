import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { RelatedNotes } from "../components/knowledge/related-notes.ts";
import type { KnowledgeRelatedData } from "../types/knowledge.ts";

const notes: KnowledgeRelatedData[] = [{
  id: 2, slug: "stable-slug", title: "Current renamed title", summary: "A short summary",
  reasons: ["WIKI_LINK", "BACKLINK", "SHARED_TAG", "SAME_COLLECTION"],
}];

test("renders one internal destination with all merged reasons and the current title", () => {
  const html = renderToStaticMarkup(createElement(RelatedNotes, { notes }));
  assert.match(html, /Related notes/);
  assert.equal((html.match(/href="\/knowledge\/stable-slug"/g) ?? []).length, 1);
  assert.match(html, /Current renamed title/);
  assert.match(html, /A short summary/);
  assert.match(html, /Linked from this note · Links to this note · Shared tags · Same collection/);
  assert.doesNotMatch(html, /ownerId|shareToken/);
});

test("omits an empty section and handles null summary without changing backend order", () => {
  assert.equal(renderToStaticMarkup(createElement(RelatedNotes, { notes: [] })), "");
  const html = renderToStaticMarkup(createElement(RelatedNotes, { notes: [
    { ...notes[0], title: "First", summary: null, reasons: ["SHARED_TAG"] },
    { ...notes[0], id: 3, slug: "second", title: "Second", reasons: ["SAME_COLLECTION"] },
  ] }));
  assert.ok(html.indexOf("First") < html.indexOf("Second"));
  assert.doesNotMatch(html, />null</);
});

test("private Reading keeps Backlinks and loads related notes through the server-only boundary", () => {
  const reading = readFileSync(new URL("../app/knowledge/[slug]/page.tsx", import.meta.url), "utf8");
  const api = readFileSync(new URL("api/knowledge.ts", import.meta.url), "utf8");
  assert.match(reading, /await requireCurrentUser\(\)/);
  assert.match(reading, /listKnowledgeBacklinks\(article.id\)/);
  assert.match(reading, /listRelatedKnowledge\(article.id\)/);
  assert.ok(reading.indexOf("<RelatedNotes") > reading.indexOf('aria-labelledby="backlinks-heading"'));
  assert.match(reading, /No notes link here yet/);
  assert.match(api, /import "server-only"/);
  assert.match(api, /\/related\?limit=5/);
  for (const path of ["../components/knowledge/external-knowledge-article.tsx", "../app/k/[slug]/page.tsx", "../app/s/[shareToken]/page.tsx"]) {
    assert.doesNotMatch(readFileSync(new URL(path, import.meta.url), "utf8"), /RelatedNotes|listRelatedKnowledge|listKnowledgeBacklinks/);
  }
});
