export function SearchInput() {
  return (
    <label className="block" htmlFor="knowledge-search">
      <span className="sr-only">Search knowledge</span>
      <input
        id="knowledge-search"
        type="search"
        placeholder="Search notes, tags, or collections"
        className="h-10 w-full border border-[var(--border-strong)] bg-[var(--surface)] px-3 text-[14px] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--accent-muted)] focus:border-[var(--accent)] focus:bg-[var(--surface)] focus:ring-1 focus:ring-[var(--accent)]"
      />
    </label>
  );
}
