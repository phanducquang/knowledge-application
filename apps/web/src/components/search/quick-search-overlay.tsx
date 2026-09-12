"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { useKnowledgeSearch } from "@/hooks/use-knowledge-search";
import { QUICK_SEARCH_LIMIT, viewAllKnowledgeSearchHref } from "@/lib/knowledge-search";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface QuickSearchOverlayProps {
  open: boolean;
  items: KnowledgeListItemData[];
  onClose: () => void;
}

export function QuickSearchOverlay({ open, items, onClose }: QuickSearchOverlayProps) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const searchState = useKnowledgeSearch(QUICK_SEARCH_LIMIT);

  const trimmedQuery = query.trim();
  const recentItems = useMemo(
    () => [...items]
      .sort((a, b) => b.updatedAtIso.localeCompare(a.updatedAtIso))
      .slice(0, QUICK_SEARCH_LIMIT),
    [items],
  );
  const results = trimmedQuery ? searchState.results : recentItems;

  const hasViewAllAction = trimmedQuery.length > 0;
  const actionCount = results.length + (hasViewAllAction ? 1 : 0);
  const selectedResultId = selectedIndex < results.length ? `quick-search-result-${selectedIndex}` : undefined;

  useEffect(() => {
    if (!open) {
      return;
    }

    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const frame = window.requestAnimationFrame(() => inputRef.current?.focus());

    return () => {
      window.cancelAnimationFrame(frame);
      const previousFocus = previousFocusRef.current;
      if (previousFocus?.isConnected) {
        previousFocus.focus();
      }
    };
  }, [open]);

  if (!open) {
    return null;
  }

  const closeAndNavigate = (href: string) => {
    onClose();
    router.push(href);
  };

  const openResult = (item: KnowledgeListItemData) => {
    closeAndNavigate(item.href);
  };

  const viewAllResults = () => {
    if (!trimmedQuery) {
      return;
    }

    closeAndNavigate(viewAllKnowledgeSearchHref(trimmedQuery));
  };

  const activateSelectedAction = () => {
    if (selectedIndex < results.length) {
      const item = results[selectedIndex];
      if (item) {
        openResult(item);
      }
      return;
    }

    if (hasViewAllAction && selectedIndex === results.length) {
      viewAllResults();
    }
  };

  const actionElements = () =>
    Array.from(dialogRef.current?.querySelectorAll<HTMLButtonElement>("[data-quick-search-action]") ?? []);

  const moveSelection = (direction: 1 | -1) => {
    if (actionCount === 0) {
      return;
    }

    let nextIndex = selectedIndex + direction;
    if (nextIndex < 0) nextIndex = actionCount - 1;
    if (nextIndex >= actionCount) nextIndex = 0;

    setSelectedIndex(nextIndex);

    if (document.activeElement !== inputRef.current) {
      const nextAction = actionElements()[nextIndex];
      window.requestAnimationFrame(() => nextAction?.focus());
    }
  };

  const trapTabFocus = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "Tab") {
      return;
    }

    const focusable = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>(
        'input, button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
      ) ?? [],
    );

    if (focusable.length === 0) {
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  };

  return (
    <div className="fixed inset-0 z-[70] flex items-start justify-center px-3 pb-3 pt-16 sm:px-6 sm:pt-[12vh]">
      <button
        type="button"
        aria-label="Close quick search"
        onClick={onClose}
        className="absolute inset-0 cursor-default bg-black/20"
      />

      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="quick-search-title"
        onKeyDown={(event) => {
          trapTabFocus(event);

          if (event.key === "Escape") {
            event.preventDefault();
            onClose();
            return;
          }

          if (event.key === "ArrowDown") {
            event.preventDefault();
            moveSelection(1);
            return;
          }

          if (event.key === "ArrowUp") {
            event.preventDefault();
            moveSelection(-1);
            return;
          }

          if (event.key === "Enter" && document.activeElement === inputRef.current) {
            event.preventDefault();
            activateSelectedAction();
          }
        }}
        className="relative flex max-h-[calc(100vh-5rem)] w-full max-w-[720px] flex-col overflow-hidden rounded-[6px] border border-[var(--border-strong)] bg-[var(--surface)] shadow-[0_18px_50px_rgb(29_33_30_/_18%)]"
      >
        <h2 id="quick-search-title" className="sr-only">Quick search knowledge</h2>

        <div className="flex min-h-14 items-center gap-3 border-b border-[var(--border)] px-4 sm:min-h-16 sm:px-5">
          <svg
            viewBox="0 0 20 20"
            aria-hidden="true"
            className="h-4 w-4 shrink-0 fill-none stroke-[var(--text-subtle)]"
            strokeWidth="1.5"
          >
            <circle cx="8.5" cy="8.5" r="5.25" />
            <path d="m12.4 12.4 4.1 4.1" strokeLinecap="round" />
          </svg>

          <input
            ref={inputRef}
            type="search"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setSelectedIndex(0);
              searchState.search(event.target.value);
            }}
            role="combobox"
            aria-autocomplete="list"
            aria-expanded="true"
            aria-controls="quick-search-results"
            aria-activedescendant={selectedResultId}
            autoComplete="off"
            placeholder="Search knowledge..."
            className="h-12 min-w-0 flex-1 border-0 bg-transparent p-0 text-[16px] text-[var(--text)] outline-none placeholder:text-[var(--text-subtle)] sm:text-[17px]"
          />

          <button
            type="button"
            onClick={onClose}
            aria-label="Close quick search"
            className="shrink-0 border border-[var(--border)] bg-[var(--surface-muted)] px-2 py-1 text-[10px] font-medium text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-muted)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
          >
            Esc
          </button>
        </div>

        <div className="min-h-0 overflow-y-auto py-2">
          <div className="flex items-center justify-between px-4 pb-2 pt-1 sm:px-5">
            <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">
              {trimmedQuery ? "Quick results" : "Recently updated"}
            </p>
            <span className="text-[11px] tabular-nums text-[var(--text-subtle)]">
              {trimmedQuery ? `${results.length} shown` : `${items.length} notes`}
            </span>
          </div>

          <div id="quick-search-results" role="listbox" aria-label="Knowledge results">
            {results.map((item, index) => {
              const selected = selectedIndex === index;
              return (
                <button
                  id={`quick-search-result-${index}`}
                  key={item.id}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  data-quick-search-action
                  onMouseEnter={() => setSelectedIndex(index)}
                  onFocus={() => setSelectedIndex(index)}
                  onClick={() => openResult(item)}
                  className={`group grid w-full grid-cols-[minmax(0,1fr)_auto] gap-4 px-4 py-3 text-left transition-colors sm:px-5 ${
                    selected ? "bg-[var(--row-hover)]" : "hover:bg-[var(--row-hover)]"
                  } focus-visible:outline-none`}
                >
                  <span className="min-w-0">
                    <span
                      className={`block truncate text-[14px] font-medium transition-colors ${
                        selected
                          ? "text-[var(--accent-strong)]"
                          : "text-[var(--text)] group-hover:text-[var(--accent-strong)]"
                      }`}
                    >
                      {item.title}
                    </span>
                    <span className="mt-1 block truncate text-[11px] text-[var(--text-subtle)]">
                      {[item.collection, ...item.tags].filter(Boolean).join(" · ") || "No collection or tags"}
                    </span>
                  </span>
                  <span className="pt-0.5 text-[11px] tabular-nums text-[var(--text-subtle)]">{item.updatedAt}</span>
                </button>
              );
            })}
          </div>

          {trimmedQuery && searchState.status === "searching" && (
            <div className="px-5 py-8 text-center" aria-live="polite">
              <p className="text-[12px] text-[var(--text-muted)]">Searching…</p>
            </div>
          )}

          {trimmedQuery && searchState.status === "error" && (
            <div className="px-5 py-8 text-center" aria-live="polite">
              <p className="text-[14px] font-medium text-[var(--text)]">Search is unavailable.</p>
              <button
                type="button"
                onClick={() => searchState.search(query)}
                className="mt-2 text-[12px] text-[var(--accent-strong)] underline underline-offset-4"
              >
                Try again
              </button>
            </div>
          )}

          {trimmedQuery && searchState.status === "success" && results.length === 0 && (
            <div className="px-5 py-8 text-center">
              <p className="text-[14px] font-medium text-[var(--text)]">No quick matches.</p>
              <p className="mt-1 text-[12px] leading-5 text-[var(--text-muted)]">
                Open full search to keep working with “{trimmedQuery}”.
              </p>
            </div>
          )}

          {hasViewAllAction && (
            <div className="mt-2 border-t border-[var(--border)] pt-2">
              <button
                type="button"
                data-quick-search-action
                onMouseEnter={() => setSelectedIndex(results.length)}
                onFocus={() => setSelectedIndex(results.length)}
                onClick={viewAllResults}
                className={`group flex w-full items-center justify-between px-4 py-3 text-left text-[13px] transition-colors sm:px-5 ${
                  selectedIndex === results.length
                    ? "bg-[var(--row-hover)] text-[var(--accent-strong)]"
                    : "text-[var(--text-muted)] hover:bg-[var(--row-hover)] hover:text-[var(--accent-strong)]"
                } focus-visible:outline-none`}
              >
                <span>View all results for “{trimmedQuery}”</span>
                <span aria-hidden="true">→</span>
              </button>
            </div>
          )}
        </div>

        <div className="hidden items-center justify-between border-t border-[var(--border)] px-5 py-2.5 text-[10px] text-[var(--text-subtle)] sm:flex">
          <span>↑ ↓ Navigate · Enter Open</span>
          <span>Cmd/Ctrl K · Esc Close</span>
        </div>
      </div>
    </div>
  );
}
