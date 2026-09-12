import Link from "next/link";
import { notFound } from "next/navigation";
import { ArticleToc } from "@/components/knowledge/article-toc";
import { KnowledgeMarkdown } from "@/components/knowledge/knowledge-markdown";
import { ReadingKnowledgeShareAction } from "@/components/knowledge/reading-knowledge-share-action";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { Tag } from "@/components/ui/tag";
import {
  getKnowledgeBySlug,
  KnowledgeApiError,
  listKnowledge,
} from "@/lib/api/knowledge";
import {
  formatKnowledgeDate,
  toKnowledgeListItem,
} from "@/lib/knowledge-mapping";
import { calculateReadTime, extractMarkdownHeadings } from "@/lib/markdown";
import { requireCurrentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function KnowledgeReadingPage(props: {
  params: Promise<{ slug: string }>;
}) {
  await requireCurrentUser();
  const { slug } = await props.params;
  let article;
  let allKnowledge;

  try {
    [article, allKnowledge] = await Promise.all([
      getKnowledgeBySlug(slug),
      listKnowledge(),
    ]);
  } catch (error) {
    if (error instanceof KnowledgeApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  const toc = extractMarkdownHeadings(article.content);
  const readTime = calculateReadTime(article.content);
  const topicLabel = article.tags[0] ?? "Knowledge";

  return (
    <WorkspaceShell items={allKnowledge.map(toKnowledgeListItem)}>
        <div className="mx-auto w-full max-w-[1160px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
          <div className="mb-7 flex items-center justify-between gap-6 border-b border-[var(--border)] pb-4">
            <Link
              href="/"
              className="inline-flex min-h-9 items-center text-[14px] font-medium text-[var(--text-muted)] underline-offset-4 transition-colors hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
            >
              ← All notes
            </Link>
            <div className="flex items-center gap-2 sm:gap-3">
              <Link
                href={`/knowledge/${article.slug}/history`}
                className="inline-flex min-h-9 items-center px-2 text-[14px] font-medium text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
              >
                History
              </Link>
              <Link
                href={`/knowledge/${article.slug}/edit`}
                className="inline-flex min-h-9 items-center px-2 text-[14px] font-medium text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
              >
                Edit
              </Link>
              <ReadingKnowledgeShareAction
                id={article.id}
                slug={article.slug}
                title={article.title}
                initialVisibility={article.visibility}
              />
            </div>
          </div>

          <div className={toc.length > 0 ? "xl:grid xl:grid-cols-[minmax(0,760px)_180px] xl:gap-16" : "max-w-[760px]"}>
            <article className="min-w-0">
              <header className="mb-8">
                <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
                  {article.collection ?? "Unfiled"} / {topicLabel}
                </p>
                <h1 className="max-w-[720px] text-[34px] font-semibold leading-[1.08] tracking-[-0.035em] text-[var(--text)] sm:text-[38px]">
                  {article.title}
                </h1>
                {article.summary && (
                  <p className="mt-5 max-w-[720px] text-[16px] leading-7 text-[var(--text-muted)] sm:text-[17px]">
                    {article.summary}
                  </p>
                )}

                <div className="mt-6 flex flex-wrap items-center gap-x-3 gap-y-2 border-y border-[var(--border)] py-3 text-[12px]">
                  <span className="text-[var(--text-subtle)]">{article.visibility}</span>
                  <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                  <time className="tabular-nums text-[var(--text-subtle)]" dateTime={article.updatedAt}>
                    Updated {formatKnowledgeDate(article.updatedAt, true)}
                  </time>
                  <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                  <span className="text-[var(--text-subtle)]">{readTime}</span>
                  <span className="hidden sm:inline" aria-hidden="true" />
                  {article.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                </div>
              </header>

              <ArticleToc variant="inline" sections={toc} />
              {article.content ? (
                <KnowledgeMarkdown markdown={article.content} />
              ) : (
                <p className="py-8 text-[14px] italic text-[var(--text-subtle)]">This note has no Markdown content yet.</p>
              )}

              <footer className="mt-12 border-t border-[var(--border-strong)] pt-5">
                <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">Collection &amp; tags</p>
                <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
                  <span className="text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--accent-strong)]">
                    {article.collection ?? "Unfiled"}
                  </span>
                  {article.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                </div>
              </footer>
            </article>

            <ArticleToc variant="aside" sections={toc} />
          </div>
        </div>
    </WorkspaceShell>
  );
}
