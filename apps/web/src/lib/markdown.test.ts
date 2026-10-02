import assert from "node:assert/strict";
import test from "node:test";
import { calculateReadTime, extractMarkdownHeadings } from "./markdown.ts";

test("extracts H2-H4 headings with stable duplicate anchors", () => {
  assert.deepEqual(
    extractMarkdownHeadings("## Overview\n### Detail\n#### Deep dive\n## Overview"),
    [
      { id: "overview", label: "Overview", level: 2 },
      { id: "detail", label: "Detail", level: 3 },
      { id: "deep-dive", label: "Deep dive", level: 4 },
      { id: "overview-2", label: "Overview", level: 2 },
    ],
  );
});

test("does not treat headings inside fenced code as article headings", () => {
  const markdown = "## Real\n```mermaid\n%% ## Not real\nflowchart TD\n  A --> B\n```\n~~~\n### Also not real\n~~~\n### Real detail";
  assert.deepEqual(extractMarkdownHeadings(markdown).map((heading) => heading.label), [
    "Real",
    "Real detail",
  ]);
});

test("owner table of contents tracks resolved wiki titles without changing escaped references", () => {
  const titles = new Map([["target-note", "Target Note"]]);
  assert.deepEqual(
    extractMarkdownHeadings("## See [[target-note]]\n### Escaped \\[[target-note]]", titles),
    [
      { id: "see-target-note", label: "See Target Note", level: 2 },
      { id: "escaped-target-note", label: "Escaped [[target-note]]", level: 3 },
    ],
  );
});

test("calculates deterministic read time without persistence", () => {
  assert.equal(calculateReadTime("one two three"), "1 min read");
  assert.equal(calculateReadTime(Array.from({ length: 201 }, () => "word").join(" ")), "2 min read");
});
