import assert from "node:assert/strict";
import test from "node:test";
import {
  fetchKnowledgeSearch,
  knowledgeSearchRequestUrl,
  LatestSearchRequest,
  viewAllKnowledgeSearchHref,
} from "./knowledge-search.ts";
import type { KnowledgeListItemData } from "../types/knowledge.ts";

const first: KnowledgeListItemData = {
  id: 2,
  slug: "backend-first",
  title: "Backend first",
  description: "",
  collection: "Backend",
  tags: [],
  visibility: "Private",
  updatedAt: "Sep 10",
  updatedAtIso: "2026-09-10T02:00:00Z",
  href: "/knowledge/backend-first",
};

const second: KnowledgeListItemData = {
  ...first,
  id: 1,
  slug: "backend-second",
  title: "Backend second",
  updatedAtIso: "2026-09-10T03:00:00Z",
  href: "/knowledge/backend-second",
};

test("builds the focused same-origin BFF request with a trimmed encoded query", () => {
  assert.equal(
    knowledgeSearchRequestUrl("  spring boot  ", 6),
    "/api/knowledge-search?q=spring+boot&limit=6",
  );
});

test("keeps backend relevance order without client-side re-ranking", async () => {
  let requestedUrl = "";
  const results = await fetchKnowledgeSearch("backend", 6, undefined, async (input) => {
    requestedUrl = String(input);
    return new Response(JSON.stringify([first, second]), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });

  assert.equal(requestedUrl, "/api/knowledge-search?q=backend&limit=6");
  assert.deepEqual(results.map((item) => item.slug), ["backend-first", "backend-second"]);
});

test("request sequence prevents an older response from becoming current", () => {
  const requests = new LatestSearchRequest();
  const older = requests.begin();
  const newer = requests.begin();

  assert.equal(requests.isLatest(older), false);
  assert.equal(requests.isLatest(newer), true);
  requests.invalidate();
  assert.equal(requests.isLatest(newer), false);
});

test("view all keeps the approved search URL", () => {
  assert.equal(viewAllKnowledgeSearchHref(" spring boot "), "/search?q=spring%20boot");
});
