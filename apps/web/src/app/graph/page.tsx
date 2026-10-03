import { KnowledgeGraph } from "@/components/graph/knowledge-graph";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { getKnowledgeGraph } from "@/lib/api/knowledge-graph";
import { listCollections } from "@/lib/api/collections";
import { requireCurrentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function GraphPage({ searchParams }: {
  searchParams: Promise<{ focus?: string | string[] }>;
}) {
  await requireCurrentUser();
  const [{ focus }, graph, collections] = await Promise.all([searchParams, getKnowledgeGraph(), listCollections()]);

  return (
    <WorkspaceShell>
      <div className="mx-auto w-full max-w-[1320px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <header className="mb-6 border-b border-[var(--border)] pb-5">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Knowledge / Relationships</p>
          <h1 className="text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[34px]">Knowledge Graph</h1>
          <p className="mt-3 max-w-2xl text-[14px] leading-6 text-[var(--text-muted)]">Current wiki links across your notes. Arrows follow the direction of each link; isolated notes remain visible.</p>
        </header>
        <KnowledgeGraph graph={graph} collections={collections} initialFocus={typeof focus === "string" ? focus : undefined} />
      </div>
    </WorkspaceShell>
  );
}
