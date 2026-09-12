import { KnowledgeSearch } from "@/components/search/knowledge-search";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { listKnowledge, searchKnowledge } from "@/lib/api/knowledge";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";
import { FULL_SEARCH_LIMIT } from "@/lib/knowledge-search";
import type { KnowledgeListItemData } from "@/types/knowledge";
import { requireCurrentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

interface SearchPageProps {
  searchParams: Promise<{ q?: string | string[] }>;
}

export default async function SearchPage({ searchParams }: SearchPageProps) {
  await requireCurrentUser();
  const params = await searchParams;
  const initialQuery = Array.isArray(params.q) ? (params.q[0] ?? "") : (params.q ?? "");
  const items = (await listKnowledge()).map(toKnowledgeListItem);
  let initialResults: KnowledgeListItemData[] = [];
  let initialSearchFailed = false;

  if (initialQuery.trim()) {
    try {
      initialResults = await searchKnowledge(initialQuery, FULL_SEARCH_LIMIT);
    } catch {
      initialSearchFailed = true;
    }
  }

  return (
    <WorkspaceShell items={items}>
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

        <KnowledgeSearch
          availableCount={items.length}
          initialQuery={initialQuery}
          initialResults={initialResults}
          initialSearchFailed={initialSearchFailed}
        />
      </div>
    </WorkspaceShell>
  );
}
