import { KnowledgeEditor } from "@/components/knowledge/knowledge-editor";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { listKnowledge } from "@/lib/api/knowledge";
import { listCollections } from "@/lib/api/collections";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";
import { requireCurrentUser } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function NewKnowledgePage() {
  await requireCurrentUser();
  const [allKnowledge, collections] = await Promise.all([listKnowledge(), listCollections()]);

  return (
    <WorkspaceShell items={allKnowledge.map(toKnowledgeListItem)}>
      <KnowledgeEditor
        mode="create"
        collectionOptions={collections.map((collection) => collection.name)}
      />
    </WorkspaceShell>
  );
}
