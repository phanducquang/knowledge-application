import { notFound } from "next/navigation";
import { KnowledgeHistory } from "@/components/knowledge/knowledge-history";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import {
  getKnowledgeBySlug,
  getKnowledgeRevision,
  KnowledgeApiError,
  listKnowledge,
  listKnowledgeRevisions,
} from "@/lib/api/knowledge";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";
import { requireCurrentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function KnowledgeHistoryPage(props: {
  params: Promise<{ slug: string }>;
}) {
  await requireCurrentUser();
  const { slug } = await props.params;
  let knowledge;
  let allKnowledge;
  let revisions;
  let initialRevision;

  try {
    [knowledge, allKnowledge] = await Promise.all([
      getKnowledgeBySlug(slug),
      listKnowledge(),
    ]);
    revisions = await listKnowledgeRevisions(knowledge.id);
    initialRevision = revisions.items[0]
      ? await getKnowledgeRevision(knowledge.id, revisions.items[0].id)
      : null;
  } catch (error) {
    if (error instanceof KnowledgeApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  return (
    <WorkspaceShell items={allKnowledge.map(toKnowledgeListItem)}>
      <KnowledgeHistory
        knowledge={knowledge}
        initialPage={revisions}
        initialRevision={initialRevision}
      />
    </WorkspaceShell>
  );
}
