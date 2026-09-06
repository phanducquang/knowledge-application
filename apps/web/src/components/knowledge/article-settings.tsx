"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { KnowledgeVisibility } from "@/types/knowledge";

interface ArticleSettingsProps {
  compact?: boolean;
  initialVisibility: KnowledgeVisibility;
  initialCollection: string;
  initialTags: string[];
  updatedAt: string;
}

const propertyLabelClassName =
  "text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]";

const visibilityOptions: KnowledgeVisibility[] = ["Private", "Unlisted", "Public"];
const initialCollections = ["Backend", "Database", "DevOps"];

function VisibilityControl({
  value,
  onChange,
}: {
  value: KnowledgeVisibility;
  onChange: (value: KnowledgeVisibility) => void;
}) {
  return (
    <div>
      <p className={propertyLabelClassName}>Visibility</p>
      <div
        className="mt-2 flex w-full border-b border-[var(--border)]"
        role="radiogroup"
        aria-label="Visibility"
      >
        {visibilityOptions.map((option) => {
          const selected = option === value;

          return (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => onChange(option)}
              className={`relative min-w-0 flex-1 whitespace-nowrap px-1 pb-2 pt-1 text-center text-[12px] font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${
                selected
                  ? "text-[var(--accent-strong)]"
                  : "text-[var(--text-muted)] hover:text-[var(--text)]"
              }`}
            >
              {option}
              <span
                aria-hidden="true"
                className={`absolute inset-x-1 -bottom-px h-0.5 transition-colors ${
                  selected ? "bg-[var(--accent)]" : "bg-transparent"
                }`}
              />
            </button>
          );
        })}
      </div>
    </div>
  );
}

function CollectionCombobox({
  id,
  value,
  onChange,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [collections, setCollections] = useState(initialCollections);
  const rootRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }

      if (rootRef.current && !rootRef.current.contains(target)) {
        setOpen(false);
        setQuery("");
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  const filteredCollections = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) {
      return collections;
    }

    return collections.filter((collection) => collection.toLowerCase().includes(normalizedQuery));
  }, [collections, query]);

  const normalizedDraft = query.trim();
  const canCreate =
    normalizedDraft.length > 0 &&
    !collections.some((collection) => collection.toLowerCase() === normalizedDraft.toLowerCase());

  const selectCollection = (collection: string) => {
    onChange(collection);
    setQuery("");
    setOpen(false);
  };

  const createCollection = () => {
    if (!canCreate) {
      return;
    }

    setCollections((current) => [...current, normalizedDraft]);
    selectCollection(normalizedDraft);
  };

  const handleCreateAction = () => {
    if (canCreate) {
      createCollection();
      return;
    }

    searchInputRef.current?.focus();
  };

  return (
    <div ref={rootRef} className="relative">
      <p className={propertyLabelClassName}>Collection</p>
      <button
        id={id}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => {
          setOpen((current) => !current);
          if (open) {
            setQuery("");
          }
        }}
        className="mt-2 inline-flex min-h-9 max-w-full items-center gap-2 border border-[var(--border-strong)] bg-[var(--surface)] px-3 text-[13px] font-medium text-[var(--text)] transition-colors hover:border-[var(--accent-muted)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
      >
        <span className="truncate">{value}</span>
        <span aria-hidden="true" className="text-[10px] text-[var(--text-subtle)]">
          ▾
        </span>
      </button>

      {open && (
        <div className="absolute left-0 z-20 mt-1 w-[min(280px,calc(100vw-48px))] border border-[var(--border-strong)] bg-[var(--surface)] shadow-sm">
          <div className="p-2">
            <label className="block" htmlFor={`${id}-search`}>
              <span className="sr-only">Search collections</span>
              <input
                ref={searchInputRef}
                id={`${id}-search`}
                type="text"
                autoFocus
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Escape") {
                    setOpen(false);
                    setQuery("");
                  }

                  if (event.key === "Enter" && canCreate) {
                    event.preventDefault();
                    createCollection();
                  }
                }}
                placeholder="Search collections..."
                className="h-9 w-full border border-[var(--border)] bg-[var(--background)] px-2.5 text-[12px] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] focus:border-[var(--accent)]"
              />
            </label>

            <div className="mt-2 max-h-48 overflow-y-auto" role="listbox" aria-label="Collections">
              {filteredCollections.map((collection) => {
                const selected = collection === value;

                return (
                  <button
                    key={collection}
                    type="button"
                    role="option"
                    aria-selected={selected}
                    onClick={() => selectCollection(collection)}
                    className={`flex min-h-8 w-full items-center justify-between gap-3 px-2 text-left text-[12px] transition-colors hover:bg-[var(--row-hover)] ${
                      selected ? "font-medium text-[var(--accent-strong)]" : "text-[var(--text)]"
                    }`}
                  >
                    <span>{collection}</span>
                    {selected && (
                      <span aria-hidden="true" className="text-[11px] text-[var(--accent)]">
                        ✓
                      </span>
                    )}
                  </button>
                );
              })}

              {filteredCollections.length === 0 && (
                <p className="px-2 py-2 text-[11px] text-[var(--text-subtle)]">No matching collection.</p>
              )}
            </div>
          </div>

          <button
            type="button"
            onClick={handleCreateAction}
            className="w-full border-t border-[var(--border)] px-4 py-2.5 text-left text-[12px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--row-hover)] hover:text-[var(--accent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--accent)]"
          >
            {canCreate ? `+ Create “${normalizedDraft}”` : "+ New collection"}
          </button>
        </div>
      )}
    </div>
  );
}

function TagEditor({ initialTags }: { initialTags: string[] }) {
  const [tags, setTags] = useState(initialTags);
  const [draft, setDraft] = useState("");

  const addTag = () => {
    const nextTag = draft.trim().replace(/^#/, "");
    if (!nextTag) {
      return;
    }

    if (!tags.some((tag) => tag.toLowerCase() === nextTag.toLowerCase())) {
      setTags((current) => [...current, nextTag]);
    }
    setDraft("");
  };

  return (
    <div>
      <p className={propertyLabelClassName}>Tags</p>

      {tags.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Selected tags">
          {tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex min-h-7 items-center gap-1.5 rounded-[4px] border border-[var(--border)] bg-[var(--surface-muted)] px-2 text-[12px] text-[var(--text)]"
            >
              #{tag}
              <button
                type="button"
                aria-label={`Remove ${tag} tag`}
                onClick={() => setTags((current) => current.filter((item) => item !== tag))}
                className="-mr-0.5 inline-flex h-5 w-5 items-center justify-center text-[14px] leading-none text-[var(--text-subtle)] transition-colors hover:text-[var(--danger)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="mt-2 border-b border-[var(--border-strong)] transition-colors focus-within:border-[var(--accent)]">
        <input
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              addTag();
            }

            if (event.key === "Backspace" && !draft && tags.length > 0) {
              setTags((current) => current.slice(0, -1));
            }
          }}
          onBlur={addTag}
          placeholder="Add tag and press Enter"
          className="h-9 w-full bg-transparent px-0 text-[12px] text-[var(--text)] outline-none placeholder:text-[var(--text-subtle)]"
        />
      </div>
    </div>
  );
}

export function ArticleSettings({
  compact = false,
  initialVisibility,
  initialCollection,
  initialTags,
  updatedAt,
}: ArticleSettingsProps) {
  const [visibility, setVisibility] = useState(initialVisibility);
  const [collection, setCollection] = useState(initialCollection);
  const suffix = compact ? "mobile" : "desktop";

  const content = (
    <div className="space-y-6">
      <VisibilityControl value={visibility} onChange={setVisibility} />

      <CollectionCombobox
        id={`collection-${suffix}`}
        value={collection}
        onChange={setCollection}
      />

      <TagEditor initialTags={initialTags} />

      <div>
        <p className={propertyLabelClassName}>Updated</p>
        <p className="mt-2 text-[12px] tabular-nums text-[var(--text-muted)]">{updatedAt}</p>
      </div>
    </div>
  );

  if (compact) {
    return (
      <details className="mb-6 border-y border-[var(--border)] py-3 xl:hidden">
        <summary className="cursor-pointer text-[13px] font-medium text-[var(--text-muted)]">Article settings</summary>
        <div className="mt-5">{content}</div>
      </details>
    );
  }

  return (
    <aside className="hidden xl:block" aria-label="Article settings">
      <div className="sticky top-8 border-l border-[var(--border)] pl-5">{content}</div>
    </aside>
  );
}
