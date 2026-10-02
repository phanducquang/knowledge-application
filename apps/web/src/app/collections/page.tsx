import Link from "next/link";
import { CollectionManager } from "@/components/collections/collection-manager";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { listCollections } from "@/lib/api/collections";
import { listKnowledge } from "@/lib/api/knowledge";
import { requireCurrentUser } from "@/lib/auth";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";

export const dynamic = "force-dynamic";

export default async function CollectionsPage() {
  await requireCurrentUser();
  const [collections, knowledge] = await Promise.all([listCollections(), listKnowledge()]);

  return (
    <WorkspaceShell items={knowledge.map(toKnowledgeListItem)}>
      <div className="mx-auto w-full max-w-[1080px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <header className="mb-8 border-b border-[var(--border)] pb-6">
          <Link href="/" className="text-[13px] font-medium text-[var(--text-muted)] underline-offset-4 hover:text-[var(--accent-strong)] hover:underline">← All notes</Link>
          <p className="mb-2 mt-5 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Knowledge / Collections</p>
          <h1 className="text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[34px]">Collections</h1>
          <p className="mt-3 max-w-2xl text-[14px] leading-6 text-[var(--text-muted)]">Organize related notes. Empty collections stay available when you create or edit a note.</p>
        </header>
        <CollectionManager collections={collections} />
      </div>
    </WorkspaceShell>
  );
}
