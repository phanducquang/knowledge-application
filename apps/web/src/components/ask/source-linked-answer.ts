"use client";
import { createElement } from "react";
import { askSourceHref, type AskAnswerData, type AskCitation } from "../../lib/ask-knowledge.ts";
import { AskAnswer } from "./ask-answer.ts";

export function citationEvidenceId(id: string) { return `ask-evidence-${id}`; }
/** A real button focuses its evidence without altering URLs or interpreting generated Markdown labels. */
export function focusCitationEvidence(id: string, root: Pick<Document, "getElementById"> = document) {
  const target = root.getElementById(citationEvidenceId(id));
  target?.focus(); target?.scrollIntoView({ block: "nearest", behavior: "auto" });
}
export function SourceLinkedAnswer({ answer, citations }: { answer: AskAnswerData; citations: AskCitation[] }) {
  const byId = new Map(citations.map(citation => [citation.id, citation]));
  const groups = new Map<number, { source: AskCitation["source"]; items: AskCitation[] }>();
  for (const citation of citations) {
    const group = groups.get(citation.source.id) ?? { source: citation.source, items: [] };
    group.items.push(citation); groups.set(citation.source.id, group);
  }
  return createElement("div", { className: "border-t border-[var(--border)] pt-6" },
    createElement("section", { "aria-labelledby": "ask-answer-heading" },
      createElement("h2", { id: "ask-answer-heading", className: "text-[20px] font-semibold tracking-[-0.02em]" }, "Answer"),
      ...answer.blocks.map((block, index) => createElement("div", { key: index, className: "my-5 min-w-0" },
        createElement(AskAnswer, { answer: block.markdown }),
        createElement("div", { className: "flex flex-wrap gap-2", role: "group", "aria-label": `Citations for answer block ${index + 1}` },
          ...block.citationIds.map(id => {
            const citation = byId.get(id)!;
            return createElement("button", { key: id, type: "button", "aria-label": `Source ${id.slice(1)}: ${citation.source.title}`,
              "aria-controls": citationEvidenceId(id), onClick: () => focusCitationEvidence(id),
              className: "min-h-8 min-w-8 rounded-[4px] px-1 text-[13px] text-[var(--accent-strong)] underline underline-offset-4 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]" }, `[${id.slice(1)}]`);
          }))))),
    createElement("section", { className: "mt-8 border-t border-[var(--border)] pt-5", "aria-labelledby": "ask-sources-heading" },
      createElement("h2", { id: "ask-sources-heading", className: "text-[18px] font-medium" }, "Sources / Evidence"),
      createElement("p", { className: "mt-2 text-[12px] leading-5 text-[var(--text-subtle)]" }, "Evidence exists in the retrieved current snapshot. Citations do not prove a claim is correct; notes can change afterward."),
      createElement("ul", { className: "mt-4 divide-y divide-[var(--border)]" },
        ...[...groups.values()].map(group => createElement("li", { key: group.source.id, className: "py-4 min-w-0 [overflow-wrap:anywhere]" },
          createElement("h3", { className: "text-[15px] font-medium" }, group.source.title),
          createElement("a", { href: askSourceHref(group.source), className: "mt-1 inline-block py-2 text-[13px] text-[var(--accent-strong)] underline underline-offset-4 focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--accent)]", "aria-label": `Open note: ${group.source.title}` }, "Open note"),
          ...group.items.map(citation => createElement("div", { key: citation.id, id: citationEvidenceId(citation.id), tabIndex: -1, role: "group",
            "aria-label": `Evidence ${citation.id.slice(1)}: ${group.source.title}, chunk ${citation.chunkIndex + 1}`,
            className: "my-3 min-w-0 border-l-2 border-[var(--border)] pl-4 focus:border-[var(--accent)] focus:outline-none" },
            createElement("p", { className: "text-[11px] font-medium text-[var(--text-subtle)]" }, `[${citation.id.slice(1)}] · Chunk ${citation.chunkIndex + 1}`),
            createElement("p", { className: "mt-2 whitespace-pre-wrap text-[13px] leading-6 text-[var(--text-muted)] [overflow-wrap:anywhere]" }, citation.evidence))))))));
}
