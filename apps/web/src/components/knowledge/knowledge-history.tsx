"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getKnowledgeRevisionAction,
  listKnowledgeRevisionsAction,
  restoreKnowledgeRevisionAction,
} from "@/app/knowledge/actions";
import { KnowledgeMarkdown } from "@/components/knowledge/knowledge-markdown";
import { Tag } from "@/components/ui/tag";
import type {
  KnowledgeData,
  KnowledgeRevisionDetail,
  KnowledgeRevisionPage,
  KnowledgeRevisionReason,
} from "@/types/knowledge";

interface KnowledgeHistoryProps {
  knowledge: KnowledgeData;
  initialPage: KnowledgeRevisionPage;
  initialRevision: KnowledgeRevisionDetail | null;
}

function reasonLabel(reason: KnowledgeRevisionReason) {
  if (reason === "CREATE") return "Created";
  if (reason === "BEFORE_RESTORE") return "Before restore";
  return "Checkpoint";
}

function revisionTime(value: string) {
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function KnowledgeHistory({
  knowledge,
  initialPage,
  initialRevision,
}: KnowledgeHistoryProps) {
  const router = useRouter();
  const [items, setItems] = useState(initialPage.items);
  const [nextPage, setNextPage] = useState(initialPage.page + 1);
  const [hasMore, setHasMore] = useState(initialPage.hasMore);
  const [selected, setSelected] = useState(initialRevision);
  const [selectedId, setSelectedId] = useState(initialRevision?.id ?? null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [confirmRestore, setConfirmRestore] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const detailRequestRef = useRef(0);

  const selectRevision = async (revisionId: number) => {
    if (revisionId === selectedId && selected) return;
    const request = detailRequestRef.current + 1;
    detailRequestRef.current = request;
    setSelectedId(revisionId);
    setSelected(null);
    setConfirmRestore(false);
    setLoadingDetail(true);
    setError(null);
    const result = await getKnowledgeRevisionAction(knowledge.id, revisionId);
    if (detailRequestRef.current !== request) return;
    setLoadingDetail(false);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    setSelected(result.revision);
  };

  const loadOlder = async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    setError(null);
    const result = await listKnowledgeRevisionsAction(knowledge.id, nextPage);
    setLoadingMore(false);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    setItems((current) => [...current, ...result.page.items]);
    setNextPage(result.page.page + 1);
    setHasMore(result.page.hasMore);
  };

  const restore = async () => {
    if (!selected || restoring) return;
    setRestoring(true);
    setError(null);
    const result = await restoreKnowledgeRevisionAction(knowledge.id, selected.id);
    if (!result.ok) {
      setRestoring(false);
      setError(result.error.message);
      return;
    }
    router.push(`/knowledge/${result.knowledge.slug}/edit`);
    router.refresh();
  };

  return (
    <div className="mx-auto w-full max-w-[1240px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
      <header className="mb-7 flex flex-wrap items-center justify-between gap-4 border-b border-[var(--border)] pb-4">
        <div>
          <Link
            href={`/knowledge/${knowledge.slug}/edit`}
            className="inline-flex min-h-9 items-center text-[13px] font-medium text-[var(--text-muted)] underline-offset-4 hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
          >
            ← Back to editor
          </Link>
          <h1 className="mt-2 text-[28px] font-semibold tracking-[-0.025em] text-[var(--text)]">
            Revision history
          </h1>
          <p className="mt-1 max-w-[720px] text-[13px] leading-5 text-[var(--text-subtle)]">
            {knowledge.title} · restore authoring content without changing its URL or sharing state.
          </p>
        </div>
        <Link
          href={`/knowledge/${knowledge.slug}`}
          className="inline-flex min-h-9 items-center px-2 text-[13px] font-medium text-[var(--text-muted)] hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          Read note
        </Link>
      </header>

      {error && (
        <p role="alert" className="mb-5 border-l-2 border-[var(--danger)] pl-3 text-[12px] leading-5 text-[var(--danger)]">
          {error}
        </p>
      )}

      {items.length === 0 ? (
        <section className="border border-[var(--border)] bg-[var(--surface)] px-6 py-12 text-center">
          <h2 className="text-[17px] font-semibold text-[var(--text)]">No revisions yet</h2>
          <p className="mt-2 text-[13px] text-[var(--text-subtle)]">
            Checkpoints will appear here as the note changes over time.
          </p>
        </section>
      ) : (
        <div className="grid gap-7 lg:grid-cols-[290px_minmax(0,1fr)] lg:gap-10">
          <aside aria-label="Revision list">
            <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">
              Newest first
            </p>
            <ol className="border-t border-[var(--border)]">
              {items.map((revision) => {
                const active = selectedId === revision.id;
                return (
                  <li key={revision.id} className="border-b border-[var(--border)]">
                    <button
                      type="button"
                      aria-current={active ? "true" : undefined}
                      onClick={() => void selectRevision(revision.id)}
                      className={`w-full px-3 py-4 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--accent)] ${
                        active ? "bg-[var(--surface-muted)]" : "hover:bg-[var(--surface-muted)]"
                      }`}
                    >
                      <span className="flex items-center justify-between gap-3">
                        <span className="text-[10px] font-semibold uppercase tracking-[0.1em] text-[var(--accent-strong)]">
                          {reasonLabel(revision.reason)}
                        </span>
                        <time dateTime={revision.createdAt} className="text-[10px] tabular-nums text-[var(--text-subtle)]">
                          {revisionTime(revision.createdAt)}
                        </time>
                      </span>
                      <span className="mt-2 block truncate text-[13px] font-medium text-[var(--text)]">
                        {revision.title}
                      </span>
                      {revision.summaryExcerpt && (
                        <span className="mt-1 line-clamp-2 block text-[11px] leading-4 text-[var(--text-subtle)]">
                          {revision.summaryExcerpt}
                        </span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ol>
            {hasMore && (
              <button
                type="button"
                disabled={loadingMore}
                onClick={() => void loadOlder()}
                className="mt-4 inline-flex min-h-9 w-full items-center justify-center border border-[var(--border-strong)] px-3 text-[12px] font-medium text-[var(--text-muted)] hover:border-[var(--accent)] hover:text-[var(--accent-strong)] disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
              >
                {loadingMore ? "Loading…" : "Load older revisions"}
              </button>
            )}
          </aside>

          <main className="min-w-0">
            {loadingDetail ? (
              <div aria-live="polite" className="border-t border-[var(--border)] py-12 text-[13px] text-[var(--text-subtle)]">
                Loading revision…
              </div>
            ) : selected ? (
              <article>
                <div className="flex flex-wrap items-start justify-between gap-5 border-b border-[var(--border)] pb-5">
                  <div>
                    <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
                      {reasonLabel(selected.reason)} · {revisionTime(selected.createdAt)}
                    </p>
                    <h2 className="mt-3 text-[26px] font-semibold leading-tight tracking-[-0.025em] text-[var(--text)]">
                      {selected.title}
                    </h2>
                    {selected.summary && (
                      <p className="mt-3 max-w-[720px] text-[14px] leading-6 text-[var(--text-muted)]">
                        {selected.summary}
                      </p>
                    )}
                    <div className="mt-4 flex flex-wrap items-center gap-2">
                      <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--text-subtle)]">
                        {selected.collection ?? "Unfiled"}
                      </span>
                      {selected.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setConfirmRestore(true)}
                    className="inline-flex min-h-9 items-center border border-[var(--accent)] px-3 text-[12px] font-medium text-[var(--accent-strong)] hover:bg-[var(--surface-muted)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
                  >
                    Restore this version
                  </button>
                </div>

                {confirmRestore && (
                  <div className="my-5 border-l-2 border-[var(--accent)] bg-[var(--surface-muted)] px-4 py-3" role="status">
                    <p className="text-[12px] leading-5 text-[var(--text-muted)]">
                      This replaces the current title, summary, Markdown, collection and tags. The current version is saved first; URL and sharing settings stay unchanged.
                    </p>
                    <div className="mt-3 flex gap-2">
                      <button
                        type="button"
                        disabled={restoring}
                        onClick={() => void restore()}
                        className="inline-flex min-h-9 items-center bg-[var(--accent)] px-3 text-[12px] font-medium text-[var(--accent-contrast)] hover:bg-[var(--accent-strong)] disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
                      >
                        {restoring ? "Restoring…" : "Confirm restore"}
                      </button>
                      <button
                        type="button"
                        disabled={restoring}
                        onClick={() => setConfirmRestore(false)}
                        className="inline-flex min-h-9 items-center px-3 text-[12px] font-medium text-[var(--text-muted)] hover:text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}

                <div className="mt-7">
                  {selected.content ? (
                    <KnowledgeMarkdown markdown={selected.content} />
                  ) : (
                    <p className="py-8 text-[14px] italic text-[var(--text-subtle)]">
                      This revision has no Markdown content.
                    </p>
                  )}
                </div>
              </article>
            ) : (
              <p className="border-t border-[var(--border)] py-12 text-[13px] text-[var(--text-subtle)]">
                Select a revision to preview it.
              </p>
            )}
          </main>
        </div>
      )}
    </div>
  );
}
