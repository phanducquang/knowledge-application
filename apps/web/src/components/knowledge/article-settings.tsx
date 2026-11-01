"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { KnowledgeVisibility } from "@/types/knowledge";

interface ArticleSettingsProps {
  compact?: boolean;
  visibility: KnowledgeVisibility;
  collection: string | null;
  tags: string[];
  collectionOptions: string[];
  updatedAt: string;
  onVisibilityChange: (value: KnowledgeVisibility) => void;
  onCollectionChange: (value: string | null) => void;
  onTagsChange: (value: string[]) => void;
}

const propertyLabelClassName =
  "text-[12px] font-medium text-[var(--text-subtle)]";

const visibilityOptions: KnowledgeVisibility[] = ["Private", "Unlisted", "Public"];

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
        className="mt-1.5 flex w-full border-b border-[var(--border)]"
        role="radiogroup"
        aria-label="Visibility"
        onKeyDown={(event) => {
          const direction = event.key === "ArrowRight" || event.key === "ArrowDown" ? 1
            : event.key === "ArrowLeft" || event.key === "ArrowUp" ? -1 : 0;
          if (!direction && event.key !== "Home" && event.key !== "End") return;
          event.preventDefault();
          const currentIndex = visibilityOptions.indexOf(value);
          const nextIndex = event.key === "Home" ? 0 : event.key === "End" ? visibilityOptions.length - 1
            : (currentIndex + direction + visibilityOptions.length) % visibilityOptions.length;
          event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]?.focus();
          onChange(visibilityOptions[nextIndex]);
        }}
      >
        {visibilityOptions.map((option) => {
          const selected = option === value;

          return (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={selected}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(option)}
              className={`relative min-h-11 min-w-0 flex-1 whitespace-nowrap px-0.5 pb-1 pt-0.5 text-center text-[14px] font-medium leading-5 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:min-h-8 ${
                selected
                  ? "text-[var(--accent-strong)]"
                  : "text-[var(--text-muted)] hover:text-[var(--text)]"
              }`}
            >
              {option}
              <span
                aria-hidden="true"
                className={`absolute inset-x-1 -bottom-px h-px transition-colors ${
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
  collections,
  onChange,
}: {
  id: string;
  value: string | null;
  collections: string[];
  onChange: (value: string | null) => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
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

  const availableCollections = useMemo(
    () =>
      value && !collections.some((collection) => collection.toLowerCase() === value.toLowerCase())
        ? [value, ...collections]
        : collections,
    [collections, value],
  );

  const filteredCollections = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) {
      return availableCollections;
    }

    return availableCollections.filter((collection) => collection.toLowerCase().includes(normalizedQuery));
  }, [availableCollections, query]);

  const normalizedDraft = query.trim();
  const canCreate =
    normalizedDraft.length > 0 &&
    !availableCollections.some((collection) => collection.toLowerCase() === normalizedDraft.toLowerCase());

  const selectCollection = (collection: string | null) => {
    onChange(collection);
    setQuery("");
    setOpen(false);
    rootRef.current?.querySelector<HTMLButtonElement>("button")?.focus();
  };

  const createCollection = () => {
    if (!canCreate) {
      return;
    }

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
    <div ref={rootRef} className="relative" onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) {
        setOpen(false);
        setQuery("");
      }
    }} onKeyDown={(event) => {
      if (event.target === searchInputRef.current && (event.key === "ArrowDown" || event.key === "ArrowUp")) {
        event.preventDefault();
        const options = rootRef.current?.querySelectorAll<HTMLButtonElement>('[role="option"]');
        options?.[event.key === "ArrowDown" ? 0 : options.length - 1]?.focus();
      }
      if (event.key === "Escape" && open) {
        event.preventDefault();
        setOpen(false);
        setQuery("");
        rootRef.current?.querySelector<HTMLButtonElement>("button")?.focus();
      }
    }}>
      <p id={`${id}-label`} className={propertyLabelClassName}>Collection</p>
      <button
        id={id}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-labelledby={`${id}-label ${id}-value`}
        aria-controls={open ? `${id}-options` : undefined}
        onClick={() => {
          setOpen((current) => !current);
          if (open) {
            setQuery("");
          }
        }}
        className="mt-1.5 inline-flex min-h-11 max-w-full items-center gap-1.5 border border-[var(--border-strong)] bg-[var(--surface)] px-2.5 py-1 text-[14px] font-normal leading-5 text-[var(--text)] transition-colors hover:border-[var(--accent-muted)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:min-h-9 sm:px-3 sm:py-1.5 sm:text-[15px] xl:px-2.5 xl:py-1 xl:text-[14px]"
      >
        <span id={`${id}-value`} className="truncate">{value ?? "No collection"}</span>
        <span aria-hidden="true" className="text-[10px] text-[var(--text-subtle)]">
          ▾
        </span>
      </button>

      {open && (
        <div className="absolute left-0 z-20 mt-1 w-64 max-w-[calc(100dvw-40px)] border border-[var(--border-strong)] bg-[var(--surface)] shadow-[var(--shadow-popover)] xl:left-auto xl:right-0">
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
                className="h-11 w-full border border-[var(--border)] bg-[var(--background)] px-2 text-[16px] font-normal leading-5 text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] focus:border-[var(--accent)] sm:h-9 sm:text-[13px]"
              />
            </label>

            <div id={`${id}-options`} className="mt-1.5 max-h-48 overflow-y-auto" role="listbox" aria-label="Collections" onKeyDown={(event) => {
              if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
              event.preventDefault();
              const options = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="option"]'));
              const currentIndex = options.indexOf(document.activeElement as HTMLButtonElement);
              const nextIndex = event.key === "Home" ? 0 : event.key === "End" ? options.length - 1
                : (currentIndex + (event.key === "ArrowDown" ? 1 : -1) + options.length) % options.length;
              options[nextIndex]?.focus();
            }}>
              {!query.trim() && (
                <button
                  type="button"
                  role="option"
                  aria-selected={value === null}
                  onClick={() => selectCollection(null)}
                  className={`flex min-h-11 w-full sm:min-h-9 items-center justify-between gap-3 px-2 text-left text-[14px] leading-5 transition-colors hover:bg-[var(--row-hover)] sm:text-[15px] xl:text-[14px] ${
                    value === null ? "font-medium text-[var(--accent-strong)]" : "text-[var(--text-muted)]"
                  }`}
                >
                  <span>No collection</span>
                  {value === null && <span aria-hidden="true" className="text-[12px] text-[var(--accent)]">✓</span>}
                </button>
              )}
              {filteredCollections.map((collection) => {
                const selected = collection === value;

                return (
                  <button
                    key={collection}
                    type="button"
                    role="option"
                    aria-selected={selected}
                    onClick={() => selectCollection(collection)}
                    className={`flex min-h-11 w-full sm:min-h-9 items-center justify-between gap-3 px-2 text-left text-[14px] leading-5 transition-colors hover:bg-[var(--row-hover)] sm:text-[15px] xl:text-[14px] ${
                      selected ? "font-medium text-[var(--accent-strong)]" : "text-[var(--text)]"
                    }`}
                  >
                    <span className="min-w-0 break-words">{collection}</span>
                    {selected && (
                      <span aria-hidden="true" className="text-[12px] text-[var(--accent)]">
                        ✓
                      </span>
                    )}
                  </button>
                );
              })}

              {filteredCollections.length === 0 && (
                <p className="px-2 py-2 text-[12px] text-[var(--text-subtle)]">No matching collection.</p>
              )}
            </div>
          </div>

          <button
            type="button"
            onClick={handleCreateAction}
            className="min-h-11 w-full border-t border-[var(--border)] px-3 py-2 text-left text-[13px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--row-hover)] hover:text-[var(--accent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--accent)]"
          >
            {canCreate ? `+ Create “${normalizedDraft}”` : "+ New collection"}
          </button>
        </div>
      )}
    </div>
  );
}

function TagEditor({ id, tags, onChange }: { id: string; tags: string[]; onChange: (tags: string[]) => void }) {
  const [draft, setDraft] = useState("");

  const addTag = () => {
    const nextTag = draft.trim().replace(/^#/, "");
    if (!nextTag) {
      return;
    }

    if (!tags.some((tag) => tag.toLowerCase() === nextTag.toLowerCase())) {
      onChange([...tags, nextTag]);
    }
    setDraft("");
  };

  return (
    <div>
      <label htmlFor={id} className={propertyLabelClassName}>Tags</label>

      {tags.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1" aria-label="Selected tags">
          {tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex min-h-9 max-w-full items-center gap-1 rounded-[4px] border border-[var(--border)] bg-[var(--surface-muted)] pl-2 text-[14px] text-[var(--text)] sm:min-h-8"
            >
              <span className="min-w-0 break-all">#{tag}</span>
              <button
                type="button"
                aria-label={`Remove ${tag} tag`}
                onClick={() => onChange(tags.filter((item) => item !== tag))}
                className="inline-flex h-9 w-9 shrink-0 items-center justify-center text-[16px] leading-none text-[var(--text-muted)] transition-colors hover:text-[var(--danger)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--accent)] sm:h-8 sm:w-8"
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="mt-1.5 border-b border-[var(--control-border)] transition-colors focus-within:border-[var(--accent)]">
        <input
          id={id}
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              addTag();
            }

            if (event.key === "Backspace" && !draft && tags.length > 0) {
              onChange(tags.slice(0, -1));
            }
          }}
          onBlur={addTag}
          placeholder="Add tag and press Enter"
          className="h-11 w-full bg-transparent px-0 text-[16px] leading-5 text-[var(--text)] outline-none placeholder:text-[var(--text-subtle)] sm:h-9 sm:text-[14px]"
        />
      </div>
    </div>
  );
}

export function ArticleSettings({
  compact = false,
  visibility,
  collection,
  tags,
  collectionOptions,
  updatedAt,
  onVisibilityChange,
  onCollectionChange,
  onTagsChange,
}: ArticleSettingsProps) {
  const suffix = compact ? "mobile" : "desktop";

  const content = (
    <div className="space-y-5">
      <VisibilityControl value={visibility} onChange={onVisibilityChange} />

      <CollectionCombobox
        id={`collection-${suffix}`}
        value={collection}
        collections={collectionOptions}
        onChange={onCollectionChange}
      />

      <TagEditor id={`tags-${suffix}`} tags={tags} onChange={onTagsChange} />

      <div>
        <p className={propertyLabelClassName}>Updated</p>
        <p className="mt-1.5 text-[14px] tabular-nums text-[var(--text-muted)]">{updatedAt}</p>
      </div>
    </div>
  );

  if (compact) {
    return (
      <details className="mb-6 border-y border-[var(--border)] py-3 xl:hidden">
        <summary className="min-h-11 cursor-pointer content-center text-[14px] font-medium text-[var(--text-muted)] sm:min-h-8">Article settings</summary>
        <div className="mt-4">{content}</div>
      </details>
    );
  }

  return (
    <aside className="hidden xl:block" aria-label="Article settings">
      <div className="sticky top-8 border-l border-[var(--border)] pl-5">{content}</div>
    </aside>
  );
}
