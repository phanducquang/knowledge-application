import Link from "next/link";
import { notFound } from "next/navigation";
import { ArticleSettings } from "@/components/knowledge/article-settings";
import { KnowledgeMarkdownEditor } from "@/components/knowledge/knowledge-markdown-editor";
import { KnowledgeShareAction } from "@/components/knowledge/knowledge-share-action";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { getMockKnowledgeArticle, mockReferenceArticle } from "@/data/mock-knowledge";

const referenceMarkdown = `## Problem

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

const fieldLabelClassName =
  "text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]";

export default async function KnowledgeEditorPage(props: { params: Promise<{ slug: string }> }) {
  const { slug } = await props.params;
  const article = getMockKnowledgeArticle(slug);

  if (!article) {
    notFound();
  }

  const initialMarkdown =
    article.id === mockReferenceArticle.id
      ? referenceMarkdown
      : `## Overview\n\n${article.description}\n\n## Topics\n\n${article.tags.map((tag) => `- ${tag}`).join("\n")}\n`;

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
            <KnowledgeShareAction
              slug={article.id}
              title={article.title}
              initialVisibility={article.visibility}
            />
          </div>
        </div>

        <div className="xl:grid xl:grid-cols-[minmax(0,760px)_240px] xl:gap-12">
          <main className="min-w-0">
            <p className="mb-5 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
              {article.collection} / Editing
            </p>

            <label className="block" htmlFor="knowledge-title">
              <span className={fieldLabelClassName}>Title</span>
              <input
                id="knowledge-title"
                type="text"
                defaultValue={article.title}
                className="mt-2 w-full border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-[28px] font-semibold leading-[1.15] tracking-[-0.025em] text-[var(--text)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--border-strong)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)] sm:text-[30px]"
              />
            </label>

            <label className="mt-5 block" htmlFor="knowledge-summary">
              <span className={fieldLabelClassName}>Summary</span>
              <textarea
                id="knowledge-summary"
                rows={3}
                defaultValue={article.lead}
                className="mt-2 w-full resize-y border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-[15px] leading-7 text-[var(--text-muted)] outline-none transition-colors placeholder:text-[var(--text-subtle)] hover:border-[var(--border-strong)] focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]"
              />
            </label>

            <ArticleSettings
              compact
              initialVisibility={article.visibility}
              initialCollection={article.collection}
              initialTags={article.tags}
              updatedAt={article.updatedAt}
            />

            <section className="mt-7" aria-labelledby="content-label">
              <div className="flex items-end justify-between gap-4">
                <p id="content-label" className={fieldLabelClassName}>
                  Content
                </p>
                <p className="text-[11px] text-[var(--text-subtle)]">Markdown · type / for blocks</p>
              </div>

              <div className="mt-2 border border-[var(--border)] bg-[var(--surface)] transition-colors focus-within:border-[var(--accent)] focus-within:ring-1 focus-within:ring-[var(--accent)]">
                <KnowledgeMarkdownEditor initialMarkdown={initialMarkdown} />
              </div>
            </section>
          </main>

          <ArticleSettings
            initialVisibility={article.visibility}
            initialCollection={article.collection}
            initialTags={article.tags}
            updatedAt={article.updatedAt}
          />
        </div>
      </div>
    </WorkspaceShell>
  );
}
