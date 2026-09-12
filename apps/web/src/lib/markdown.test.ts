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
  const markdown = "## Real\n```markdown\n## Not real\n```\n~~~\n### Also not real\n~~~\n### Real detail";
  assert.deepEqual(extractMarkdownHeadings(markdown).map((heading) => heading.label), [
    "Real",
    "Real detail",
  ]);
});

test("calculates deterministic read time without persistence", () => {
  assert.equal(calculateReadTime("one two three"), "1 min read");
  assert.equal(calculateReadTime(Array.from({ length: 201 }, () => "word").join(" ")), "2 min read");
});
