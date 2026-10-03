export const MAX_QUESTION_CHARS = 2000;
export type AskErrorCode = "ASK_DISABLED" | "ASK_RETRIEVAL_UNAVAILABLE" | "ASK_UNAVAILABLE" | "VALIDATION_ERROR" | "UNAUTHENTICATED" | "ACCESS_DENIED";
export interface AskSource { id: number; title: string; slug: string; excerpt: string }
export interface AskResult { status: "ANSWERED" | "NO_CONTEXT"; answer: string | null; sources: AskSource[] }
export function askErrorCode(code: unknown): AskErrorCode {
  return ["ASK_DISABLED", "ASK_RETRIEVAL_UNAVAILABLE", "VALIDATION_ERROR", "UNAUTHENTICATED", "ACCESS_DENIED"].includes(String(code))
    ? code as AskErrorCode : "ASK_UNAVAILABLE";
}
export class AskError extends Error {
  readonly code: AskErrorCode;
  constructor(code: unknown) { super("Ask My Knowledge request failed"); this.code = askErrorCode(code); }
}
function record(value: unknown): value is Record<string, unknown> { return typeof value === "object" && value !== null; }
export function mapAskResponse(body: unknown): AskResult {
  if (!record(body) || !["ANSWERED", "NO_CONTEXT"].includes(String(body.status)) || !Array.isArray(body.sources) || body.sources.length > 30
    || (body.status === "ANSWERED" && (typeof body.answer !== "string" || !body.answer.trim() || body.answer.length > 65536 || body.sources.length === 0))
    || (body.status === "NO_CONTEXT" && (body.answer !== null || body.sources.length !== 0))) throw new AskError("ASK_UNAVAILABLE");
  const ids = new Set<number>();
  const sources = body.sources.map((source: unknown): AskSource => {
    if (!record(source) || !Number.isSafeInteger(source.id) || Number(source.id) <= 0 || ids.has(Number(source.id))
      || typeof source.title !== "string" || !source.title.trim() || source.title.length > 255
      || typeof source.slug !== "string" || source.slug.length > 255 || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(source.slug)
      || typeof source.excerpt !== "string" || source.excerpt.length > 600) throw new AskError("ASK_UNAVAILABLE");
    ids.add(Number(source.id));
    return { id: Number(source.id), title: source.title, slug: source.slug, excerpt: source.excerpt };
  });
  return { status: body.status as AskResult["status"], answer: body.answer as string | null, sources };
}
export function askSourceHref(source: AskSource) { return `/knowledge/${source.slug}`; }
export async function fetchAskKnowledge(question: string, signal?: AbortSignal, fetcher: typeof fetch = fetch): Promise<AskResult> {
  if (!question.trim() || question.length > MAX_QUESTION_CHARS) throw new AskError("VALIDATION_ERROR");
  const response = await fetcher("/api/ask-my-knowledge", { method: "POST", cache: "no-store", credentials: "same-origin", signal,
    headers: { Accept: "application/json", "Content-Type": "application/json" }, body: JSON.stringify({ question: question.trim() }) });
  if (!response.ok) {
    let code: unknown;
    try { const body: unknown = await response.json(); if (record(body)) code = body.code; } catch { /* no upstream data */ }
    throw new AskError(code);
  }
  try { return mapAskResponse(await response.json()); } catch { throw new AskError("ASK_UNAVAILABLE"); }
}
export interface AskState { draft: string; submittedQuestion: string; status: "idle" | "loading" | "success" | "error"; result?: AskResult; error?: AskErrorCode }
type AskTransport = (question: string, signal?: AbortSignal) => Promise<AskResult>;
/** Ephemeral explicit-submit state. No effects/timers, browser storage, URL mutation or automatic retry. */
export class AskSession {
  private state: AskState = { draft: "", submittedQuestion: "", status: "idle" };
  private listeners = new Set<() => void>();
  private sequence = 0;
  private active: AbortController | null = null;
  private transport: AskTransport;
  constructor(transport: AskTransport = fetchAskKnowledge) { this.transport = transport; }
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private publish(state: AskState) { this.state = state; this.listeners.forEach(listener => listener()); }
  draft(question: string) { this.publish({ ...this.state, draft: question }); }
  cancel() { this.sequence++; this.active?.abort(); this.active = null; }
  clear() { this.cancel(); this.publish({ draft: this.state.draft, submittedQuestion: "", status: "idle" }); }
  async submit() {
    if (this.active) return; // Every duplicate active submit is suppressed, including a changed draft.
    const question = this.state.draft.trim();
    if (!question || this.state.draft.length > MAX_QUESTION_CHARS) { this.publish({ ...this.state, status: "error", error: "VALIDATION_ERROR", result: undefined }); return; }
    const sequence = ++this.sequence;
    const controller = new AbortController(); this.active = controller;
    this.publish({ ...this.state, submittedQuestion: question, status: "loading", result: undefined, error: undefined });
    try {
      const result = await this.transport(question, controller.signal);
      if (sequence === this.sequence) this.publish({ ...this.state, status: "success", result, error: undefined });
    } catch (error) {
      if (sequence === this.sequence && !controller.signal.aborted)
        this.publish({ ...this.state, status: "error", result: undefined, error: error instanceof AskError ? error.code : "ASK_UNAVAILABLE" });
    } finally { if (sequence === this.sequence) this.active = null; }
  }
}
