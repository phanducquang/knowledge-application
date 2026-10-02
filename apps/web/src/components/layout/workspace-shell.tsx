import { WorkspaceShellClient } from "@/components/layout/workspace-shell-client";
import { listKnowledge } from "@/lib/api/knowledge";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";
import type { KnowledgeListItemData } from "@/types/knowledge";
import { getCurrentUser } from "@/lib/api/auth";
import { listCollections } from "@/lib/api/collections";

interface WorkspaceShellProps {
  children: React.ReactNode;
  items?: KnowledgeListItemData[];
}

export async function WorkspaceShell({ children, items }: WorkspaceShellProps) {
  const [currentUser, shellItems, collections] = await Promise.all([
    getCurrentUser(),
    items ? Promise.resolve(items) : listKnowledge().then((knowledge) => knowledge.map(toKnowledgeListItem)),
    listCollections(),
  ]);

  return <WorkspaceShellClient items={shellItems} collections={collections} currentUser={currentUser}>{children}</WorkspaceShellClient>;
}
