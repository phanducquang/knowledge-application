"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { KnowledgeListItem } from "@/components/knowledge/knowledge-list-item";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeSearchProps {
  items: KnowledgeListItemData[];
  initialQuery?: string;
}

const suggestedQueries = ["Spring Boot", "Redis", "Elasticsearch", "Nginx"];

function scoreItem(item: KnowledgeListItemData, query: string) {
  const normalizedQuery = query.toLowerCase();
  const title = item.title.toLowerCase();
  const description = item.description.toLowerCase();
  const collection = item.collection.toLowerCase();
  const tags = item.tags.map((tag) => tag.toLowerCase());

  let score = 0;

  if (title === normalizedQuery) score += 12;
  else if (title.startsWith(normalizedQuery)) score += 8;
  else if (title.includes(normalizedQuery)) score += 6;

  if (collection === normalizedQuery) score += 6;
  else if (collection.includes(normalizedQuery)) score += 3;

  for (const tag of tags) {
    if (tag === normalizedQuery) score += 6;
    else if (tag.includes(normalizedQuery)) score += 3;
  }

  if (description.includes(normalizedQuery)) score += 2;

  return score;
}

function searchItems(items: KnowledgeListItemData[], query: string) {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) {
    return [];
  }

  return items
    .map((item) => ({ item, score: scoreItem(item, normalizedQuery) }))
    .filter(({ score }) => score > 0)
    .sort((a, b) => b.score - a.score || b.item.updatedAtIso.localeCompare(a.item.updatedAtIso))
    .map(({ item }) => item);
}

export function KnowledgeSearch({ items, initialQuery = "" }: KnowledgeSearchProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState(initialQuery);
  const [committedQuery, setCommittedQuery] = useState(initialQuery.trim());
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const trimmedQuery = query.trim();

    if (!trimmedQuery) {
      setCommittedQuery("");
      setSearching(false);
      return;
    }

    if (trimmedQuery === committedQuery) {
      setSearching(false);
      return;
    }

    setSearching(true);
    const timeout = window.setTimeout(() => {
      setCommittedQuery(trimmedQuery);
      setSearching(false);
    }, 140);

    return () => window.clearTimeout(timeout);
  }, [query, committedQuery]);

  useEffect(() => {
    const url = new URL(window.location.href);
    const trimmedQuery = query.trim();

    if (trimmedQuery) {
      url.searchParams.set("q", trimmedQuery);
    } else {
      url.searchParams.delete("q");
    }

    window.history.replaceState(window.history.state, "", url);
  }, [query]);

  const results = useMemo(() => searchItems(items, committedQuery), [items, committedQuery]);
  const hasQuery = query.trim().length > 0;

  const resultLinks = () =>
    Array.from(resultsRef.current?.querySelectorAll<HTMLAnchorElement>("article a") ?? []);

  const focusFirstResult = () => {
    resultLinks()[0]?.focus();
  };

  const handleResultsKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!(event.target instanceof HTMLAnchorElement)) {
      return;
    }

    const links = resultLinks();
    const currentIndex = links.indexOf(event.target);
    if (currentIndex < 0) {
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      links[Math.min(currentIndex + 1, links.length - 1)]?.focus();
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      if (currentIndex === 0) {
        inputRef.current?.focus();
      } else {
        links[currentIndex - 1]?.focus();
      }
    }

    if (event.key === "Escape") {
      event.preventDefault();
      inputRef.current?.focus();
    }
  };

  const applySuggestion = (value: string) => {
    setQuery(value);
    inputRef.current?.focus();
  };

  return (
    <div>
      <form
        role="search"
        onSubmit={(event) => event.preventDefault()}
        className="mx-auto w-full max-w-[980px]"
      >
        <label className="block" htmlFor="knowledge-search-page">
          <span className="sr-only">Search knowledge</span>
          <input
            ref={inputRef}
            id="knowledge-search-page"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "ArrowDown" && !searching && results.length > 0) {
                event.preventDefault();
                focusFirstResult();
              }

              if (event.key === "Escape" && query) {
                event.preventDefault();
                setQuery("");
              }
            }}
            autoFocus
            autoComplete="off"
            enterKeyHint="search"
            placeholder="Search notes, tags, or collections"
            className="h-12 w-full border border-[var(--border-strong)] bg-[var(--surface)] px-4 text-[16px] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--accent-muted)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]"
          />
        </label>

        <div className="mt-2 flex flex-wrap items-center justify-between gap-x-6 gap-y-1 text-[11px] text-[var(--text-subtle)]">
          <span>Titles, summaries, collections, and tags</span>
          <span className="hidden sm:inline">↓ results · ↑ back · Enter open · Esc search</span>
        </div>
      </form>

      {!hasQuery && (
        <section
          className="mx-auto flex min-h-[340px] max-w-[980px] items-center justify-center py-12 text-center"
          aria-label="Search guidance"
        >
          <div className="max-w-[520px]">
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">
              Search knowledge
            </p>
            <h2 className="mt-3 text-[22px] font-medium tracking-[-0.025em] text-[var(--text)] sm:text-[24px]">
              Find the note you need.
            </h2>
            <p className="mx-auto mt-3 max-w-[460px] text-[14px] leading-6 text-[var(--text-muted)]">
              Search across note titles, summaries, collections, and tags. Start with a technology, topic, or phrase you remember.
            </p>

            <div className="mt-6 flex flex-wrap justify-center gap-x-4 gap-y-2" aria-label="Suggested searches">
              {suggestedQueries.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => applySuggestion(suggestion)}
                  className="border-b border-[var(--border-strong)] pb-0.5 text-[13px] text-[var(--accent-strong)] transition-colors hover:border-[var(--accent)] hover:text-[var(--accent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[var(--accent)]"
                >
                  {suggestion}
                </button>
              ))}
            </div>

            <p className="mt-7 text-[11px] tabular-nums text-[var(--text-subtle)]">{items.length} notes available</p>
          </div>
        </section>
      )}

      {hasQuery && searching && (
        <section
          className="mx-auto flex min-h-[300px] max-w-[980px] items-center justify-center py-12 text-center"
          aria-label="Searching"
          aria-live="polite"
        >
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">Searching</p>
            <p className="mt-3 text-[16px] text-[var(--text-muted)]">
              Looking for <span className="font-medium text-[var(--text)]">“{query.trim()}”</span>…
            </p>
          </div>
        </section>
      )}

      {hasQuery && !searching && results.length === 0 && (
        <section
          className="mx-auto flex min-h-[340px] max-w-[980px] items-center justify-center py-12 text-center"
          aria-label="No search results"
        >
          <div className="max-w-[520px]">
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">No matches</p>
            <h2 className="mt-3 text-[21px] font-medium tracking-[-0.02em] text-[var(--text)] sm:text-[23px]">
              Nothing found for “{committedQuery}”.
            </h2>
            <p className="mx-auto mt-3 max-w-[450px] text-[14px] leading-6 text-[var(--text-muted)]">
              Try a shorter phrase, a collection name, or a tag. You can also start again with one of the common technical topics below.
            </p>

            <div className="mt-6 flex flex-wrap justify-center gap-x-4 gap-y-2" aria-label="Alternative searches">
              {suggestedQueries.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => applySuggestion(suggestion)}
                  className="border-b border-[var(--border-strong)] pb-0.5 text-[13px] text-[var(--accent-strong)] transition-colors hover:border-[var(--accent)] hover:text-[var(--accent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[var(--accent)]"
                >
                  {suggestion}
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={() => {
                setQuery("");
                inputRef.current?.focus();
              }}
              className="mt-7 text-[12px] text-[var(--text-muted)] underline decoration-[var(--border-strong)] underline-offset-4 transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[var(--accent)]"
            >
              Clear search
            </button>
          </div>
        </section>
      )}

      {hasQuery && !searching && results.length > 0 && (
        <section className="mx-auto mt-9 w-full max-w-[980px]" aria-labelledby="search-results-heading">
          <div className="mb-4 flex items-baseline justify-between gap-4 border-b border-[var(--border)] pb-3">
            <h2 id="search-results-heading" className="text-[13px] font-medium text-[var(--text-muted)]">
              Search results
            </h2>
            <span className="text-[12px] tabular-nums text-[var(--text-subtle)]" aria-live="polite">
              {results.length} {results.length === 1 ? "note" : "notes"}
            </span>
          </div>

          <div ref={resultsRef} onKeyDown={handleResultsKeyDown}>
            {results.map((item) => (
              <KnowledgeListItem key={item.id} item={item} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
