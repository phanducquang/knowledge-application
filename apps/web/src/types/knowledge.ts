export type KnowledgeVisibility = "Private" | "Unlisted" | "Public";

export interface KnowledgeDraft {
  title: string;
  summary: string;
  content: string;
  visibility: KnowledgeVisibility;
  collection: string | null;
  tags: string[];
}

export interface KnowledgeData extends KnowledgeDraft {
  id: number;
  slug: string;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface KnowledgeListItemData {
  id: number;
  slug: string;
  title: string;
  description: string;
  collection: string | null;
  tags: string[];
  visibility: KnowledgeVisibility;
  updatedAt: string;
  updatedAtIso: string;
  href: string;
}

export interface KnowledgeActionError {
  message: string;
  fieldErrors: Record<string, string>;
}

export interface UnlistedLinkData {
  token: string;
  path: string;
  createdAt: string;
}

export type KnowledgeActionResult =
  | { ok: true; knowledge: KnowledgeData }
  | { ok: false; error: KnowledgeActionError };

export type UnlistedLinkActionResult =
  | { ok: true; link: UnlistedLinkData }
  | { ok: false; error: KnowledgeActionError };

export type KnowledgeRevisionReason = "CREATE" | "CHECKPOINT" | "BEFORE_RESTORE";

export interface KnowledgeRevisionSummary {
  id: number;
  createdAt: string;
  reason: KnowledgeRevisionReason;
  title: string;
  summaryExcerpt: string | null;
}

export interface KnowledgeRevisionDetail {
  id: number;
  createdAt: string;
  reason: KnowledgeRevisionReason;
  title: string;
  summary: string | null;
  content: string;
  collection: string | null;
  tags: string[];
}

export interface KnowledgeRevisionPage {
  items: KnowledgeRevisionSummary[];
  page: number;
  size: number;
  hasMore: boolean;
}

export type KnowledgeRevisionPageActionResult =
  | { ok: true; page: KnowledgeRevisionPage }
  | { ok: false; error: KnowledgeActionError };

export type KnowledgeRevisionDetailActionResult =
  | { ok: true; revision: KnowledgeRevisionDetail }
  | { ok: false; error: KnowledgeActionError };
