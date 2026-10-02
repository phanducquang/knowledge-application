import assert from "node:assert/strict";
import test from "node:test";
import type { Root, Text } from "mdast";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ReactMarkdown from "react-markdown";
import { remarkWikiLinks } from "./wiki-links.ts";

const targets = [{ slug: "target-note", title: "Target Note" }];

function prose(source: string, value = source): Root {
  return {
    type: "root",
    children: [{
      type: "paragraph",
      children: [{
        type: "text",
        value,
        position: {
          start: { line: 1, column: 1, offset: 0 },
          end: { line: 1, column: source.length + 1, offset: source.length },
        },
      }],
    }],
  };
}

test("resolves an owned wiki slug to its current title and stable reading route", () => {
  const source = "See [[target-note]] and [[missing-note]].";
  const tree = prose(source);
  remarkWikiLinks({ source, targets })(tree);
  const children = tree.children[0].type === "paragraph" ? tree.children[0].children : [];
  assert.deepEqual(children.map((child) => child.type), ["text", "link", "text"]);
  assert.deepEqual(children[1], {
    type: "link",
    url: "/knowledge/target-note",
    title: null,
    children: [{ type: "text", value: "Target Note" }],
  });
  assert.equal((children[2] as Text).value, " and [[missing-note]].");
});

test("leaves escaped references, code and existing Markdown links unchanged", () => {
  const source = "\\[[target-note]]";
  const tree = prose(source, "[[target-note]]");
  tree.children.push({
    type: "paragraph",
    children: [
      { type: "inlineCode", value: "[[target-note]]" },
      { type: "link", url: "/elsewhere", children: [{ type: "text", value: "[[target-note]]" }] },
    ],
  });
  remarkWikiLinks({ source, targets })(tree);
  assert.equal(tree.children[0].type === "paragraph" && tree.children[0].children[0].type, "text");
  assert.equal(tree.children[1].type === "paragraph" && tree.children[1].children[1].type, "link");
});

test("renders only the owned prose reference as a link in the actual Markdown pipeline", () => {
  const source = "See [[target-note]], \\[[target-note]], and `[[target-note]]`.";
  const plugin: [typeof remarkWikiLinks, { source: string; targets: typeof targets }] = [
    remarkWikiLinks,
    { source, targets },
  ];
  const html = renderToStaticMarkup(
    React.createElement(ReactMarkdown, { remarkPlugins: [plugin] }, source),
  );
  assert.equal(html, "<p>See <a href=\"/knowledge/target-note\">Target Note</a>, [[target-note]], and <code>[[target-note]]</code>.</p>");
});
