import { createElement as h } from "react";
import Link from "next/link.js";
import { Handle, Position, type NodeProps } from "@xyflow/react";
import { graphNoteHref, type KnowledgeFlowNode } from "../../lib/knowledge-graph.ts";

export function KnowledgeGraphNode({ data }: NodeProps<KnowledgeFlowNode>) {
  const { note, focused, neighbor } = data;
  return h("div", {
    className: `h-full border bg-[var(--surface)] px-3 py-2 text-left ${focused ? "border-[var(--accent)] outline outline-2 outline-offset-2 outline-[var(--accent)]" : neighbor ? "border-[var(--accent-muted)]" : "border-[var(--border-strong)]"}`,
    title: [note.title, note.collection ?? "Unfiled", note.tags.join(", ")].filter(Boolean).join(" / "),
  },
  h(Handle, { type: "target", position: Position.Left, isConnectable: false, className: "!border-0 !bg-[var(--border-strong)]" }),
  h(Link, {
    href: graphNoteHref(note.slug),
    onClick: (event) => event.stopPropagation(),
    className: "nodrag nopan block line-clamp-2 break-words text-[13px] font-medium leading-[18px] text-[var(--text)] hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]",
  }, note.title),
  h("p", { className: "mt-1 truncate text-[11px] leading-4 text-[var(--text-subtle)]" }, note.collection ?? "Unfiled"),
  h(Handle, { type: "source", position: Position.Right, isConnectable: false, className: "!border-0 !bg-[var(--border-strong)]" }),
  );
}
