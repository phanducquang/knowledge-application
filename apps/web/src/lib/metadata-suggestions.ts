export interface MetadataSuggestion { summary: string; tags: string[] }
export type MetadataErrorCode = "AI_METADATA_DISABLED" | "AI_METADATA_UNAVAILABLE" | "SAVE_REQUIRED" | "UNAUTHENTICATED" | "ACCESS_DENIED" | "KNOWLEDGE_NOT_FOUND";
export class MetadataError extends Error {
  readonly code: MetadataErrorCode;
  constructor(code: MetadataErrorCode = "AI_METADATA_UNAVAILABLE") {
    super(metadataErrorMessage(code)); this.code = code;
  }
}
export function metadataErrorCode(value: unknown): MetadataErrorCode {
  return ["AI_METADATA_DISABLED", "SAVE_REQUIRED", "UNAUTHENTICATED", "ACCESS_DENIED", "KNOWLEDGE_NOT_FOUND"].includes(String(value))
    ? value as MetadataErrorCode : "AI_METADATA_UNAVAILABLE";
}
export function metadataErrorMessage(code: MetadataErrorCode) {
  switch (code) {
    case "AI_METADATA_DISABLED": return "AI metadata suggestions are disabled in this workspace.";
    case "SAVE_REQUIRED": return "Save the latest edits successfully before requesting suggestions.";
    case "UNAUTHENTICATED": case "ACCESS_DENIED": return "Sign in as the workspace owner to request suggestions.";
    case "KNOWLEDGE_NOT_FOUND": return "This note is no longer available.";
    default: return "Suggestions are unavailable. The provider or quota may be unavailable. Try again explicitly later.";
  }
}
export function mapMetadataSuggestion(value: unknown): MetadataSuggestion {
  if (!value || typeof value !== "object") throw new MetadataError();
  const { summary, tags } = value as Record<string, unknown>;
  if (typeof summary !== "string" || !summary.trim() || summary.length > 500 || !Array.isArray(tags) || tags.length > 5
      || tags.some(t => typeof t !== "string" || !t.trim() || t.length > 50)) throw new MetadataError();
  return { summary: summary.trim(), tags: [...new Map((tags as string[]).map(t => [t.trim().toLowerCase(), t.trim()])).values()] };
}
export async function fetchMetadataSuggestion(id: number, signal?: AbortSignal, transport: typeof fetch = fetch) {
  if (!Number.isSafeInteger(id) || id < 1) throw new MetadataError("KNOWLEDGE_NOT_FOUND");
  try {
    const response = await transport("/api/knowledge-metadata-suggestions", { method: "POST", cache: "no-store", credentials: "same-origin", signal,
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ id }) });
    if (!response.ok) {
      let code: unknown; try { code = (await response.json()).code; } catch { /* sanitized below */ }
      throw new MetadataError(metadataErrorCode(code));
    }
    return mapMetadataSuggestion(await response.json());
  } catch (error) { if (error instanceof MetadataError) throw error; throw new MetadataError(); }
}
export function addSuggestedTags(existing: string[], suggested: string[]) {
  const seen = new Set(existing.map(t => t.trim().replace(/^#+/, "").trim().toLowerCase()));
  const next = [...existing];
  for (const tag of suggested) {
    const key = tag.trim().replace(/^#+/, "").trim().toLowerCase();
    if (!seen.has(key) && next.length < 20) { next.push(tag); seen.add(key); }
  }
  return next;
}
interface MetadataState { busy: boolean; result: MetadataSuggestion | null; error: MetadataErrorCode | null; sourceDraft: string | null }
export async function flushMetadataDraft(save: {
  pending: () => Promise<{ ok: boolean }> | null; flush: () => Promise<void>;
  blocked: () => boolean; dirty: () => boolean; saving: () => boolean; failed: () => boolean; key: () => string;
}) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const pending = save.pending();
    if (pending && !(await pending).ok) throw new MetadataError("SAVE_REQUIRED");
    if (save.blocked()) throw new MetadataError("SAVE_REQUIRED");
    await save.flush();
    if (save.failed()) throw new MetadataError("SAVE_REQUIRED");
    if (!save.dirty() && !save.saving()) return save.key();
  }
  throw new MetadataError("SAVE_REQUIRED");
}
/** Explicit-only request coordinator. No debounce, storage, save or generation on construction. */
export class MetadataSession {
  private state: MetadataState = { busy: false, result: null, error: null, sourceDraft: null };
  private listeners = new Set<() => void>();
  private version = 0;
  private controller?: AbortController;
  private readonly request: (signal: AbortSignal) => Promise<MetadataSuggestion>;
  constructor(request: (signal: AbortSignal) => Promise<MetadataSuggestion>) { this.request = request; }
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private update(patch: Partial<MetadataState>) { this.state = { ...this.state, ...patch }; this.listeners.forEach(l => l()); }
  async submit(flush: () => Promise<string>) {
    if (this.state.busy) return;
    const version = ++this.version; this.controller = new AbortController();
    this.update({ busy: true, result: null, error: null, sourceDraft: null });
    try {
      const sourceDraft = await flush();
      if (version !== this.version) return;
      const result = await this.request(this.controller.signal);
      if (version === this.version) this.update({ result, sourceDraft });
    } catch (error) {
      if (version === this.version) this.update({ error: error instanceof MetadataError ? error.code : "AI_METADATA_UNAVAILABLE" });
    } finally { this.update({ busy: false }); }
  }
  dismiss = () => { ++this.version; this.controller?.abort(); this.update({ result: null, error: null, sourceDraft: null }); };
}
