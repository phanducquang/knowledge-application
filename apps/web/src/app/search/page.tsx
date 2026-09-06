import { KnowledgeSearch } from "@/components/search/knowledge-search";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { mockKnowledgeItems } from "@/data/mock-knowledge";

interface SearchPageProps {
  searchParams: Promise<{ q?: string | string[] }>;
}

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;
  const initialQuery = Array.isArray(params.q) ? (params.q[0] ?? "") : (params.q ?? "");

  return (
    <WorkspaceShell>
      <div className="mx-auto w-full max-w-[1080px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <header className="mb-8 border-b border-[var(--border)] pb-6">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
            Knowledge / Search
          </p>
          <h1 className="text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[34px]">
            Search
          </h1>
          <p className="mt-3 max-w-2xl text-[14px] leading-6 text-[var(--text-muted)]">
            Find technical notes by title, summary, collection, or tag without leaving the knowledge workspace.
          </p>
        </header>

        <KnowledgeSearch items={mockKnowledgeItems} initialQuery={initialQuery} />
      </div>
    </WorkspaceShell>
  );
}
