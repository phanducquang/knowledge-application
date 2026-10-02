import Link from "next/link";
import { notFound } from "next/navigation";
import { KnowledgeList } from "@/components/knowledge/knowledge-list";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { getCollection, listCollectionKnowledge } from "@/lib/api/collections";
import { listKnowledge } from "@/lib/api/knowledge";
import { BackendApiError } from "@/lib/api/backend";
import { requireCurrentUser } from "@/lib/auth";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";

export const dynamic = "force-dynamic";

export default async function CollectionPage(props: { params: Promise<{ id: string }> }) {
  await requireCurrentUser();
  const { id: rawId } = await props.params;
  const id = Number(rawId);
  if (!Number.isSafeInteger(id) || id <= 0) notFound();

  let collection;
  let notes;
  let allKnowledge;
  try {
    [collection, notes, allKnowledge] = await Promise.all([
      getCollection(id),
      listCollectionKnowledge(id),
      listKnowledge(),
    ]);
  } catch (error) {
    if (error instanceof BackendApiError && error.status === 404) notFound();
    throw error;
  }

  return (
    <WorkspaceShell items={allKnowledge.map(toKnowledgeListItem)}>
      <div className="mx-auto w-full max-w-[1080px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
        <header className="mb-8 border-b border-[var(--border)] pb-6 sm:flex sm:items-end sm:justify-between sm:gap-8">
          <div>
            <Link href="/collections" className="text-[13px] font-medium text-[var(--text-muted)] underline-offset-4 hover:text-[var(--accent-strong)] hover:underline">← Collections</Link>
            <p className="mb-2 mt-5 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Knowledge / Collection</p>
            <h1 className="text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[34px]">{collection.name}</h1>
            <p className="mt-3 text-[14px] leading-6 text-[var(--text-muted)]">{collection.knowledgeCount} {collection.knowledgeCount === 1 ? "note" : "notes"} in this collection.</p>
          </div>
          <Link href="/collections" className="mt-5 inline-flex min-h-9 items-center text-[13px] font-medium text-[var(--accent-strong)] underline-offset-4 hover:underline sm:mt-0">Manage collections</Link>
        </header>
        <KnowledgeList
          items={notes.map(toKnowledgeListItem)}
          heading="In this collection"
          emptyTitle="This collection is empty"
          emptyDescription="Choose this collection while creating or editing a note to see it here."
        />
      </div>
    </WorkspaceShell>
  );
}
