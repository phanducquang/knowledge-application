import { Children, isValidElement, type ReactNode } from "react";
import type { RootContent } from "hast";
import { highlightCode } from "@/lib/syntax-highlighting";

function sourceText(children: ReactNode) {
  return Children.toArray(children)
    .map((child) => (typeof child === "string" || typeof child === "number" ? String(child) : ""))
    .join("");
}

function renderHighlightedNode(node: RootContent, key: string): ReactNode {
  if (node.type === "text") return node.value;
  if (node.type !== "element" || node.tagName !== "span") return null;

  const className = Array.isArray(node.properties.className)
    ? node.properties.className.join(" ")
    : undefined;
  return (
    <span key={key} className={className}>
      {node.children.map((child, index) => renderHighlightedNode(child, `${key}-${index}`))}
    </span>
  );
}

export function KnowledgeCodeBlock({ children }: { children?: ReactNode }) {
  const code = Children.only(children);
  if (!isValidElement<{ className?: string; children?: ReactNode }>(code)) {
    return <pre className="my-5 max-w-full overflow-x-auto border-y border-[var(--border-strong)] bg-[var(--surface-muted)] px-4 py-4 text-[13px] leading-6 text-[var(--text)]">{children}</pre>;
  }

  const source = sourceText(code.props.children);
  const tree = highlightCode(source, code.props.className);

  return (
    <pre className="my-5 max-w-full overflow-x-auto border-y border-[var(--border-strong)] bg-[var(--surface-muted)] px-4 py-4 text-[13px] leading-6 text-[var(--text)]">
      <code className={tree ? `hljs ${code.props.className ?? ""}`.trim() : code.props.className}>
        {tree
          ? tree.children.map((node, index) => renderHighlightedNode(node, `token-${index}`))
          : source}
      </code>
    </pre>
  );
}
