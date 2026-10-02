import { createElement as h } from "react";
import Link from "next/link.js";
import type { KnowledgeRelatedData, KnowledgeRelationReason } from "../../types/knowledge.ts";

const reasonLabels: Record<KnowledgeRelationReason, string> = {
  WIKI_LINK: "Linked from this note",
  BACKLINK: "Links to this note",
  SHARED_TAG: "Shared tags",
  SAME_COLLECTION: "Same collection",
};

// A small server-rendered list; the API owns deduplication and ranking.
export function RelatedNotes({ notes }: { notes: KnowledgeRelatedData[] }) {
  if (notes.length === 0) return null;

  return h("section", {
    className: "mt-10 border-t border-[var(--border)] pt-6",
    "aria-labelledby": "related-notes-heading",
  },
  h("h2", {
    id: "related-notes-heading",
    className: "text-[13px] font-medium text-[var(--text-muted)]",
  }, "Related notes"),
  h("ul", { className: "mt-3 border-t border-[var(--border)]" },
    notes.map((note) => h("li", {
      key: note.id,
      className: "min-w-0 border-b border-[var(--border)] py-3",
    },
    h(Link, {
      href: `/knowledge/${note.slug}`,
      className: "break-words text-[14px] font-medium text-[var(--text)] underline-offset-4 hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]",
    }, note.title),
    note.summary ? h("p", {
      className: "mt-1 line-clamp-2 break-words text-[13px] leading-6 text-[var(--text-muted)]",
    }, note.summary) : null,
    h("p", { className: "mt-1 text-[12px] leading-5 text-[var(--text-subtle)]" },
      note.reasons.map((reason) => reasonLabels[reason]).join(" · ")),
    )),
  ));
}
