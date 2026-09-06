"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { KnowledgeListItem } from "@/components/knowledge/knowledge-list-item";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeSearchProps {
  items: KnowledgeListItemData[];
  initialQuery?: string;
}

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

    setSearching(true);
    const timeout = window.setTimeout(() => {
      setCommittedQuery(trimmedQuery);
      setSearching(false);
    }, 140);

    return () => window.clearTimeout(timeout);
  }, [query]);

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

  const handleResultsKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
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

  return (
    <div>
      <form
        role="search"
        onSubmit={(event) => event.preventDefault()}
        className="max-w-[720px]"
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
            className="h-11 w-full border border-[var(--border-strong)] bg-[var(--surface)] px-3 text-[15px] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--accent-muted)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]"
          />
        </label>

        <div className="mt-2 flex flex-wrap items-center justify-between gap-x-6 gap-y-1 text-[11px] text-[var(--text-subtle)]">
          <span>Titles, summaries, collections, and tags</span>
          <span className="hidden sm:inline">↓ results · ↑ back · Enter open · Esc search</span>
        </div>
      </form>

      <section className="mt-9" aria-labelledby="search-results-heading" aria-busy={searching}>
        <div className="mb-4 flex items-baseline justify-between gap-4 border-b border-[var(--border)] pb-3">
          <h2 id="search-results-heading" className="text-[13px] font-medium text-[var(--text-muted)]">
            Search results
          </h2>
          <span className="text-[12px] tabular-nums text-[var(--text-subtle)]" aria-live="polite">
            {searching
              ? "Searching…"
              : hasQuery
                ? `${results.length} ${results.length === 1 ? "note" : "notes"}`
                : `${items.length} notes available`}
          </span>
        </div>

        {!hasQuery && (
          <div className="border-b border-[var(--border)] py-8 sm:py-10">
            <p className="text-[15px] font-medium text-[var(--text)]">Search your technical knowledge.</p>
            <p className="mt-2 max-w-xl text-[13px] leading-6 text-[var(--text-muted)]">
              Start with a technology, topic, collection, or phrase from a note. Try Spring Boot, Redis, Elasticsearch, or Nginx.
            </p>
          </div>
        )}

        {hasQuery && searching && (
          <div className="border-b border-[var(--border)] py-8 text-[13px] text-[var(--text-muted)]">
            Searching for <span className="font-medium text-[var(--text)]">“{query.trim()}”</span>…
          </div>
        )}

        {hasQuery && !searching && results.length === 0 && (
          <div className="border-b border-[var(--border)] py-8 sm:py-10">
            <p className="text-[15px] font-medium text-[var(--text)]">
              No notes found for “{committedQuery}”.
            </p>
            <p className="mt-2 text-[13px] leading-6 text-[var(--text-muted)]">
              Try fewer words, a collection name, or one of the tags attached to the note.
            </p>
          </div>
        )}

        {hasQuery && !searching && results.length > 0 && (
          <div ref={resultsRef} onKeyDown={handleResultsKeyDown}>
            {results.map((item) => (
              <KnowledgeListItem key={item.id} item={item} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
