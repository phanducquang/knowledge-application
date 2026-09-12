import { WorkspaceShellClient } from "@/components/layout/workspace-shell-client";
import { listKnowledge } from "@/lib/api/knowledge";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";
import type { KnowledgeListItemData } from "@/types/knowledge";
import { getCurrentUser } from "@/lib/api/auth";

interface WorkspaceShellProps {
  children: React.ReactNode;
  items?: KnowledgeListItemData[];
}

export async function WorkspaceShell({ children, items }: WorkspaceShellProps) {
  const [currentUser, shellItems] = await Promise.all([
    getCurrentUser(),
    items ? Promise.resolve(items) : listKnowledge().then((knowledge) => knowledge.map(toKnowledgeListItem)),
  ]);

  return <WorkspaceShellClient items={shellItems} currentUser={currentUser}>{children}</WorkspaceShellClient>;
}
