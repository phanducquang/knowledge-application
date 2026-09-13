import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  codeLanguageFromClassName,
  highlightCode,
  highlightedSourceText,
  normalizeCodeLanguage,
} from "./syntax-highlighting.ts";

test("normalizes the supported language names and common aliases", () => {
  assert.equal(normalizeCodeLanguage("java"), "java");
  assert.equal(normalizeCodeLanguage("JS"), "javascript");
  assert.equal(normalizeCodeLanguage("jsx"), "javascript");
  assert.equal(normalizeCodeLanguage("ts"), "typescript");
  assert.equal(normalizeCodeLanguage("tsx"), "typescript");
  assert.equal(normalizeCodeLanguage("sh"), "bash");
  assert.equal(normalizeCodeLanguage("shell"), "bash");
  assert.equal(normalizeCodeLanguage("yml"), "yaml");
  assert.equal(normalizeCodeLanguage("html"), "xml");
  assert.equal(normalizeCodeLanguage("properties"), "ini");
});

test("extracts a supported fenced language without accepting unrelated classes", () => {
  assert.equal(codeLanguageFromClassName("language-typescript"), "typescript");
  assert.equal(codeLanguageFromClassName("extra language-bash"), "bash");
  assert.equal(codeLanguageFromClassName("inline-code"), null);
});

test("highlights representative Java and TypeScript without changing source text", () => {
  const javaSource = "public class Example {\n    private final int value = 1;\n}\n";
  const typeScriptSource = 'const message: string = "hello";\n';
  const javaTree = highlightCode(javaSource, "language-java");
  const typeScriptTree = highlightCode(typeScriptSource, "language-ts");

  assert.ok(javaTree);
  assert.ok(typeScriptTree);
  assert.equal(highlightedSourceText(javaTree), javaSource);
  assert.equal(highlightedSourceText(typeScriptTree), typeScriptSource);
  assert.ok(javaTree.children.some((node) => node.type === "element"));
});

test("handles shell and YAML aliases", () => {
  assert.ok(highlightCode("./gradlew clean build\n", "language-shell"));
  assert.ok(highlightCode("name: knowledge\n", "language-yml"));
});

test("supports the initial technical language set", () => {
  for (const [language, source] of [
    ["javascript", "const value = true;\n"],
    ["json", '{"value": true}\n'],
    ["sql", "SELECT value FROM example;\n"],
    ["python", "value = True\n"],
    ["html", "<strong>value</strong>\n"],
    ["css", ".value { color: inherit; }\n"],
    ["markdown", "## Code heading\n"],
  ] as const) {
    const tree = highlightCode(source, `language-${language}`);
    assert.ok(tree, `${language} should be registered`);
    assert.equal(highlightedSourceText(tree), source);
  }
});

test("falls back for unknown and absent languages without auto-detection", () => {
  assert.equal(highlightCode("raw content\n", "language-some-internal-format"), null);
  assert.equal(highlightCode("raw content\n", undefined), null);
});

test("keeps HTML-looking source as inert code text", () => {
  const source = '<script>alert("never execute")</script>\n';
  const tree = highlightCode(source, "language-html");
  assert.ok(tree);
  assert.equal(highlightedSourceText(tree), source);
});

test("keeps one safe shared renderer for every reading surface", () => {
  const renderer = readFileSync(
    new URL("../components/knowledge/knowledge-markdown.tsx", import.meta.url),
    "utf8",
  );
  const block = readFileSync(
    new URL("../components/knowledge/knowledge-code-block.tsx", import.meta.url),
    "utf8",
  );
  const privateReading = readFileSync(
    new URL("../app/knowledge/[slug]/page.tsx", import.meta.url),
    "utf8",
  );
  const externalReading = readFileSync(
    new URL("../components/knowledge/external-knowledge-article.tsx", import.meta.url),
    "utf8",
  );
  const history = readFileSync(
    new URL("../components/knowledge/knowledge-history.tsx", import.meta.url),
    "utf8",
  );

  assert.match(renderer, /pre: KnowledgeCodeBlock/);
  assert.match(privateReading, /<KnowledgeMarkdown/);
  assert.match(externalReading, /<KnowledgeMarkdown/);
  assert.match(history, /<KnowledgeMarkdown/);
  assert.doesNotMatch(`${renderer}\n${block}`, /rehype-raw|dangerouslySetInnerHTML/);
  assert.match(renderer, /attachmentIdFromReference/);
  assert.match(renderer, /rounded-\[3px\].*text-\[0\.88em\]/);
});
