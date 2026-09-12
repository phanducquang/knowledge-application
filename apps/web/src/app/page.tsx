import Link from "next/link";
import { KnowledgeList } from "@/components/knowledge/knowledge-list";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { SearchInput } from "@/components/ui/search-input";
import { listKnowledge } from "@/lib/api/knowledge";
import { requireCurrentUser } from "@/lib/auth";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";

export const dynamic = "force-dynamic";

export default async function Home() {
  await requireCurrentUser();
  const items = (await listKnowledge()).map(toKnowledgeListItem);

  return (
    <WorkspaceShell items={items}>
      <div className="mx-auto w-full max-w-[1080px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <header className="mb-8 border-b border-[var(--border)] pb-6 sm:flex sm:items-end sm:justify-between sm:gap-10">
          <div>
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
              Knowledge / Library
            </p>
            <h1 className="text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[34px]">
              All notes
            </h1>
            <p className="mt-3 max-w-xl text-[14px] leading-6 text-[var(--text-muted)]">
              Technical notes, decisions, and references kept for daily engineering work.
            </p>
          </div>

          <div className="mt-6 flex items-end justify-between gap-6 sm:mt-0 sm:justify-end">
            <div className="border-l border-[var(--border-strong)] pl-4">
              <p className="text-[10px] font-medium uppercase tracking-[0.12em] text-[var(--text-subtle)]">Notes</p>
              <p className="mt-1 text-[28px] font-medium leading-none tabular-nums tracking-[-0.04em] text-[var(--accent)]">
                {String(items.length).padStart(2, "0")}
              </p>
            </div>
            <Link
              href="/knowledge/new"
              className="hidden border border-[var(--accent)] bg-[var(--accent)] px-3 py-2 text-[13px] font-medium text-[var(--accent-contrast)] transition-colors hover:bg-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:block"
            >
              New note
            </Link>
          </div>
        </header>

        <div className="mb-8 max-w-[640px]">
          <SearchInput />
        </div>

        <KnowledgeList items={items} />
      </div>
    </WorkspaceShell>
  );
}
