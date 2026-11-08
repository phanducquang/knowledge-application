import { Slice, type Node } from "@milkdown/kit/prose/model";
import type { EditorView } from "@milkdown/kit/prose/view";
import type { Parser } from "@milkdown/kit/transformer";

function hasFormatting(doc: Node) {
  let formatted = false;
  doc.descendants((node) => {
    if ((!node.isText && !["paragraph", "html"].includes(node.type.name)) || node.marks.length > 0) formatted = true;
    return !formatted;
  });
  return formatted;
}

/** Let Milkdown parse Markdown source before HTML/VS Code clipboard fallbacks. */
export function createMarkdownPasteHandler(parse: Parser) {
  return (view: EditorView, event: ClipboardEvent): boolean => {
    const data = event.clipboardData;
    if (!view.editable || view.state.selection.$from.parent.type.spec.code || !data || data.files.length) return false;
    const text = data.getData("text/plain").replace(/\r\n?/g, "\n");
    if (!text.trim()) return false;

    const vscode = data.getData("vscode-editor-data");
    if (vscode) {
      try {
        const { mode } = JSON.parse(vscode);
        if (mode && !["markdown", "plaintext"].includes(mode)) return false;
      } catch {
        return false;
      }
    }

    try {
      const doc = parse(text);
      // Ordinary prose still uses Milkdown's rich HTML/plain-text paste behavior.
      if (!doc || !hasFormatting(doc)) return false;
      const inline = doc.childCount === 1 && doc.firstChild?.type.name === "paragraph";
      const slice = new Slice(doc.content, inline ? 1 : 0, inline ? 1 : 0);
      view.dispatch(view.state.tr.replaceSelection(slice).scrollIntoView().setMeta("paste", true).setMeta("uiEvent", "paste"));
      return true;
    } catch {
      return false;
    }
  };
}
