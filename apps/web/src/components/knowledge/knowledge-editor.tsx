"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ArticleSettings } from "@/components/knowledge/article-settings";
import { KnowledgeMarkdownEditor } from "@/components/knowledge/knowledge-markdown-editor";
import { KnowledgeShareAction } from "@/components/knowledge/knowledge-share-action";
import {
  createKnowledgeAction,
  getUnlistedLinkAction,
  regenerateUnlistedLinkAction,
  updateKnowledgeAction,
  updateKnowledgeVisibilityAction,
} from "@/app/knowledge/actions";
import { formatKnowledgeDate } from "@/lib/knowledge-mapping";
import { runSerializedVisibilityChange } from "@/lib/share-visibility-coordinator";
import type {
  KnowledgeActionError,
  KnowledgeActionResult,
  KnowledgeData,
  KnowledgeDraft,
  KnowledgeVisibility,
} from "@/types/knowledge";

type SaveStatus = "saved" | "saving" | "failed";

interface KnowledgeEditorProps {
  mode: "create" | "edit";
  initialKnowledge?: KnowledgeData;
  collectionOptions: string[];
}

const emptyDraft: KnowledgeDraft = {
  title: "",
  summary: "",
  content: "",
  visibility: "Private",
  collection: null,
  tags: [],
};

const fieldLabelClassName =
  "text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]";

function editableFields(knowledge: KnowledgeData): KnowledgeDraft {
  return {
    title: knowledge.title,
    summary: knowledge.summary,
    content: knowledge.content,
    visibility: knowledge.visibility,
    collection: knowledge.collection,
    tags: knowledge.tags,
  };
}

function statusClassName(status: SaveStatus) {
  if (status === "saved") return "text-[var(--success)]";
  if (status === "failed") return "text-[var(--danger)]";
  return "text-[var(--text-subtle)]";
}

export function KnowledgeEditor({
  mode,
  initialKnowledge,
  collectionOptions,
}: KnowledgeEditorProps) {
  const router = useRouter();
  const initialDraft = initialKnowledge ? editableFields(initialKnowledge) : emptyDraft;
  const [initialMarkdown] = useState(initialDraft.content);
  const [draft, setDraft] = useState<KnowledgeDraft>(initialDraft);
  const [updatedAt, setUpdatedAt] = useState(initialKnowledge?.updatedAt ?? null);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("saved");
  const [saveError, setSaveError] = useState<KnowledgeActionError | null>(null);
  const [creating, setCreating] = useState(false);
  const [openingHistory, setOpeningHistory] = useState(false);
  const latestDraftRef = useRef(initialDraft);
  const revisionRef = useRef(0);
  const dirtyRef = useRef(false);
  const saveInFlightRef = useRef(false);
  const shareMutationRef = useRef(false);
  const savePromiseRef = useRef<Promise<KnowledgeActionResult> | null>(null);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);
  const flushSaveRef = useRef<() => Promise<void>>(async () => undefined);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  const flushSave = useCallback(async () => {
    if (
      mode !== "edit" ||
      !initialKnowledge ||
      saveInFlightRef.current ||
      shareMutationRef.current ||
      !dirtyRef.current
    ) {
      return;
    }

    saveInFlightRef.current = true;
    dirtyRef.current = false;
    const savingRevision = revisionRef.current;
    const savingDraft: KnowledgeDraft = {
      ...latestDraftRef.current,
      tags: [...latestDraftRef.current.tags],
    };
    setSaveStatus("saving");

    const savePromise = updateKnowledgeAction(
      initialKnowledge.id,
      initialKnowledge.slug,
      savingDraft,
    );
    savePromiseRef.current = savePromise;
    const result = await savePromise;
    if (savePromiseRef.current === savePromise) {
      savePromiseRef.current = null;
    }

    saveInFlightRef.current = false;
    if (!mountedRef.current) {
      return;
    }

    if (!result.ok) {
      const hasNewerDraft = dirtyRef.current;
      dirtyRef.current = true;
      setSaveError(result.error);
      setSaveStatus("failed");
      if (hasNewerDraft) {
        window.setTimeout(() => void flushSaveRef.current(), 0);
      }
      return;
    }

    setSaveError(null);
    setUpdatedAt(result.knowledge.updatedAt);

    if (revisionRef.current === savingRevision && !dirtyRef.current) {
      const normalizedDraft = editableFields(result.knowledge);
      latestDraftRef.current = normalizedDraft;
      setDraft(normalizedDraft);
      setSaveStatus("saved");
      return;
    }

    setSaveStatus("saving");
    window.setTimeout(() => void flushSaveRef.current(), 0);
  }, [initialKnowledge, mode]);

  useEffect(() => {
    flushSaveRef.current = flushSave;
  }, [flushSave]);

  const changeDraft = useCallback(
    (update: (current: KnowledgeDraft) => KnowledgeDraft) => {
      const nextDraft = update(latestDraftRef.current);
      latestDraftRef.current = nextDraft;
      setDraft(nextDraft);
      setSaveError(null);

      if (mode !== "edit") {
        return;
      }

      revisionRef.current += 1;
      dirtyRef.current = true;
      setSaveStatus("saving");

      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
      debounceTimerRef.current = setTimeout(() => {
        debounceTimerRef.current = null;
        void flushSaveRef.current();
      }, 700);
    },
    [mode],
  );

  const changeVisibility = useCallback(
    (visibility: KnowledgeVisibility) =>
      changeDraft((current) => ({ ...current, visibility })),
    [changeDraft],
  );

  const changeVisibilityFromShare = useCallback(async (visibility: KnowledgeVisibility) => {
    if (!initialKnowledge || mode !== "edit" || shareMutationRef.current) {
      return {
        ok: false as const,
        error: { message: "The visibility change could not be started.", fieldErrors: {} },
      };
    }

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    shareMutationRef.current = true;
    revisionRef.current += 1;
    setSaveError(null);
    setSaveStatus("saving");

    const result = await runSerializedVisibilityChange({
      inFlightSave: savePromiseRef.current,
      flushPendingDraft: async () => {
        if (!dirtyRef.current) return null;
        dirtyRef.current = false;
        const savingDraft: KnowledgeDraft = {
          ...latestDraftRef.current,
          tags: [...latestDraftRef.current.tags],
        };
        const flushResult = await updateKnowledgeAction(
          initialKnowledge.id,
          initialKnowledge.slug,
          savingDraft,
        );
        if (!flushResult.ok) dirtyRef.current = true;
        else setUpdatedAt(flushResult.knowledge.updatedAt);
        return flushResult;
      },
      patchVisibility: () => updateKnowledgeVisibilityAction(
        initialKnowledge.id,
        visibility,
      ),
    });
    shareMutationRef.current = false;

    if (!result.ok) {
      setSaveError(result.error);
      setSaveStatus(dirtyRef.current ? "saving" : "failed");
    } else {
      latestDraftRef.current = {
        ...latestDraftRef.current,
        visibility: result.knowledge.visibility,
      };
      setDraft((current) => ({ ...current, visibility: result.knowledge.visibility }));
      setUpdatedAt(result.knowledge.updatedAt);
      setSaveError(null);
      setSaveStatus(dirtyRef.current ? "saving" : "saved");
    }

    if (dirtyRef.current) {
      window.setTimeout(() => void flushSaveRef.current(), 0);
    }
    return result;
  }, [initialKnowledge, mode]);

  const loadUnlistedLink = useCallback(
    () => initialKnowledge
      ? getUnlistedLinkAction(initialKnowledge.id)
      : Promise.resolve({
          ok: false as const,
          error: { message: "Create this note before sharing it.", fieldErrors: {} },
        }),
    [initialKnowledge],
  );

  const regenerateUnlistedLink = useCallback(
    () => initialKnowledge
      ? regenerateUnlistedLinkAction(initialKnowledge.id)
      : Promise.resolve({
          ok: false as const,
          error: { message: "Create this note before sharing it.", fieldErrors: {} },
        }),
    [initialKnowledge],
  );

  const changeCollection = useCallback(
    (collection: string | null) =>
      changeDraft((current) => ({ ...current, collection })),
    [changeDraft],
  );

  const changeTags = useCallback(
    (tags: string[]) => changeDraft((current) => ({ ...current, tags })),
    [changeDraft],
  );

  const changeMarkdown = useCallback(
    (content: string) => {
      if (latestDraftRef.current.content === content) {
        return;
      }
      changeDraft((current) => ({ ...current, content }));
    },
    [changeDraft],
  );

  const createNote = async () => {
    if (creating) {
      return;
    }

    if (!latestDraftRef.current.title.trim()) {
      setSaveError({
        message: "Enter a title before creating this note.",
        fieldErrors: { title: "Title must not be blank" },
      });
      return;
    }

    setCreating(true);
    setSaveError(null);
    const result = await createKnowledgeAction(latestDraftRef.current);

    if (!result.ok) {
      setCreating(false);
      setSaveError(result.error);
      return;
    }

    router.replace(`/knowledge/${result.knowledge.slug}/edit`);
    router.refresh();
  };

  const openHistory = async () => {
    if (!initialKnowledge || openingHistory) return;
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    setOpeningHistory(true);
    if (savePromiseRef.current) await savePromiseRef.current;
    await flushSaveRef.current();
    if (!mountedRef.current) return;
    if (dirtyRef.current) {
      setOpeningHistory(false);
      return;
    }
    router.push(`/knowledge/${initialKnowledge.slug}/history`);
  };

  const settingsProps = {
    visibility: draft.visibility,
    collection: draft.collection,
    tags: draft.tags,
    collectionOptions,
    updatedAt: updatedAt ? formatKnowledgeDate(updatedAt, true) : "Not created yet",
    onVisibilityChange: changeVisibility,
    onCollectionChange: changeCollection,
    onTagsChange: changeTags,
  };

  return (
    <div className="mx-auto w-full max-w-[1160px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
      <div className="mb-7 flex flex-wrap items-center justify-between gap-3 border-b border-[var(--border)] pb-4">
        <Link
          href={mode === "edit" && initialKnowledge ? `/knowledge/${initialKnowledge.slug}` : "/"}
          className="inline-flex min-h-9 items-center text-[14px] font-medium text-[var(--text-muted)] underline-offset-4 transition-colors hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
        >
          ← {mode === "edit" ? "Reading" : "All notes"}
        </Link>

        <div className="flex items-center gap-2 sm:gap-3">
          {mode === "edit" ? (
            <>
              <span
                className={`mr-1 text-[12px] ${statusClassName(saveStatus)}`}
                aria-live="polite"
              >
                {saveStatus === "saved"
                  ? "Saved"
                  : saveStatus === "saving"
                    ? "Saving…"
                    : "Save failed"}
              </span>
              <Link
                href={`/knowledge/${initialKnowledge?.slug}`}
                className="inline-flex min-h-9 items-center px-2 text-[14px] font-medium text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
              >
                Preview
              </Link>
              <button
                type="button"
                disabled={openingHistory}
                onClick={() => void openHistory()}
                className="inline-flex min-h-9 items-center px-2 text-[14px] font-medium text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)] disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
              >
                {openingHistory ? "Opening…" : "History"}
              </button>
              {initialKnowledge && (
                <KnowledgeShareAction
                  slug={initialKnowledge.slug}
                  title={draft.title || initialKnowledge.title}
                  visibility={draft.visibility}
                  onVisibilityChange={changeVisibilityFromShare}
                  onLoadUnlistedLink={loadUnlistedLink}
                  onRegenerateUnlistedLink={regenerateUnlistedLink}
                />
              )}
            </>
          ) : (
            <button
              type="button"
              disabled={creating}
              onClick={() => void createNote()}
              className="inline-flex min-h-9 items-center border border-[var(--accent)] bg-[var(--accent)] px-3 text-[13px] font-medium text-[var(--accent-contrast)] transition-colors hover:bg-[var(--accent-strong)] disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
            >
              {creating ? "Creating…" : "Create"}
            </button>
          )}
        </div>
      </div>

      {saveError && (
        <p className="mb-5 border-l-2 border-[var(--danger)] pl-3 text-[12px] leading-5 text-[var(--danger)]" role="alert">
          {saveError.fieldErrors.title ?? saveError.message}
        </p>
      )}

      <div className="xl:grid xl:grid-cols-[minmax(0,760px)_240px] xl:gap-12">
        <main className="min-w-0">
          <p className="mb-5 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
            {draft.collection ?? "Unfiled"} / {mode === "edit" ? "Editing" : "New note"}
          </p>

          <label className="block" htmlFor="knowledge-title">
            <span className={fieldLabelClassName}>Title</span>
            <input
              id="knowledge-title"
              type="text"
              maxLength={255}
              value={draft.title}
              onChange={(event) =>
                changeDraft((current) => ({ ...current, title: event.target.value }))
              }
              aria-invalid={Boolean(saveError?.fieldErrors.title)}
              placeholder="A clear, durable title"
              className="mt-2 w-full border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-[28px] font-semibold leading-[1.15] tracking-[-0.025em] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--border-strong)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)] sm:text-[30px]"
            />
          </label>

          <label className="mt-5 block" htmlFor="knowledge-summary">
            <span className={fieldLabelClassName}>Summary</span>
            <textarea
              id="knowledge-summary"
              rows={3}
              maxLength={2000}
              value={draft.summary}
              onChange={(event) =>
                changeDraft((current) => ({ ...current, summary: event.target.value }))
              }
              placeholder="What should you remember about this note?"
              className="mt-2 w-full resize-y border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-[15px] leading-7 text-[var(--text-muted)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--border-strong)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]"
            />
          </label>

          <ArticleSettings compact {...settingsProps} />

          <section className="mt-7" aria-labelledby="content-label">
            <div className="flex items-end justify-between gap-4">
              <p id="content-label" className={fieldLabelClassName}>Content</p>
              <p className="text-[11px] text-[var(--text-subtle)]">
                {mode === "edit"
                  ? "Markdown · type / for blocks and images"
                  : "Markdown · create the note before adding images"}
              </p>
            </div>

            <div className="mt-2 border border-[var(--border)] bg-[var(--surface)] transition-colors focus-within:border-[var(--accent)] focus-within:ring-1 focus-within:ring-[var(--accent)]">
              <KnowledgeMarkdownEditor
                initialMarkdown={initialMarkdown}
                onMarkdownChange={changeMarkdown}
                knowledgeId={initialKnowledge?.id}
              />
            </div>
          </section>
        </main>

        <ArticleSettings {...settingsProps} />
      </div>
    </div>
  );
}
