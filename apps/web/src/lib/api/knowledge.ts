import "server-only";
import { cache } from "react";
import { backendRequest } from "@/lib/api/backend";
import {
  mapApiKnowledgeSearchResult,
  mapApiKnowledge,
  toApiKnowledgeWriteRequest,
  toApiKnowledgeVisibility,
  type ApiKnowledgeResponse,
  type ApiKnowledgeSearchResultResponse,
} from "@/lib/knowledge-mapping";
import type {
  KnowledgeDraft,
  KnowledgeRevisionDetail,
  KnowledgeRevisionPage,
  KnowledgeVisibility,
  UnlistedLinkData,
} from "@/types/knowledge";

export { BackendApiError as KnowledgeApiError } from "@/lib/api/backend";

export const listKnowledge = cache(async () => {
  const response = await backendRequest<ApiKnowledgeResponse[]>("/api/knowledge");
  return response.map(mapApiKnowledge);
});

export async function getKnowledgeBySlug(slug: string) {
  const response = await backendRequest<ApiKnowledgeResponse>(
    `/api/knowledge/slug/${encodeURIComponent(slug)}`,
  );
  return mapApiKnowledge(response);
}

export async function createKnowledge(draft: KnowledgeDraft) {
  const response = await backendRequest<ApiKnowledgeResponse>("/api/knowledge", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(toApiKnowledgeWriteRequest(draft)),
  });
  return mapApiKnowledge(response);
}

export async function updateKnowledge(id: number, draft: KnowledgeDraft) {
  const response = await backendRequest<ApiKnowledgeResponse>(`/api/knowledge/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(toApiKnowledgeWriteRequest(draft)),
  });
  return mapApiKnowledge(response);
}

export async function updateKnowledgeVisibility(
  id: number,
  visibility: KnowledgeVisibility,
) {
  const response = await backendRequest<ApiKnowledgeResponse>(
    `/api/knowledge/${id}/visibility`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ visibility: toApiKnowledgeVisibility(visibility) }),
    },
  );
  return mapApiKnowledge(response);
}

export async function getUnlistedLink(id: number) {
  return backendRequest<UnlistedLinkData>(`/api/knowledge/${id}/unlisted-link`);
}

export async function regenerateUnlistedLink(id: number) {
  return backendRequest<UnlistedLinkData>(
    `/api/knowledge/${id}/unlisted-link/regenerate`,
    { method: "POST" },
  );
}

export async function listKnowledgeRevisions(id: number, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return backendRequest<KnowledgeRevisionPage>(
    `/api/knowledge/${id}/revisions?${params.toString()}`,
  );
}

export async function getKnowledgeRevision(id: number, revisionId: number) {
  return backendRequest<KnowledgeRevisionDetail>(
    `/api/knowledge/${id}/revisions/${revisionId}`,
  );
}

export async function restoreKnowledgeRevision(id: number, revisionId: number) {
  const response = await backendRequest<ApiKnowledgeResponse>(
    `/api/knowledge/${id}/revisions/${revisionId}/restore`,
    { method: "POST" },
  );
  return mapApiKnowledge(response);
}

export async function searchKnowledge(query: string, limit = 20) {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  const response = await backendRequest<ApiKnowledgeSearchResultResponse[]>(
    `/api/search/knowledge?${params.toString()}`,
  );
  return response.map(mapApiKnowledgeSearchResult);
}
