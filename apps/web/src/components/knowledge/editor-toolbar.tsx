const tools = ["H2", "Bold", "Italic", "Code", "Link", "Quote"];

export function EditorToolbar() {
  return (
    <div className="flex flex-wrap items-center gap-1 border-y border-[var(--border)] py-2" aria-label="Markdown formatting tools">
      {tools.map((tool) => (
        <button
          key={tool}
          type="button"
          className="inline-flex min-h-8 items-center px-2.5 text-[12px] font-medium text-[var(--text-muted)] transition-colors hover:bg-[var(--accent-soft)] hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          {tool}
        </button>
      ))}
      <span className="ml-auto hidden text-[11px] text-[var(--text-subtle)] sm:block">Markdown</span>
    </div>
  );
}
