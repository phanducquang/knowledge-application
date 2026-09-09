import Link from "next/link";
import { notFound } from "next/navigation";
import { ArticleContent } from "@/components/knowledge/article-content";
import { ArticleToc } from "@/components/knowledge/article-toc";
import { KnowledgeShareAction } from "@/components/knowledge/knowledge-share-action";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { Tag } from "@/components/ui/tag";
import { getMockKnowledgeArticle, mockReferenceArticle } from "@/data/mock-knowledge";
import type { KnowledgeArticleData } from "@/types/knowledge";

function CompactMockArticleContent({ article }: { article: KnowledgeArticleData }) {
  return (
    <div className="article-content text-[16px] leading-[1.72] text-[var(--text)]">
      <section>
        <h2 className="mb-4 text-[22px] font-semibold leading-tight tracking-[-0.02em]">Overview</h2>
        <p className="text-[var(--text-muted)]">{article.description}</p>
      </section>

      <section className="mt-10">
        <h2 className="mb-4 text-[22px] font-semibold leading-tight tracking-[-0.02em]">Topics</h2>
        <div className="flex flex-wrap gap-x-3 gap-y-2">
          {article.tags.map((tag) => (
            <Tag key={tag}>{tag}</Tag>
          ))}
        </div>
      </section>
    </div>
  );
}

export default async function KnowledgeReadingPage(props: { params: Promise<{ slug: string }> }) {
  const { slug } = await props.params;
  const article = getMockKnowledgeArticle(slug);

  if (!article) {
    notFound();
  }

  const isReferenceArticle = article.id === mockReferenceArticle.id;
  const topicLabel = article.tags[0] ?? "Knowledge";

  return (
    <WorkspaceShell>
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
              href={`/knowledge/${slug}/edit`}
              className="inline-flex min-h-9 items-center px-2 text-[14px] font-medium text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
            >
              Edit
            </Link>
            <KnowledgeShareAction
              slug={article.id}
              title={article.title}
              initialVisibility={article.visibility}
            />
          </div>
        </div>

        <div className={isReferenceArticle ? "xl:grid xl:grid-cols-[minmax(0,760px)_180px] xl:gap-16" : "max-w-[760px]"}>
          <article className="min-w-0">
            <header className="mb-8">
              <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
                {article.collection} / {topicLabel}
              </p>
              <h1 className="max-w-[720px] text-[34px] font-semibold leading-[1.08] tracking-[-0.035em] text-[var(--text)] sm:text-[38px]">
                {article.title}
              </h1>
              <p className="mt-5 max-w-[720px] text-[16px] leading-7 text-[var(--text-muted)] sm:text-[17px]">
                {article.lead}
              </p>

              <div className="mt-6 flex flex-wrap items-center gap-x-3 gap-y-2 border-y border-[var(--border)] py-3 text-[12px]">
                <span className="text-[var(--text-subtle)]">{article.visibility}</span>
                <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                <time className="tabular-nums text-[var(--text-subtle)]" dateTime={article.updatedAtIso}>
                  Updated {article.updatedAt}
                </time>
                <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                <span className="text-[var(--text-subtle)]">{article.readTime}</span>
                <span className="hidden sm:inline" aria-hidden="true" />
                {article.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </div>
            </header>

            {isReferenceArticle ? (
              <>
                <ArticleToc variant="inline" />
                <ArticleContent />
              </>
            ) : (
              <CompactMockArticleContent article={article} />
            )}

            <footer className="mt-12 border-t border-[var(--border-strong)] pt-5">
              <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">Collection &amp; tags</p>
              <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
                <span className="text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--accent-strong)]">
                  {article.collection}
                </span>
                {article.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </div>
            </footer>
          </article>

          {isReferenceArticle && <ArticleToc variant="aside" />}
        </div>
      </div>
    </WorkspaceShell>
  );
}
