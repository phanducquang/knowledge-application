import "server-only";
import { backendRequest } from "@/lib/api/backend";
import { mapSemanticApiResponse } from "@/lib/knowledge-semantic-search";

export async function searchSemanticKnowledge(query: string, limit = 20, signal?: AbortSignal) {
  const params = new URLSearchParams({ q: query.trim(), limit: String(limit) });
  const response = await backendRequest<unknown>(`/api/search/knowledge/semantic?${params}`, { signal });
  return mapSemanticApiResponse(response);
}
