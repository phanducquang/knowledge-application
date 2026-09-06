const sections = [
  { href: "#problem", label: "Problem" },
  { href: "#timeout-placement", label: "Timeout placement" },
  { href: "#error-boundaries", label: "Error boundaries" },
  { href: "#decision", label: "Decision" },
];

function TocLinks() {
  return (
    <nav aria-label="On this page" className="space-y-2">
      {sections.map((section) => (
        <a
          key={section.href}
          href={section.href}
          className="block border-l border-[var(--border-strong)] pl-3 text-[12px] leading-5 text-[var(--text-muted)] transition-colors hover:border-[var(--accent)] hover:text-[var(--accent-strong)]"
        >
          {section.label}
        </a>
      ))}
    </nav>
  );
}

interface ArticleTocProps {
  variant: "inline" | "aside";
}

export function ArticleToc({ variant }: ArticleTocProps) {
  if (variant === "inline") {
    return (
      <details className="mb-8 border-y border-[var(--border)] py-3 xl:hidden">
        <summary className="cursor-pointer text-[12px] font-medium text-[var(--text-muted)]">On this page</summary>
        <div className="mt-3">
          <TocLinks />
        </div>
      </details>
    );
  }

  return (
    <aside className="hidden xl:block" aria-label="Table of contents">
      <div className="sticky top-8">
        <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">On this page</p>
        <TocLinks />
      </div>
    </aside>
  );
}
