import assert from "node:assert/strict";
import test from "node:test";
import { Clock, Container, Ctx } from "@milkdown/kit/ctx";
import { marksCtx, nodesCtx, remarkCtx, remarkStringifyOptionsCtx } from "@milkdown/kit/core";
import { Schema } from "@milkdown/kit/prose/model";
import { EditorState, TextSelection, type Transaction } from "@milkdown/kit/prose/state";
import { history, undo } from "@milkdown/kit/prose/history";
import type { EditorView } from "@milkdown/kit/prose/view";
import { ParserState, SerializerState } from "@milkdown/kit/transformer";
import { docSchema, textSchema, paragraphSchema, headingSchema, headingIdGenerator, codeBlockSchema,
  bulletListSchema, orderedListSchema, listItemSchema, blockquoteSchema, strongSchema } from "@milkdown/kit/preset/commonmark";
import { createMarkdownPasteHandler } from "./markdown-paste.ts";

// Use Milkdown's real node schemas and Markdown parser without requiring a browser DOM.
const ctx = new Ctx(new Container(), new Clock());
ctx.inject(nodesCtx, []).inject(marksCtx, []).inject(remarkCtx).inject(remarkStringifyOptionsCtx);
headingIdGenerator(ctx);
await docSchema(ctx)();
await textSchema(ctx)();
for (const plugin of [paragraphSchema, headingSchema, codeBlockSchema, bulletListSchema, orderedListSchema, listItemSchema, blockquoteSchema]) {
  plugin.ctx(ctx);
  await plugin.node(ctx)();
}
strongSchema.ctx(ctx);
await strongSchema.mark(ctx)();
const schema = new Schema({ nodes: Object.fromEntries(ctx.get(nodesCtx)), marks: Object.fromEntries(ctx.get(marksCtx)) });
const parse = ParserState.create(schema, ctx.get(remarkCtx));
const serialize = SerializerState.create(schema, ctx.get(remarkCtx));
const paste = createMarkdownPasteHandler(parse);
const heading = "# Runbook: Cấu hình Nginx Reverse Proxy & HTTPS cho một service";

function editor(markdown = "", editable = true) {
  let state = EditorState.create({ schema, doc: parse(markdown), plugins: [history()] });
  let dispatched = 0;
  const view = { editable, get state() { return state; }, dispatch(tr: Transaction) { state = state.apply(tr); dispatched++; } } as EditorView;
  return { view, get state() { return state; }, get dispatched() { return dispatched; } };
}
function clipboard(text: string, extra: Record<string, string> = {}, files = 0) {
  return { clipboardData: { getData: (type: string) => type === "text/plain" ? text : extra[type] ?? "", files: { length: files } } } as unknown as ClipboardEvent;
}

const clipboardSources: Record<string, Record<string, string>> = {
  plain: {},
  html: { "text/html": `<span>${heading.replace("&", "&amp;")}</span>` },
  vscode: { "vscode-editor-data": JSON.stringify({ mode: "markdown" }), "text/html": `<pre>${heading}</pre>` },
};
for (const [name, extra] of Object.entries(clipboardSources)) {
  test(`pastes the reported Runbook as H1 from ${name} clipboard`, () => {
    const target = editor();
    assert.equal(paste(target.view, clipboard(heading, extra)), true);
    assert.equal(target.state.doc.firstChild?.type.name, "heading");
    assert.equal(target.state.doc.firstChild?.attrs.level, 1);
    assert.equal(target.state.doc.firstChild?.textContent, heading.slice(2));
    assert.equal(serialize(target.state.doc).trim(), heading);
    assert.equal(target.dispatched, 1);
  });
}

test("a Markdown paste is reversible with one undo", () => {
  const target = editor();
  const before = target.state.doc.toJSON();
  assert.equal(paste(target.view, clipboard(heading)), true);
  assert.equal(undo(target.state, target.view.dispatch), true);
  assert.deepEqual(target.state.doc.toJSON(), before);
});

test("pastes Markdown blocks as headings, lists, quotes and fenced code", () => {
  const target = editor();
  assert.equal(paste(target.view, clipboard("## Setup\r\n\r\n- Nginx\r\n- HTTPS\r\n\r\n> Check config\r\n\r\n```nginx\r\nserver {}\r\n```")), true);
  assert.deepEqual(target.state.doc.content.content.map(node => node.type.name), ["heading", "bullet_list", "blockquote", "code_block"]);
  assert.equal(target.state.doc.lastChild?.textContent, "server {}");
});

test("inline Markdown stays in the current paragraph and replaces only the selection", () => {
  const target = editor("Before selected after");
  target.view.dispatch(target.state.tr.setSelection(TextSelection.create(target.state.doc, 8, 16)));
  assert.equal(paste(target.view, clipboard("**formatted**")), true);
  assert.equal(target.state.doc.childCount, 1);
  assert.equal(target.state.doc.textContent, "Before formatted after");
  assert.equal(target.state.doc.firstChild?.child(1).marks[0].type.name, "strong");
});

test("preserves native paste inside a code block and for non-Markdown VS Code sources", () => {
  const code = editor("```sh\necho test\n```");
  assert.equal(paste(code.view, clipboard(heading)), false);
  const target = editor();
  assert.equal(paste(target.view, clipboard("# Python comment", { "vscode-editor-data": '{"mode":"python"}' })), false);
  assert.equal(target.dispatched, 0);
});

test("preserves native rich-text, file, empty and read-only paste", () => {
  const target = editor();
  assert.equal(paste(target.view, clipboard("Bold text", { "text/html": "<strong>Bold text</strong>" })), false);
  assert.equal(paste(target.view, clipboard(heading, {}, 1)), false);
  assert.equal(paste(target.view, clipboard("")), false);
  assert.equal(paste(editor("", false).view, clipboard(heading)), false);
  assert.equal(target.dispatched, 0);
});
