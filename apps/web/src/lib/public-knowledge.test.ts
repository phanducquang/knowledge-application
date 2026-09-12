import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  isPublicKnowledgeNotFound,
  mapApiPublicKnowledge,
  publicKnowledgeMetadata,
  type ApiPublicKnowledgeResponse,
} from "./public-knowledge.ts";

const response: ApiPublicKnowledgeResponse = {
  title: "Public WebClient guide",
  slug: "public-webclient-guide",
  summary: "A public summary",
  content: "## Configure timeouts\n\nUse `WebClient` safely.",
  collection: "Backend",
  tags: ["Spring Boot", "WebClient"],
  publishedAt: "2026-09-11T08:00:00Z",
  updatedAt: "2026-09-11T09:00:00Z",
};

test("maps the dedicated public API response without owner or internal id", () => {
  const article = mapApiPublicKnowledge(response);

  assert.deepEqual(article, response);
  assert.equal("ownerId" in article, false);
  assert.equal("id" in article, false);
  assert.equal("visibility" in article, false);
});

test("uses opaque 404 handling for unavailable public knowledge", () => {
  assert.equal(isPublicKnowledgeNotFound(404), true);
  assert.equal(isPublicKnowledgeNotFound(401), false);
  assert.equal(isPublicKnowledgeNotFound(503), false);
});

test("builds indexable metadata from real public content", () => {
  const metadata = publicKnowledgeMetadata(mapApiPublicKnowledge(response));

  assert.deepEqual(metadata, {
    title: "Public WebClient guide",
    description: "A public summary",
    robots: { index: true, follow: true },
  });
});

test("public page reuses reading primitives without private workspace controls", () => {
  const source = readFileSync(
    new URL("../app/k/[slug]/page.tsx", import.meta.url),
    "utf8",
  );
  const presentation = readFileSync(
    new URL("../components/knowledge/external-knowledge-article.tsx", import.meta.url),
    "utf8",
  );

  assert.match(source, /ExternalKnowledgeArticle/);
  assert.match(presentation, /KnowledgeMarkdown/);
  assert.match(presentation, /ArticleToc/);
  assert.match(presentation, /calculateReadTime/);
  assert.doesNotMatch(source, /WorkspaceShell/);
  assert.doesNotMatch(source, /requireCurrentUser/);
  assert.doesNotMatch(source, /KnowledgeShareAction/);
  assert.doesNotMatch(source, />\s*Edit\s*</);
  assert.doesNotMatch(source, /owner email|Sign out|QuickSearch/i);
});
