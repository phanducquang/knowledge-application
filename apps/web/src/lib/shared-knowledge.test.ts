import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  isSharedKnowledgeNotFound,
  mapApiSharedKnowledge,
  sharedKnowledgeMetadata,
  type ApiSharedKnowledgeResponse,
} from "./shared-knowledge.ts";

const response: ApiSharedKnowledgeResponse = {
  title: "Shared WebClient guide",
  slug: "shared-webclient-guide",
  summary: "A bearer-link summary",
  content: "## Configure timeouts\n\nUse `WebClient` safely.",
  collection: "Backend",
  tags: ["Spring Boot", "WebClient"],
  publishedAt: null,
  updatedAt: "2026-09-11T09:00:00Z",
};

test("maps the shared API response without token, owner, visibility or internal id", () => {
  const article = mapApiSharedKnowledge(response);

  assert.deepEqual(article, response);
  assert.equal("token" in article, false);
  assert.equal("shareToken" in article, false);
  assert.equal("ownerId" in article, false);
  assert.equal("id" in article, false);
  assert.equal("visibility" in article, false);
});

test("uses opaque 404 handling and noindex metadata", () => {
  assert.equal(isSharedKnowledgeNotFound(404), true);
  assert.equal(isSharedKnowledgeNotFound(401), false);
  assert.deepEqual(sharedKnowledgeMetadata(mapApiSharedKnowledge(response)), {
    title: "Shared WebClient guide",
    description: "A bearer-link summary",
    robots: { index: false, follow: false, noarchive: true },
  });
});

test("shared route is anonymous and reuses the external article without private controls", () => {
  const page = readFileSync(
    new URL("../app/s/[shareToken]/page.tsx", import.meta.url),
    "utf8",
  );
  const presentation = readFileSync(
    new URL("../components/knowledge/external-knowledge-article.tsx", import.meta.url),
    "utf8",
  );
  const apiClient = readFileSync(
    new URL("api/shared-knowledge.ts", import.meta.url),
    "utf8",
  );

  assert.match(page, /ExternalKnowledgeArticle/);
  assert.match(page, /dynamic = "force-dynamic"/);
  assert.doesNotMatch(page, /WorkspaceShell|requireCurrentUser|KnowledgeShareAction/);
  assert.match(presentation, /KnowledgeMarkdown/);
  assert.match(presentation, /ArticleToc/);
  assert.match(presentation, /calculateReadTime/);
  assert.doesNotMatch(presentation, />\s*Edit\s*</);
  assert.match(apiClient, /publicBackendRequest/);
  assert.doesNotMatch(apiClient, /backendRequest<|cookies\(/);
});

test("shared route has route-specific no-store, no-referrer and robots headers", () => {
  const config = readFileSync(new URL("../../next.config.ts", import.meta.url), "utf8");

  assert.match(config, /source: "\/s\/:path\*"/);
  assert.match(config, /private, no-store, max-age=0/);
  assert.match(config, /Referrer-Policy.*no-referrer/);
  assert.match(config, /X-Robots-Tag.*noindex, nofollow, noarchive/);
});

test("share dialog uses injected persistence callbacks without fake or browser-stored tokens", () => {
  const dialog = readFileSync(
    new URL("../components/knowledge/knowledge-share-action.tsx", import.meta.url),
    "utf8",
  );

  assert.match(dialog, /onVisibilityChange/);
  assert.match(dialog, /onLoadUnlistedLink/);
  assert.match(dialog, /onRegenerateUnlistedLink/);
  assert.doesNotMatch(dialog, /backend-managed-token|mock-\$\{slug\}|localStorage|sessionStorage/);
  assert.match(dialog, /current link will stop working immediately/i);
  assert.match(dialog, /Saving visibility/);
  assert.match(dialog, /Loading secret link/);
  assert.match(dialog, /Retry loading link/);
  assert.match(dialog, /Copy failed/);
});

test("Reading refreshes after persistence and Editor routes share changes through autosave serialization", () => {
  const reading = readFileSync(
    new URL("../components/knowledge/reading-knowledge-share-action.tsx", import.meta.url),
    "utf8",
  );
  const editor = readFileSync(
    new URL("../components/knowledge/knowledge-editor.tsx", import.meta.url),
    "utf8",
  );
  const actions = readFileSync(new URL("../app/knowledge/actions.ts", import.meta.url), "utf8");

  assert.match(reading, /updateKnowledgeVisibilityAction/);
  assert.match(reading, /router\.refresh\(\)/);
  assert.match(editor, /runSerializedVisibilityChange/);
  assert.match(editor, /inFlightSave: savePromiseRef\.current/);
  assert.match(actions, /getUnlistedLinkAction/);
  assert.match(actions, /regenerateUnlistedLinkAction/);
});
