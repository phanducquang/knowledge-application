import { Children, isValidElement, type ReactNode } from "react";
import ReactMarkdown, { defaultUrlTransform } from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  accessibleImageAlt,
  attachmentIdFromReference,
  resolveImageSource,
  type ImageAccessContext,
} from "@/lib/attachment-reference";
import { createHeadingSlugger } from "@/lib/markdown";

function textFromChildren(children: ReactNode): string {
  return Children.toArray(children)
    .map((child) => {
      if (typeof child === "string" || typeof child === "number") {
        return String(child);
      }
      if (isValidElement<{ children?: ReactNode }>(child)) {
        return textFromChildren(child.props.children);
      }
      return "";
    })
    .join("");
}

export function KnowledgeMarkdown({
  markdown,
  imageContext,
}: {
  markdown: string;
  imageContext: ImageAccessContext;
}) {
  const nextHeadingId = createHeadingSlugger();
  const heading = (level: 2 | 3 | 4, children: ReactNode) => {
    const id = nextHeadingId(textFromChildren(children));
    const className =
      level === 2
        ? "mb-4 mt-10 scroll-mt-8 text-[22px] font-semibold leading-tight tracking-[-0.02em] first:mt-0"
        : level === 3
          ? "mb-3 mt-7 scroll-mt-8 text-[17px] font-semibold tracking-[-0.01em]"
          : "mb-2 mt-6 scroll-mt-8 text-[15px] font-semibold tracking-[-0.005em]";
    const TagName = `h${level}` as "h2" | "h3" | "h4";
    return <TagName id={id} className={className}>{children}</TagName>;
  };

  return (
    <div className="article-content text-[16px] leading-[1.72] text-[var(--text)]">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        urlTransform={(url, key) => {
          if (key === "src" && attachmentIdFromReference(url)) {
            return resolveImageSource(url, imageContext);
          }
          return defaultUrlTransform(url);
        }}
        components={{
          h1: ({ children }) => (
            <h2 className="mb-4 mt-10 text-[22px] font-semibold leading-tight tracking-[-0.02em] first:mt-0">
              {children}
            </h2>
          ),
          h2: ({ children }) => heading(2, children),
          h3: ({ children }) => heading(3, children),
          h4: ({ children }) => heading(4, children),
          h5: ({ children }) => heading(4, children),
          h6: ({ children }) => heading(4, children),
          p: ({ children }) => <p className="my-4 text-[var(--text-muted)]">{children}</p>,
          img: ({ src, alt, title }) => (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={src}
              alt={accessibleImageAlt(alt, title)}
              title={title}
              loading="lazy"
              className="my-6 h-auto max-w-full border border-[var(--border)] bg-[var(--surface-muted)]"
            />
          ),
          a: ({ children, href }) => (
            <a
              href={href}
              className="text-[var(--accent-strong)] underline decoration-[var(--accent-muted)] underline-offset-3 hover:text-[var(--accent)]"
            >
              {children}
            </a>
          ),
          blockquote: ({ children }) => (
            <blockquote className="my-6 border-l-2 border-[var(--accent)] pl-4 text-[15px] leading-7 text-[var(--text-muted)]">
              {children}
            </blockquote>
          ),
          ul: ({ children }) => (
            <ul className="my-4 list-disc space-y-2 pl-5 text-[var(--text-muted)] marker:text-[var(--accent-muted)]">{children}</ul>
          ),
          ol: ({ children }) => (
            <ol className="my-4 list-decimal space-y-2 pl-5 text-[var(--text-muted)] marker:font-medium marker:text-[var(--accent-strong)]">{children}</ol>
          ),
          pre: ({ children }) => (
            <pre className="my-5 overflow-x-auto border-y border-[var(--border-strong)] bg-[var(--surface-muted)] px-4 py-4 text-[13px] leading-6 text-[var(--text)]">{children}</pre>
          ),
          code: ({ children, className }) => (
            <code className={className ?? "rounded-[3px] bg-[var(--surface-muted)] px-1 py-0.5 text-[0.88em] text-[var(--accent-strong)]"}>{children}</code>
          ),
          table: ({ children }) => (
            <div className="my-6 overflow-x-auto">
              <table className="w-full min-w-[560px] border-collapse text-left text-[13px] leading-5">{children}</table>
            </div>
          ),
          thead: ({ children }) => <thead className="border-y border-[var(--border-strong)] text-[11px] uppercase tracking-[0.08em] text-[var(--text-subtle)]">{children}</thead>,
          th: ({ children }) => <th className="py-2 pr-5 font-semibold">{children}</th>,
          tr: ({ children }) => <tr className="border-b border-[var(--border)]">{children}</tr>,
          td: ({ children }) => <td className="py-3 pr-5 text-[var(--text-muted)]">{children}</td>,
          hr: () => <hr className="my-8 border-0 border-t border-[var(--border-strong)]" />,
        }}
      >
        {markdown}
      </ReactMarkdown>
    </div>
  );
}
