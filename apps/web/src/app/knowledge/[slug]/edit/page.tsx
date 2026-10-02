import { notFound } from "next/navigation";
import { KnowledgeEditor } from "@/components/knowledge/knowledge-editor";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import {
  getKnowledgeBySlug,
  KnowledgeApiError,
  listKnowledge,
} from "@/lib/api/knowledge";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";
import { listCollections } from "@/lib/api/collections";
import { requireCurrentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function KnowledgeEditorPage(props: {
  params: Promise<{ slug: string }>;
}) {
  await requireCurrentUser();
  const { slug } = await props.params;
  let knowledge;
  let allKnowledge;
  let collections;

  try {
    [knowledge, allKnowledge, collections] = await Promise.all([
      getKnowledgeBySlug(slug),
      listKnowledge(),
      listCollections(),
    ]);
  } catch (error) {
    if (error instanceof KnowledgeApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  return (
    <WorkspaceShell items={allKnowledge.map(toKnowledgeListItem)}>
      <KnowledgeEditor
        mode="edit"
        initialKnowledge={knowledge}
        collectionOptions={collections.map((collection) => collection.name)}
      />
    </WorkspaceShell>
  );
}
