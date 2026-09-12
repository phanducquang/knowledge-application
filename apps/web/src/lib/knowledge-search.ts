import type { KnowledgeListItemData } from "../types/knowledge.ts";

export const FULL_SEARCH_LIMIT = 20;
export const QUICK_SEARCH_LIMIT = 6;
export const SEARCH_DEBOUNCE_MS = 180;

type SearchFetch = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;

export function knowledgeSearchRequestUrl(query: string, limit: number) {
  const params = new URLSearchParams({ q: query.trim(), limit: String(limit) });
  return `/api/knowledge-search?${params.toString()}`;
}

export async function fetchKnowledgeSearch(
  query: string,
  limit: number,
  signal?: AbortSignal,
  fetcher: SearchFetch = fetch,
): Promise<KnowledgeListItemData[]> {
  const response = await fetcher(knowledgeSearchRequestUrl(query, limit), {
    method: "GET",
    headers: { Accept: "application/json" },
    signal,
  });

  if (!response.ok) {
    throw new Error("Knowledge search request failed");
  }

  const body: unknown = await response.json();
  if (!Array.isArray(body)) {
    throw new Error("Knowledge search returned an invalid response");
  }

  // PostgreSQL order is authoritative; intentionally do not re-rank here.
  return body as KnowledgeListItemData[];
}

export function viewAllKnowledgeSearchHref(query: string) {
  return `/search?q=${encodeURIComponent(query.trim())}`;
}

export class LatestSearchRequest {
  private sequence = 0;

  begin() {
    this.sequence += 1;
    return this.sequence;
  }

  invalidate() {
    this.sequence += 1;
  }

  isLatest(requestSequence: number) {
    return requestSequence === this.sequence;
  }
}
