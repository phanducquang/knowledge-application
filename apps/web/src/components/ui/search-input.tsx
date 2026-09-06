export function SearchInput() {
  return (
    <label className="block" htmlFor="knowledge-search">
      <span className="sr-only">Search knowledge</span>
      <input
        id="knowledge-search"
        type="search"
        placeholder="Search notes, tags, or collections"
        className="h-10 w-full border border-[var(--border)] bg-[var(--surface)] px-3 text-[14px] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]"
      />
    </label>
  );
}
