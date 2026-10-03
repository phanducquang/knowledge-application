import { mapApiKnowledgeSearchResult, type ApiKnowledgeSearchResultResponse } from "./knowledge-mapping.ts";
import type { KnowledgeListItemData } from "../types/knowledge.ts";

export type SearchMode = "keyword" | "semantic";
export type SemanticErrorCode = "SEMANTIC_SEARCH_DISABLED" | "SEMANTIC_SEARCH_UNAVAILABLE" | "VALIDATION_ERROR" | "UNAUTHENTICATED" | "ACCESS_DENIED";
export interface SemanticKnowledgeResult extends KnowledgeListItemData { match: { chunkIndex: number; text: string } }
export interface ApiSemanticKnowledgeResult extends ApiKnowledgeSearchResultResponse { match: { chunkIndex: number; text: string } }
export type SemanticStatus = "idle" | "searching" | "success" | "error";
export interface SemanticState {
  draft: string;
  submittedQuery: string;
  results: SemanticKnowledgeResult[];
  status: SemanticStatus;
  error?: SemanticErrorCode;
}

export function resolveSearchMode(value?: string | string[]): SearchMode {
  return (Array.isArray(value) ? value[0] : value) === "semantic" ? "semantic" : "keyword";
}
export function semanticSearchHref(query: string) {
  return `/search?${new URLSearchParams({ mode: "semantic", ...(query.trim() ? { q: query.trim() } : {}) })}`;
}
export function semanticSearchRequestUrl(query: string, limit: number) {
  return `/api/knowledge-semantic-search?${new URLSearchParams({ q: query.trim(), limit: String(limit) })}`;
}
export function semanticErrorCode(code: unknown): SemanticErrorCode {
  return ["SEMANTIC_SEARCH_DISABLED", "VALIDATION_ERROR", "UNAUTHENTICATED", "ACCESS_DENIED"].includes(String(code))
    ? code as SemanticErrorCode : "SEMANTIC_SEARCH_UNAVAILABLE";
}
export class SemanticSearchError extends Error {
  readonly code: SemanticErrorCode;
  constructor(code: unknown) {
    super("Semantic search request failed");
    this.name = "SemanticSearchError";
    this.code = semanticErrorCode(code);
  }
}

function record(value: unknown): value is Record<string, unknown> { return value !== null && typeof value === "object"; }
function validMatch(value: unknown): value is { chunkIndex: number; text: string } {
  return record(value) && Number.isInteger(value.chunkIndex) && Number(value.chunkIndex) >= 0
    && typeof value.text === "string" && value.text.length <= 600;
}
export function mapSemanticApiResponse(body: unknown): SemanticKnowledgeResult[] {
  if (!Array.isArray(body) || body.length > 50) throw new SemanticSearchError("SEMANTIC_SEARCH_UNAVAILABLE");
  const ids = new Set<number>();
  return body.map((value: unknown) => {
    if (!record(value) || !Number.isSafeInteger(value.id) || Number(value.id) <= 0 || ids.has(Number(value.id))
      || typeof value.title !== "string" || typeof value.slug !== "string" || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value.slug)
      || !(value.summary === null || typeof value.summary === "string")
      || !["PRIVATE", "UNLISTED", "PUBLIC"].includes(String(value.visibility))
      || !(value.collection === null || typeof value.collection === "string")
      || !Array.isArray(value.tags) || !value.tags.every(tag => typeof tag === "string")
      || typeof value.updatedAt !== "string" || !Number.isFinite(Date.parse(value.updatedAt)) || !validMatch(value.match)) {
      throw new SemanticSearchError("SEMANTIC_SEARCH_UNAVAILABLE");
    }
    ids.add(Number(value.id));
    const response = value as unknown as ApiSemanticKnowledgeResult;
    // Construct an allowlisted view DTO; raw distance/vector/provider fields are never forwarded.
    return { ...mapApiKnowledgeSearchResult(response), match: { chunkIndex: response.match.chunkIndex, text: response.match.text } };
  });
}

export async function fetchSemanticKnowledge(query: string, limit: number, signal?: AbortSignal,
  fetcher: typeof fetch = fetch): Promise<SemanticKnowledgeResult[]> {
  const response = await fetcher(semanticSearchRequestUrl(query, limit), {
    method: "GET", cache: "no-store", headers: { Accept: "application/json" }, signal,
  });
  if (!response.ok) {
    let code: unknown;
    try { const body: unknown = await response.json(); if (record(body)) code = body.code; } catch { /* no upstream body surfaced */ }
    throw new SemanticSearchError(code);
  }
  const body: unknown = await response.json();
  if (!Array.isArray(body) || body.length > limit || body.some(value => !record(value) || !validMatch(value.match)
    || !Number.isSafeInteger(value.id) || typeof value.title !== "string" || typeof value.slug !== "string"
    || value.href !== `/knowledge/${value.slug}` || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value.slug)
    || typeof value.description !== "string" || !["Private", "Unlisted", "Public"].includes(String(value.visibility))
    || !(value.collection === null || typeof value.collection === "string") || !Array.isArray(value.tags)
    || !value.tags.every(tag => typeof tag === "string") || typeof value.updatedAt !== "string"
    || typeof value.updatedAtIso !== "string" || !Number.isFinite(Date.parse(value.updatedAtIso)))) {
    throw new SemanticSearchError("SEMANTIC_SEARCH_UNAVAILABLE");
  }
  return body as SemanticKnowledgeResult[];
}

type SearchTransport = (query: string, limit: number, signal?: AbortSignal) => Promise<SemanticKnowledgeResult[]>;
/** Explicit-submit state used by the UI and cost/cancellation regression tests. No timers or automatic retries. */
export class SemanticSearchSession {
  private state: SemanticState;
  private listeners = new Set<() => void>();
  private sequence = 0;
  private active: { query: string; controller: AbortController } | null = null;
  private transport: SearchTransport;
  constructor(initial: Partial<SemanticState> = {}, transport: SearchTransport = fetchSemanticKnowledge) {
    this.transport = transport;
    this.state = { draft: "", submittedQuery: "", results: [], status: "idle", ...initial };
  }
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private publish(state: SemanticState) { this.state = state; this.listeners.forEach(listener => listener()); }
  draft(query: string) { this.publish({ ...this.state, draft: query }); }
  cancel() { this.sequence++; this.active?.controller.abort(); this.active = null; }
  clear() { this.cancel(); this.publish({ draft: this.state.draft, submittedQuery: "", results: [], status: "idle" }); }
  async submit(limit: number) {
    const query = this.state.draft.trim();
    if (!query || query.length > 200) { this.cancel(); this.publish({ ...this.state, submittedQuery: query, results: [], status: "error", error: "VALIDATION_ERROR" }); return; }
    if (this.active?.query === query) return;
    this.cancel();
    const sequence = this.sequence;
    const controller = new AbortController();
    this.active = { query, controller };
    this.publish({ ...this.state, submittedQuery: query, results: [], status: "searching", error: undefined });
    try {
      const results = await this.transport(query, limit, controller.signal);
      if (sequence === this.sequence) this.publish({ ...this.state, submittedQuery: query, results, status: "success", error: undefined });
    } catch (error) {
      if (sequence === this.sequence && !controller.signal.aborted)
        this.publish({ ...this.state, submittedQuery: query, results: [], status: "error", error: error instanceof SemanticSearchError ? error.code : "SEMANTIC_SEARCH_UNAVAILABLE" });
    } finally { if (sequence === this.sequence) this.active = null; }
  }
}
