import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  codeBlockSourceText,
  createMermaidRenderGuard,
  isMermaidCodeBlock,
  mermaidRenderId,
} from "./mermaid.ts";

test("classifies only explicitly labelled Mermaid fences", () => {
  assert.equal(isMermaidCodeBlock("language-mermaid"), true);
  assert.equal(isMermaidCodeBlock("extra language-MERMAID"), true);
  assert.equal(isMermaidCodeBlock("language-java"), false);
  assert.equal(isMermaidCodeBlock("language-typescript"), false);
  assert.equal(isMermaidCodeBlock("language-some-internal-format"), false);
  assert.equal(isMermaidCodeBlock(undefined), false);
});

test("creates internal render IDs without leaking source or route data", () => {
  const first = mermaidRenderId(":R1:", 1);
  const second = mermaidRenderId(":R2:", 1);
  assert.match(first, /^knowledge-mermaid-[a-zA-Z0-9_-]+-1$/);
  assert.notEqual(first, second);
  assert.doesNotMatch(first, /flowchart|slug|token|@/);
});

test("preserves Mermaid source whitespace and newlines exactly", () => {
  const source = "flowchart TD\n    A --> B\n    %% ## Not a heading\n";
  assert.equal(codeBlockSourceText([source]), source);
  assert.equal(codeBlockSourceText(["sequenceDiagram\n", ["    A->>B: Request\n"]]), "sequenceDiagram\n    A->>B: Request\n");
});

test("prevents a stale asynchronous render from replacing the latest source", () => {
  const guard = createMermaidRenderGuard();
  const first = guard.begin();
  const second = guard.begin();
  assert.equal(guard.isCurrent(first), false);
  assert.equal(guard.isCurrent(second), true);
  guard.invalidate();
  assert.equal(guard.isCurrent(second), false);
});

test("keeps Mermaid integration centralized and canonical Markdown unchanged", () => {
  const renderer = readFileSync(
    new URL("../components/knowledge/knowledge-markdown.tsx", import.meta.url),
    "utf8",
  );
  const codeBlock = readFileSync(
    new URL("../components/knowledge/knowledge-code-block.tsx", import.meta.url),
    "utf8",
  );
  const diagram = readFileSync(
    new URL("../components/knowledge/knowledge-mermaid-diagram.tsx", import.meta.url),
    "utf8",
  );

  assert.match(renderer, /pre: KnowledgeCodeBlock/);
  assert.match(codeBlock, /isMermaidCodeBlock/);
  assert.match(codeBlock, /KnowledgeMermaidDiagram/);
  assert.match(codeBlock, /highlightCode/);
  assert.match(diagram, /import\("mermaid"\)/);
  assert.match(diagram, /securityLevel: "strict"/);
  assert.match(diagram, /htmlLabels: false/);
  assert.doesNotMatch(`${renderer}\n${codeBlock}`, /rehype-raw|dangerouslySetInnerHTML/);
  assert.match(renderer, /attachmentIdFromReference/);
  assert.match(renderer, /rounded-\[3px\].*text-\[0\.88em\]/);
});
