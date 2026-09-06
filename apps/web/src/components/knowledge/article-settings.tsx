"use client";

import { useState } from "react";
import type { KnowledgeVisibility } from "@/types/knowledge";

interface ArticleSettingsProps {
  compact?: boolean;
  initialVisibility: KnowledgeVisibility;
  initialCollection: string;
  initialTags: string[];
  updatedAt: string;
}

interface SettingPickerProps {
  id: string;
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}

const propertyLabelClassName =
  "text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]";

function SettingPicker({ id, label, value, options, onChange }: SettingPickerProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <p className={propertyLabelClassName}>{label}</p>
      <button
        id={id}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="mt-1.5 flex min-h-9 w-full items-center justify-between gap-3 border-b border-[var(--border-strong)] text-left text-[13px] font-medium text-[var(--text)] transition-colors hover:border-[var(--accent-muted)] focus-visible:border-[var(--accent)] focus-visible:outline-none"
      >
        <span>{value}</span>
        <span aria-hidden="true" className="text-[11px] text-[var(--text-subtle)]">
          ▾
        </span>
      </button>

      {open && (
        <div
          role="listbox"
          aria-labelledby={id}
          className="absolute left-0 right-0 z-20 mt-1 border border-[var(--border-strong)] bg-[var(--surface)] p-1 shadow-sm"
        >
          {options.map((option) => {
            const selected = option === value;
            return (
              <button
                key={option}
                type="button"
                role="option"
                aria-selected={selected}
                onClick={() => {
                  onChange(option);
                  setOpen(false);
                }}
                className={`flex min-h-8 w-full items-center justify-between px-2 text-left text-[13px] transition-colors hover:bg-[var(--row-hover)] ${
                  selected ? "font-medium text-[var(--accent-strong)]" : "text-[var(--text)]"
                }`}
              >
                <span>{option}</span>
                {selected && (
                  <span aria-hidden="true" className="text-[11px] text-[var(--accent)]">
                    ✓
                  </span>
                )}
              </button>
            );
          })}
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
      <div className="mt-2 flex min-h-10 flex-wrap items-center gap-1.5 border-b border-[var(--border-strong)] pb-2 transition-colors focus-within:border-[var(--accent)]">
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
          placeholder={tags.length === 0 ? "Add tag and press Enter" : "Add tag"}
          className="min-h-7 min-w-[110px] flex-1 bg-transparent px-1 text-[12px] text-[var(--text)] outline-none placeholder:text-[var(--text-subtle)]"
        />
      </div>
      <p className="mt-1.5 text-[10px] leading-4 text-[var(--text-subtle)]">Press Enter to add a tag.</p>
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
    <div className="space-y-5">
      <SettingPicker
        id={`visibility-${suffix}`}
        label="Visibility"
        value={visibility}
        options={["Private", "Unlisted", "Public"]}
        onChange={(value) => setVisibility(value as KnowledgeVisibility)}
      />

      <SettingPicker
        id={`collection-${suffix}`}
        label="Collection"
        value={collection}
        options={["Backend", "Database", "DevOps"]}
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
        <div className="mt-4">{content}</div>
      </details>
    );
  }

  return (
    <aside className="hidden xl:block" aria-label="Article settings">
      <div className="sticky top-8 border-l border-[var(--border)] pl-5">
        <p className="mb-5 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">Article settings</p>
        {content}
      </div>
    </aside>
  );
}
