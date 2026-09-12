import { ArticleToc } from "@/components/knowledge/article-toc";
import { KnowledgeMarkdown } from "@/components/knowledge/knowledge-markdown";
import { Tag } from "@/components/ui/tag";
import { formatKnowledgeDate } from "@/lib/knowledge-mapping";
import { calculateReadTime, extractMarkdownHeadings } from "@/lib/markdown";

interface ExternalKnowledgeArticleData {
  title: string;
  summary: string;
  content: string;
  collection: string | null;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
}

interface ExternalKnowledgeArticleProps {
  article: ExternalKnowledgeArticleData;
  accessLabel: "Public note" | "Shared note";
}

export function ExternalKnowledgeArticle({
  article,
  accessLabel,
}: ExternalKnowledgeArticleProps) {
  const toc = extractMarkdownHeadings(article.content);
  const readTime = calculateReadTime(article.content);
  const topicLabel = article.tags[0] ?? "Knowledge";

  return (
    <main className="min-h-screen bg-[var(--background)] text-[var(--text)]">
      <div className="mx-auto w-full max-w-[1160px] px-5 py-6 sm:px-8 sm:py-8 lg:px-12 lg:py-10">
        <header className="mb-9 flex items-center justify-between border-b border-[var(--border)] pb-4">
          <span className="text-[14px] font-semibold tracking-[-0.015em]">Knowledge</span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
            {accessLabel}
          </span>
        </header>

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
                {article.publishedAt && (
                  <>
                    <time className="tabular-nums text-[var(--text-subtle)]" dateTime={article.publishedAt}>
                      Published {formatKnowledgeDate(article.publishedAt, true)}
                    </time>
                    <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                  </>
                )}
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
              <p className="py-8 text-[14px] italic text-[var(--text-subtle)]">
                This note has no Markdown content yet.
              </p>
            )}

            <footer className="mt-12 border-t border-[var(--border-strong)] pt-5">
              <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">
                Collection &amp; tags
              </p>
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
    </main>
  );
}
