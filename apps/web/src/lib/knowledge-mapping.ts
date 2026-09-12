import type {
  KnowledgeData,
  KnowledgeDraft,
  KnowledgeListItemData,
  KnowledgeVisibility,
} from "@/types/knowledge";

export type ApiKnowledgeVisibility = "PRIVATE" | "UNLISTED" | "PUBLIC";

export interface ApiKnowledgeResponse {
  id: number;
  title: string;
  slug: string;
  summary: string | null;
  content: string;
  visibility: ApiKnowledgeVisibility;
  collection: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface ApiKnowledgeWriteRequest {
  title: string;
  summary: string;
  content: string;
  visibility: ApiKnowledgeVisibility;
  collection: string | null;
  tags: string[];
}

export interface ApiKnowledgeSearchResultResponse {
  id: number;
  title: string;
  slug: string;
  summary: string | null;
  visibility: ApiKnowledgeVisibility;
  collection: string | null;
  tags: string[];
  updatedAt: string;
}

const apiToViewVisibility: Record<ApiKnowledgeVisibility, KnowledgeVisibility> = {
  PRIVATE: "Private",
  UNLISTED: "Unlisted",
  PUBLIC: "Public",
};

const viewToApiVisibility: Record<KnowledgeVisibility, ApiKnowledgeVisibility> = {
  Private: "PRIVATE",
  Unlisted: "UNLISTED",
  Public: "PUBLIC",
};

export function toApiKnowledgeVisibility(
  visibility: KnowledgeVisibility,
): ApiKnowledgeVisibility {
  return viewToApiVisibility[visibility];
}

export function mapApiKnowledge(response: ApiKnowledgeResponse): KnowledgeData {
  return {
    id: response.id,
    slug: response.slug,
    title: response.title,
    summary: response.summary ?? "",
    content: response.content ?? "",
    visibility: apiToViewVisibility[response.visibility],
    collection: response.collection ?? null,
    tags: response.tags ?? [],
    createdAt: response.createdAt,
    updatedAt: response.updatedAt,
    publishedAt: response.publishedAt ?? null,
  };
}

export function toApiKnowledgeWriteRequest(draft: KnowledgeDraft): ApiKnowledgeWriteRequest {
  return {
    title: draft.title,
    summary: draft.summary,
    content: draft.content,
    visibility: toApiKnowledgeVisibility(draft.visibility),
    collection: draft.collection,
    tags: [...draft.tags],
  };
}

export function formatKnowledgeDate(isoTimestamp: string, includeYear = false) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    ...(includeYear ? { year: "numeric" } : {}),
    timeZone: "UTC",
  }).format(new Date(isoTimestamp));
}

export function toKnowledgeListItem(knowledge: KnowledgeData): KnowledgeListItemData {
  return {
    id: knowledge.id,
    slug: knowledge.slug,
    title: knowledge.title,
    description: knowledge.summary,
    collection: knowledge.collection,
    tags: knowledge.tags,
    visibility: knowledge.visibility,
    updatedAt: formatKnowledgeDate(knowledge.updatedAt),
    updatedAtIso: knowledge.updatedAt,
    href: `/knowledge/${knowledge.slug}`,
  };
}

export function mapApiKnowledgeSearchResult(
  response: ApiKnowledgeSearchResultResponse,
): KnowledgeListItemData {
  return {
    id: response.id,
    slug: response.slug,
    title: response.title,
    description: response.summary ?? "",
    collection: response.collection ?? null,
    tags: response.tags ?? [],
    visibility: apiToViewVisibility[response.visibility],
    updatedAt: formatKnowledgeDate(response.updatedAt),
    updatedAtIso: response.updatedAt,
    href: `/knowledge/${response.slug}`,
  };
}

export function knowledgeCollectionOptions(items: KnowledgeData[]) {
  return Array.from(
    new Set(items.map((item) => item.collection).filter((value): value is string => Boolean(value))),
  ).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }));
}
