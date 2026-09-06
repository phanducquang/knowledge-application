export type KnowledgeVisibility = "Private" | "Unlisted" | "Public";

export interface KnowledgeListItemData {
  id: string;
  title: string;
  description: string;
  collection: string;
  tags: string[];
  visibility: KnowledgeVisibility;
  updatedAt: string;
  updatedAtIso: string;
}
