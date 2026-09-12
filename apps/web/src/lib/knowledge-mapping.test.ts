import assert from "node:assert/strict";
import test from "node:test";
import {
  mapApiKnowledgeSearchResult,
  mapApiKnowledge,
  toApiKnowledgeWriteRequest,
  toKnowledgeListItem,
  type ApiKnowledgeResponse,
} from "./knowledge-mapping.ts";

const apiKnowledge: ApiKnowledgeResponse = {
  id: 42,
  title: "Persisted note",
  slug: "persisted-note",
  summary: null,
  content: "## Body",
  visibility: "PUBLIC",
  collection: null,
  tags: [],
  createdAt: "2026-09-10T01:00:00Z",
  updatedAt: "2026-09-10T02:00:00Z",
  publishedAt: "2026-09-10T02:00:00Z",
};

test("maps API visibility, nullable fields, timestamps, and stable slug to the view model", () => {
  const knowledge = mapApiKnowledge(apiKnowledge);
  const listItem = toKnowledgeListItem(knowledge);

  assert.equal(knowledge.visibility, "Public");
  assert.equal(knowledge.summary, "");
  assert.equal(knowledge.collection, null);
  assert.deepEqual(knowledge.tags, []);
  assert.equal(listItem.href, "/knowledge/persisted-note");
  assert.equal(listItem.updatedAtIso, apiKnowledge.updatedAt);
});

test("creates the exact writable API payload without server-owned fields", () => {
  const payload = toApiKnowledgeWriteRequest({
    title: "Draft",
    summary: "Summary",
    content: "",
    visibility: "Unlisted",
    collection: null,
    tags: [],
  });

  assert.deepEqual(payload, {
    title: "Draft",
    summary: "Summary",
    content: "",
    visibility: "UNLISTED",
    collection: null,
    tags: [],
  });
  assert.equal("ownerId" in payload, false);
  assert.equal("slug" in payload, false);
  assert.equal("updatedAt" in payload, false);
});

test("maps every API visibility value at one boundary", () => {
  assert.equal(mapApiKnowledge({ ...apiKnowledge, visibility: "PRIVATE" }).visibility, "Private");
  assert.equal(mapApiKnowledge({ ...apiKnowledge, visibility: "UNLISTED" }).visibility, "Unlisted");
  assert.equal(mapApiKnowledge({ ...apiKnowledge, visibility: "PUBLIC" }).visibility, "Public");
});

test("maps compact search responses without requiring Markdown content", () => {
  const result = mapApiKnowledgeSearchResult({
    id: 7,
    title: "Search result",
    slug: "search-result",
    summary: null,
    visibility: "UNLISTED",
    collection: "Backend",
    tags: ["Spring Boot"],
    updatedAt: "2026-09-10T03:00:00Z",
  });

  assert.equal(result.description, "");
  assert.equal(result.visibility, "Unlisted");
  assert.equal(result.href, "/knowledge/search-result");
  assert.deepEqual(result.tags, ["Spring Boot"]);
});
