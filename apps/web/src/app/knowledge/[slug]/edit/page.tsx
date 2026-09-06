import Link from "next/link";
import { notFound } from "next/navigation";
import { EditorToolbar } from "@/components/knowledge/editor-toolbar";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { mockReferenceArticle } from "@/data/mock-knowledge";

const mockMarkdown = `## Problem

A downstream search service can accept a connection and then take too long to produce a useful response. Without an explicit application-level timeout, that latency leaks into the caller and makes the failure mode harder to classify.

The timeout should belong to the reactive operation that owns the latency budget. Recovery should happen after that boundary so timeout, HTTP, and connection failures can still be observed as distinct causes.

## Timeout placement

Keep the happy-path transformation visible, then apply the timeout before the final recovery step.

\`\`\`java
handler.search(query)
    .timeout(handler.getTimeout())
    .doOnError(TimeoutException.class, ex ->
        log.warn("Search timed out. query={}", query))
    .doOnError(ex -> !(ex instanceof TimeoutException), ex ->
        log.error("Search request failed. query={}", query, ex))
    .map(result -> buildEvent("success", result))
    .onErrorResume(error -> Mono.just(
        buildEvent(error instanceof TimeoutException
            ? "timeout"
            : "error", null)
    ));
\`\`\`

> Treat timeout as a classification boundary, not as a reason to hide the original reactive flow behind a second recovery abstraction.

### Why this order matters

- \`timeout\` converts excessive latency into a deterministic error signal.
- \`doOnError\` observes the failure without consuming it.
- \`onErrorResume\` is the single place that converts the failure into the application result.

## Decision

1. Each search handler owns its timeout duration.
2. The aggregator applies the timeout to the handler call.
3. Logging observes the original error before fallback conversion.
4. One final recovery step maps the error into \`timeout\` or \`error\` status.
`;

const propertyLabelClassName =
  "text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]";

const propertyControlClassName =
  "mt-1.5 h-9 w-full border-0 border-b border-[var(--border-strong)] bg-transparent px-0 text-[13px] font-medium text-[var(--text)] outline-none transition-colors hover:border-[var(--accent-muted)] focus:border-[var(--accent)] focus:ring-0";

function ArticleSettings({ compact = false }: { compact?: boolean }) {
  const content = (
    <div className="space-y-5">
      <label className="block" htmlFor={compact ? "visibility-mobile" : "visibility-desktop"}>
        <span className={propertyLabelClassName}>Visibility</span>
        <select
          id={compact ? "visibility-mobile" : "visibility-desktop"}
          defaultValue={mockReferenceArticle.visibility}
          className={propertyControlClassName}
        >
          <option>Private</option>
          <option>Unlisted</option>
          <option>Public</option>
        </select>
      </label>

      <label className="block" htmlFor={compact ? "collection-mobile" : "collection-desktop"}>
        <span className={propertyLabelClassName}>Collection</span>
        <select
          id={compact ? "collection-mobile" : "collection-desktop"}
          defaultValue={mockReferenceArticle.collection}
          className={propertyControlClassName}
        >
          <option>Backend</option>
          <option>Database</option>
          <option>DevOps</option>
        </select>
      </label>

      <label className="block" htmlFor={compact ? "tags-mobile" : "tags-desktop"}>
        <span className={propertyLabelClassName}>Tags</span>
        <input
          id={compact ? "tags-mobile" : "tags-desktop"}
          type="text"
          defaultValue={mockReferenceArticle.tags.join(", ")}
          className={propertyControlClassName}
        />
        <span className="mt-1.5 block text-[10px] leading-4 text-[var(--text-subtle)]">Separate tags with commas.</span>
      </label>

      <div>
        <p className={propertyLabelClassName}>Updated</p>
        <p className="mt-2 text-[12px] tabular-nums text-[var(--text-muted)]">{mockReferenceArticle.updatedAt}</p>
        <p className="mt-1 text-[10px] leading-4 text-[var(--text-subtle)]">Managed automatically.</p>
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

export default async function KnowledgeEditorPage(props: { params: Promise<{ slug: string }> }) {
  const { slug } = await props.params;

  if (slug !== mockReferenceArticle.id) {
    notFound();
  }

  return (
    <WorkspaceShell>
      <div className="mx-auto w-full max-w-[1160px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <div className="mb-7 flex flex-wrap items-center justify-between gap-3 border-b border-[var(--border)] pb-4">
          <Link
            href={`/knowledge/${slug}`}
            className="inline-flex min-h-9 items-center text-[14px] font-medium text-[var(--text-muted)] underline-offset-4 transition-colors hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
          >
            ← Reading
          </Link>

          <div className="flex items-center gap-2 sm:gap-3">
            <span className="mr-1 text-[12px] text-[var(--success)]">Saved</span>
            <Link
              href={`/knowledge/${slug}`}
              className="inline-flex min-h-9 items-center px-2 text-[14px] font-medium text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
            >
              Preview
            </Link>
            <button
              type="button"
              className="inline-flex min-h-9 items-center border border-[var(--accent-muted)] px-3 text-[14px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--accent-soft)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
            >
              Share
            </button>
          </div>
        </div>

        <div className="xl:grid xl:grid-cols-[minmax(0,760px)_180px] xl:gap-16">
          <main className="min-w-0">
            <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
              {mockReferenceArticle.collection} / Editing
            </p>

            <label className="block" htmlFor="knowledge-title">
              <span className="sr-only">Knowledge title</span>
              <input
                id="knowledge-title"
                type="text"
                defaultValue={mockReferenceArticle.title}
                className="w-full border-0 bg-transparent p-0 text-[30px] font-semibold leading-[1.12] tracking-[-0.025em] text-[var(--text)] outline-none placeholder:text-[var(--text-subtle)] focus-visible:outline-none sm:text-[32px]"
              />
            </label>

            <label className="mt-4 block" htmlFor="knowledge-summary">
              <span className="sr-only">Knowledge summary</span>
              <textarea
                id="knowledge-summary"
                rows={2}
                defaultValue={mockReferenceArticle.lead}
                className="w-full resize-none border-0 bg-transparent p-0 text-[15px] leading-7 text-[var(--text-muted)] outline-none placeholder:text-[var(--text-subtle)] sm:text-[16px]"
              />
            </label>

            <ArticleSettings compact />

            <div className="mt-7">
              <EditorToolbar />
            </div>

            <label className="block" htmlFor="knowledge-content">
              <span className="sr-only">Markdown content</span>
              <textarea
                id="knowledge-content"
                defaultValue={mockMarkdown}
                spellCheck={false}
                className="min-h-[760px] w-full resize-y border-0 bg-transparent py-6 font-mono text-[14px] leading-7 text-[var(--text)] outline-none placeholder:text-[var(--text-subtle)]"
              />
            </label>

            <div className="border-t border-[var(--border)] pt-4 text-[11px] text-[var(--text-subtle)]">
              Reference screen only — title, summary, content and article settings are editable visual controls; autosave and persistence are placeholders until behavior is implemented.
            </div>
          </main>

          <ArticleSettings />
        </div>
      </div>
    </WorkspaceShell>
  );
}
