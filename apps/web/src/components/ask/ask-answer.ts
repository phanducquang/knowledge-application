import { createElement } from "react";
import ReactMarkdown from "react-markdown";

/** Generated output is NOT persisted Knowledge: no HTML/images/active links/wiki resolution/Mermaid. */
export function AskAnswer({ answer }: { answer: string }) {
  return createElement("div", { className: "article-content min-w-0 text-[16px] leading-[1.72] text-[var(--text)] [overflow-wrap:anywhere]" },
    createElement(ReactMarkdown, {
      skipHtml: true, allowedElements: ["p", "ul", "ol", "li", "strong", "em", "code", "pre", "br"], unwrapDisallowed: true,
      components: {
        p: ({ children }) => createElement("p", { className: "my-4 text-[var(--text-muted)]" }, children),
        ul: ({ children }) => createElement("ul", { className: "my-4 list-disc space-y-2 pl-5 text-[var(--text-muted)]" }, children),
        ol: ({ children }) => createElement("ol", { className: "my-4 list-decimal space-y-2 pl-5 text-[var(--text-muted)]" }, children),
        pre: ({ children }) => createElement("pre", { className: "my-5 max-w-full overflow-x-auto border border-[var(--border)] bg-[var(--surface-muted)] p-4 text-[13px]" }, children),
        code: ({ children }) => createElement("code", { className: "font-mono text-[0.88em]" }, children),
      },
    }, answer));
}
