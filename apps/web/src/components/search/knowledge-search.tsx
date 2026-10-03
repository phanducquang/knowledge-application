"use client";

import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { KnowledgeListItem } from "@/components/knowledge/knowledge-list-item";
import { useKnowledgeSearch } from "@/hooks/use-knowledge-search";
import { FULL_SEARCH_LIMIT } from "@/lib/knowledge-search";
import { useSemanticKnowledgeSearch } from "@/hooks/use-semantic-knowledge-search";
import { semanticSearchHref, type SearchMode, type SemanticErrorCode, type SemanticKnowledgeResult } from "@/lib/knowledge-semantic-search";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeSearchProps {
  availableCount: number;
  initialQuery?: string;
  initialResults?: KnowledgeListItemData[];
  initialSearchFailed?: boolean;
  initialMode?: SearchMode;
  initialSemanticResults?: SemanticKnowledgeResult[];
  initialSemanticError?: SemanticErrorCode;
}

const suggestedQueries = ["Spring Boot", "Redis", "Elasticsearch", "Nginx"];

export function KnowledgeSearch({
  availableCount,
  initialQuery = "",
  initialResults = [],
  initialSearchFailed = false,
  initialMode = "keyword",
  initialSemanticResults = [],
  initialSemanticError,
}: KnowledgeSearchProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const resultsRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState(initialQuery);
  const [mode, setMode] = useState<SearchMode>(initialMode);
  const searchState = useKnowledgeSearch(FULL_SEARCH_LIMIT, {
    query: initialMode === "keyword" ? initialQuery : "",
    results: initialResults,
    failed: initialSearchFailed,
  });
  const semantic = useSemanticKnowledgeSearch({
    draft: initialQuery,
    submittedQuery: initialMode === "semantic" ? initialQuery.trim() : "",
    results: initialSemanticResults,
    status: initialMode === "semantic" && initialQuery.trim() ? (initialSemanticError ? "error" : "success") : "idle",
    error: initialSemanticError,
  });

  useEffect(() => {
    if (mode !== "keyword") return;
    const url = new URL(window.location.href);
    url.searchParams.delete("mode");
    const trimmedQuery = query.trim();

    if (trimmedQuery) {
      url.searchParams.set("q", trimmedQuery);
    } else {
      url.searchParams.delete("q");
    }

    window.history.replaceState(window.history.state, "", url);
  }, [query, mode]);

  const hasQuery = query.trim().length > 0;
  const searching = mode === "keyword" ? searchState.status === "searching" : semantic.status === "searching";
  const results = mode === "keyword" ? searchState.results : semantic.results;

  const resultLinks = () =>
    Array.from(resultsRef.current?.querySelectorAll<HTMLAnchorElement>("article a") ?? []);

  const focusFirstResult = () => {
    resultLinks()[0]?.focus();
  };

  const changeQuery = (nextQuery: string) => {
    setQuery(nextQuery);
    if (mode === "keyword") searchState.search(nextQuery);
    else semantic.session.draft(nextQuery);
  };

  const switchMode = (next: SearchMode) => {
    if (next === mode) return;
    setMode(next);
    if (next === "semantic") {
      searchState.search("");
      semantic.session.clear();
      semantic.session.draft(query);
      window.history.replaceState(window.history.state, "", semanticSearchHref(""));
    } else {
      semantic.session.clear();
      searchState.search(query);
    }
    inputRef.current?.focus();
  };

  const submitSemantic = () => {
    if (!query.trim() || query.length > 200) return;
    semantic.session.draft(query);
    void semantic.session.submit(FULL_SEARCH_LIMIT);
    window.history.replaceState(window.history.state, "", semanticSearchHref(query));
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
    changeQuery(value);
    inputRef.current?.focus();
  };

  return (
    <div>
      <form
        role="search"
        onSubmit={(event) => { event.preventDefault(); if (mode === "semantic") submitSemantic(); }}
        className="mx-auto w-full max-w-[980px]"
      >
        <div role="group" aria-label="Search mode" className="mb-4 flex gap-5 border-b border-[var(--border)]">
          {(["keyword", "semantic"] as const).map(value => (
            <button key={value} type="button" aria-pressed={mode === value} onClick={() => switchMode(value)}
              className={`border-b-2 px-1 pb-2 text-[13px] capitalize focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${mode === value ? "border-[var(--accent)] text-[var(--accent-strong)]" : "border-transparent text-[var(--text-muted)] hover:text-[var(--text)]"}`}>
              {value === "keyword" ? "Keyword" : "Semantic"}
            </button>
          ))}
        </div>
        <div className="flex min-w-0 gap-2">
        <label className="block min-w-0 flex-1" htmlFor="knowledge-search-page">
          <span className="sr-only">Search knowledge</span>
          <input
            ref={inputRef}
            id="knowledge-search-page"
            type="search"
            maxLength={mode === "semantic" ? 200 : undefined}
            value={query}
            onChange={(event) => changeQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "ArrowDown" && !searching && results.length > 0) {
                event.preventDefault();
                focusFirstResult();
              }

              if (event.key === "Escape" && query) {
                event.preventDefault();
                changeQuery("");
              }
            }}
            autoFocus
            autoComplete="off"
            enterKeyHint="search"
            placeholder={mode === "keyword" ? "Search notes, tags, or collections" : "Describe what you want to find"}
            className="h-12 w-full border border-[var(--border-strong)] bg-[var(--surface)] px-4 text-[16px] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--accent-muted)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]"
          />
        </label>
        {mode === "semantic" && <button type="submit" disabled={!query.trim() || (searching && query.trim() === semantic.submittedQuery)}
          className="shrink-0 border border-[var(--border-strong)] bg-[var(--surface)] px-3 text-[13px] text-[var(--accent-strong)] hover:border-[var(--accent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--accent)] disabled:cursor-default disabled:text-[var(--text-subtle)] sm:px-4">Search</button>}
        </div>

        <div className="mt-2 flex flex-wrap items-center justify-between gap-x-6 gap-y-1 text-[11px] text-[var(--text-subtle)]">
          <span>{mode === "keyword" ? "Titles, summaries, collections, and tags" : "Submit to search by meaning · Query sent to your configured provider"}</span>
          <span className="hidden sm:inline">{mode === "semantic" ? "Enter search · ↓ results · Esc clear draft" : "↓ results · ↑ back · Enter open · Esc search"}</span>
        </div>
      </form>

      {mode === "semantic" && <div aria-live="polite">
        {semantic.status === "idle" && <section className="mx-auto max-w-[980px] py-16 text-center" aria-label="Semantic search guidance">
          <h2 className="text-[22px] font-medium tracking-[-0.025em]">Find notes by meaning.</h2>
          <p className="mt-3 text-[14px] leading-6 text-[var(--text-muted)]">Describe a problem or idea, then press Enter or Search.</p>
          <p className="mt-3 text-[12px] text-[var(--text-subtle)]">Typing does not send a request. {availableCount} notes available.</p>
        </section>}
        {semantic.status === "searching" && <section className="mx-auto max-w-[980px] py-16 text-center" aria-label="Semantic searching" aria-busy="true">
          <p className="text-[16px] text-[var(--text-muted)]">Searching by meaning…</p>
          <p className="mt-3 break-words text-[13px] [overflow-wrap:anywhere]">“{semantic.submittedQuery}”</p>
        </section>}
        {semantic.status === "error" && <section className="mx-auto max-w-[980px] py-16 text-center" aria-label="Semantic search unavailable">
          <h2 className="text-[21px] font-medium tracking-[-0.02em]">{semantic.error === "SEMANTIC_SEARCH_DISABLED" ? "Semantic search is not configured." : semantic.error === "VALIDATION_ERROR" ? "Enter a query of up to 200 characters." : semantic.error === "UNAUTHENTICATED" || semantic.error === "ACCESS_DENIED" ? "Sign in again to search your workspace." : "Semantic search is temporarily unavailable."}</h2>
          <p className="mt-3 text-[14px] text-[var(--text-muted)]">Your notes are unaffected. Keyword search is still available.</p>
          <div className="mt-6 flex flex-wrap justify-center gap-6 text-[13px] text-[var(--accent-strong)]">
            {semantic.error !== "SEMANTIC_SEARCH_DISABLED" && <button type="button" onClick={submitSemantic} className="underline underline-offset-4">Try again</button>}
            <button type="button" onClick={() => switchMode("keyword")} className="underline underline-offset-4">Use keyword search</button>
          </div>
        </section>}
        {semantic.status === "success" && semantic.results.length === 0 && <section className="mx-auto max-w-[980px] py-16 text-center" aria-label="Semantic index not ready">
          <h2 className="text-[21px] font-medium">{availableCount ? "Semantic index is not ready yet." : "Your library is empty."}</h2>
          <p className="mt-3 text-[14px] text-[var(--text-muted)]">{availableCount ? "Current notes may still be waiting for background indexing." : "Create a note before searching by meaning."}</p>
          <button type="button" onClick={() => switchMode("keyword")} className="mt-6 text-[13px] text-[var(--accent-strong)] underline underline-offset-4">Use keyword search</button>
        </section>}
      </div>}

      {mode === "keyword" && !hasQuery && (
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

            <p className="mt-7 text-[11px] tabular-nums text-[var(--text-subtle)]">{availableCount} notes available</p>
          </div>
        </section>
      )}

      {mode === "keyword" && hasQuery && searching && (
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

      {mode === "keyword" && hasQuery && searchState.status === "error" && (
        <section
          className="mx-auto flex min-h-[340px] max-w-[980px] items-center justify-center py-12 text-center"
          aria-label="Search unavailable"
          aria-live="polite"
        >
          <div className="max-w-[520px]">
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">Search unavailable</p>
            <h2 className="mt-3 text-[21px] font-medium tracking-[-0.02em] text-[var(--text)] sm:text-[23px]">
              We couldn&apos;t search right now.
            </h2>
            <p className="mx-auto mt-3 max-w-[450px] text-[14px] leading-6 text-[var(--text-muted)]">
              Keep your query in place and try again in a moment.
            </p>
            <button
              type="button"
              onClick={() => searchState.search(query)}
              className="mt-6 text-[12px] text-[var(--accent-strong)] underline decoration-[var(--border-strong)] underline-offset-4"
            >
              Try again
            </button>
          </div>
        </section>
      )}

      {mode === "keyword" && hasQuery && searchState.status === "success" && results.length === 0 && (
        <section
          className="mx-auto flex min-h-[340px] max-w-[980px] items-center justify-center py-12 text-center"
          aria-label="No search results"
        >
          <div className="max-w-[520px]">
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">No matches</p>
            <h2 className="mt-3 text-[21px] font-medium tracking-[-0.02em] text-[var(--text)] sm:text-[23px]">
              Nothing found for “{searchState.query}”.
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
                changeQuery("");
                inputRef.current?.focus();
              }}
              className="mt-7 text-[12px] text-[var(--text-muted)] underline decoration-[var(--border-strong)] underline-offset-4 transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[var(--accent)]"
            >
              Clear search
            </button>
          </div>
        </section>
      )}

      {(mode === "keyword" ? hasQuery && searchState.status === "success" : semantic.status === "success") && results.length > 0 && (
        <section className="mx-auto mt-9 w-full max-w-[980px]" aria-labelledby="search-results-heading">
          <div className="flex items-baseline justify-between gap-4 border-b border-[var(--border)] pb-3">
            <h2 id="search-results-heading" className="min-w-0 flex-1 text-[13px] font-medium text-[var(--text-muted)] [overflow-wrap:anywhere]">
              {mode === "keyword" ? "Search results" : `Semantic results for “${semantic.submittedQuery}”`}
            </h2>
            <span className="shrink-0 text-[12px] tabular-nums text-[var(--text-subtle)]" aria-live="polite">
              {results.length} {results.length === 1 ? "note" : "notes"}
            </span>
          </div>

          <div ref={resultsRef} onKeyDown={handleResultsKeyDown}>
            {results.map((item) => (
              <KnowledgeListItem key={item.id} item={item} matchText={mode === "semantic" ? (item as SemanticKnowledgeResult).match.text : undefined} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
