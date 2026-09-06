import Link from "next/link";
import { notFound } from "next/navigation";
import { ArticleContent } from "@/components/knowledge/article-content";
import { ArticleToc } from "@/components/knowledge/article-toc";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { Tag } from "@/components/ui/tag";
import { mockReferenceArticle } from "@/data/mock-knowledge";

export default async function KnowledgeReadingPage(props: { params: Promise<{ slug: string }> }) {
  const { slug } = await props.params;

  if (slug !== mockReferenceArticle.id) {
    notFound();
  }

  return (
    <WorkspaceShell>
      <div className="mx-auto w-full max-w-[1160px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <div className="mb-7 flex items-center justify-between gap-6 border-b border-[var(--border)] pb-4">
          <Link
            href="/"
            className="text-[12px] font-medium text-[var(--text-muted)] underline-offset-4 hover:text-[var(--accent-strong)] hover:underline"
          >
            ← All notes
          </Link>
          <div className="flex items-center gap-4 text-[12px]">
            <button type="button" className="text-[var(--text-muted)] transition-colors hover:text-[var(--accent-strong)]">
              Edit
            </button>
            <button
              type="button"
              className="border border-[var(--accent-muted)] px-3 py-1.5 font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--accent-soft)]"
            >
              Share
            </button>
          </div>
        </div>

        <div className="xl:grid xl:grid-cols-[minmax(0,760px)_180px] xl:gap-16">
          <article className="min-w-0">
            <header className="mb-8">
              <p className="mb-3 text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
                {mockReferenceArticle.collection} / Spring Boot
              </p>
              <h1 className="max-w-[720px] text-[34px] font-semibold leading-[1.08] tracking-[-0.035em] text-[var(--text)] sm:text-[38px]">
                {mockReferenceArticle.title}
              </h1>
              <p className="mt-5 max-w-[720px] text-[16px] leading-7 text-[var(--text-muted)] sm:text-[17px]">
                {mockReferenceArticle.lead}
              </p>

              <div className="mt-6 flex flex-wrap items-center gap-x-3 gap-y-2 border-y border-[var(--border)] py-3 text-[12px]">
                <span className="text-[var(--text-subtle)]">{mockReferenceArticle.visibility}</span>
                <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                <time className="tabular-nums text-[var(--text-subtle)]" dateTime={mockReferenceArticle.updatedAtIso}>
                  Updated {mockReferenceArticle.updatedAt}
                </time>
                <span aria-hidden="true" className="text-[var(--border-strong)]">/</span>
                <span className="text-[var(--text-subtle)]">{mockReferenceArticle.readTime}</span>
                <span className="hidden sm:inline" aria-hidden="true" />
                {mockReferenceArticle.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </div>
            </header>

            <ArticleToc />
            <ArticleContent />

            <footer className="mt-12 border-t border-[var(--border-strong)] pt-5">
              <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">Filed under</p>
              <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
                <span className="text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--accent-strong)]">
                  {mockReferenceArticle.collection}
                </span>
                {mockReferenceArticle.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </div>
            </footer>
          </article>

          <ArticleToc />
        </div>
      </div>
    </WorkspaceShell>
  );
}
