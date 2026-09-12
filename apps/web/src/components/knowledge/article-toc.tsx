import type { MarkdownHeading } from "@/lib/markdown";

function TocLinks({ sections }: { sections: MarkdownHeading[] }) {
  return (
    <nav aria-label="On this page" className="space-y-2">
      {sections.map((section) => (
        <a
          key={section.id}
          href={`#${section.id}`}
          className={`block border-l border-[var(--border-strong)] text-[12px] leading-5 text-[var(--text-muted)] transition-colors hover:border-[var(--accent)] hover:text-[var(--accent-strong)] ${section.level === 2 ? "pl-3" : section.level === 3 ? "pl-5" : "pl-7"}`}
        >
          {section.label}
        </a>
      ))}
    </nav>
  );
}

interface ArticleTocProps {
  variant: "inline" | "aside";
  sections: MarkdownHeading[];
}

export function ArticleToc({ variant, sections }: ArticleTocProps) {
  if (sections.length === 0) {
    return null;
  }

  if (variant === "inline") {
    return (
      <details className="mb-8 border-y border-[var(--border)] py-3 xl:hidden">
        <summary className="cursor-pointer text-[12px] font-medium text-[var(--text-muted)]">On this page</summary>
        <div className="mt-3">
          <TocLinks sections={sections} />
        </div>
      </details>
    );
  }

  return (
    <aside className="hidden xl:block" aria-label="Table of contents">
      <div className="sticky top-8">
        <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">On this page</p>
        <TocLinks sections={sections} />
      </div>
    </aside>
  );
}
