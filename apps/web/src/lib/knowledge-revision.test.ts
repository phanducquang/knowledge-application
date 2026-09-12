import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

test("history is protected and loads compact pages before selected detail", () => {
  const page = readFileSync(
    new URL("../app/knowledge/[slug]/history/page.tsx", import.meta.url),
    "utf8",
  );
  const api = readFileSync(new URL("api/knowledge.ts", import.meta.url), "utf8");

  assert.match(page, /requireCurrentUser\(\)/);
  assert.match(page, /listKnowledgeRevisions\(knowledge\.id\)/);
  assert.match(page, /getKnowledgeRevision\(knowledge\.id/);
  assert.match(api, /\/api\/knowledge\/\$\{id\}\/revisions\?/);
  assert.match(api, /\/api\/knowledge\/\$\{id\}\/revisions\/\$\{revisionId\}/);
});

test("history previews Markdown and confirms restore inline through the CSRF-aware action", () => {
  const history = readFileSync(
    new URL("../components/knowledge/knowledge-history.tsx", import.meta.url),
    "utf8",
  );
  const actions = readFileSync(new URL("../app/knowledge/actions.ts", import.meta.url), "utf8");
  const editor = readFileSync(
    new URL("../components/knowledge/knowledge-editor.tsx", import.meta.url),
    "utf8",
  );

  assert.match(history, /KnowledgeMarkdown markdown=\{selected\.content\}/);
  assert.match(history, /Load older revisions/);
  assert.match(history, /Confirm restore/);
  assert.doesNotMatch(history, /role="dialog"|fixed inset-0/);
  assert.match(actions, /restoreKnowledgeRevisionAction/);
  assert.match(actions, /revalidatePath\(`\/knowledge\/\$\{slug\}\/history`\)/);
  assert.match(editor, /await flushSaveRef\.current\(\)/);
  assert.match(editor, /router\.push\(`\/knowledge\/\$\{initialKnowledge\.slug\}\/history`\)/);
});
