import { KnowledgeList } from "@/components/knowledge/knowledge-list";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { SearchInput } from "@/components/ui/search-input";
import { mockKnowledgeItems } from "@/data/mock-knowledge";

export default function Home() {
  return (
    <WorkspaceShell>
      <div className="mx-auto w-full max-w-[1040px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <header className="mb-8 border-b border-[var(--border)] pb-6 sm:flex sm:items-end sm:justify-between sm:gap-8">
          <div>
            <p className="mb-2 text-[12px] font-medium uppercase tracking-[0.08em] text-[var(--text-subtle)]">Workspace</p>
            <h1 className="text-[30px] font-semibold leading-[1.15] tracking-[-0.025em] text-[var(--text)] sm:text-[32px]">All notes</h1>
            <p className="mt-2 max-w-xl text-[14px] leading-6 text-[var(--text-muted)]">
              Technical notes, decisions, and references kept for daily engineering work.
            </p>
          </div>
          <button
            type="button"
            className="mt-5 hidden border border-[var(--text)] bg-[var(--text)] px-3 py-2 text-[13px] font-medium text-[var(--background)] transition-opacity hover:opacity-90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:block"
          >
            New note
          </button>
        </header>

        <div className="mb-8 max-w-[640px]">
          <SearchInput />
        </div>

        <KnowledgeList items={mockKnowledgeItems} />
      </div>
    </WorkspaceShell>
  );
}
