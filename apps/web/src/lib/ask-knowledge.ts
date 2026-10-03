export const MAX_QUESTION_CHARS = 2000;
export type AskErrorCode = "ASK_DISABLED" | "ASK_RETRIEVAL_UNAVAILABLE" | "ASK_UNAVAILABLE" | "VALIDATION_ERROR" | "UNAUTHENTICATED" | "ACCESS_DENIED";
export interface AskSource { id: number; title: string; slug: string }
export interface AskCitation { id: string; source: AskSource; chunkIndex: number; evidence: string }
export interface AskAnswerData { blocks: { markdown: string; citationIds: string[] }[] }
export interface AskResult { status: "ANSWERED" | "NO_CONTEXT"; answer: AskAnswerData | null; citations: AskCitation[] }
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
  const invalid = () => { throw new AskError("ASK_UNAVAILABLE"); };
  if (!record(body) || !["ANSWERED", "NO_CONTEXT"].includes(String(body.status)) || !Array.isArray(body.citations) || body.citations.length > 100) return invalid();
  if (body.status === "NO_CONTEXT") {
    if (body.answer !== null || body.citations.length !== 0) return invalid();
    return { status: "NO_CONTEXT", answer: null, citations: [] };
  }
  if (!record(body.answer) || !Array.isArray(body.answer.blocks) || !body.answer.blocks.length || body.answer.blocks.length > 24 || !body.citations.length) return invalid();
  const chunks = new Set<string>(); const notes = new Map<number, AskSource>();
  const citations = body.citations.map((citation: unknown, index: number): AskCitation => {
    if (!record(citation) || citation.id !== `C${index + 1}` || !record(citation.source) || !Number.isSafeInteger(citation.chunkIndex) || Number(citation.chunkIndex) < 0
      || typeof citation.evidence !== "string" || !citation.evidence.trim() || citation.evidence.length > 400) return invalid();
    const source = citation.source;
    if (!Number.isSafeInteger(source.id) || Number(source.id) <= 0
      || typeof source.title !== "string" || !source.title.trim() || source.title.length > 255
      || typeof source.slug !== "string" || source.slug.length > 255 || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(source.slug)) return invalid();
    const key = `${source.id}:${citation.chunkIndex}`; const previous = notes.get(Number(source.id));
    if (chunks.has(key) || (previous && (previous.title !== source.title || previous.slug !== source.slug))) return invalid();
    chunks.add(key); const safe = { id: Number(source.id), title: source.title, slug: source.slug }; notes.set(safe.id, safe);
    return { id: String(citation.id), source: safe, chunkIndex: Number(citation.chunkIndex), evidence: citation.evidence };
  });
  const known = new Set(citations.map(citation => citation.id)); const seen = new Set<string>();
  let chars = 0, occurrences = 0;
  const blocks = body.answer.blocks.map((block: unknown) => {
    if (!record(block) || typeof block.markdown !== "string" || !block.markdown.trim() || block.markdown.length > 8192
      || !Array.isArray(block.citationIds) || !block.citationIds.length || block.citationIds.length > citations.length
      || new Set(block.citationIds).size !== block.citationIds.length) return invalid();
    const ids: string[] = [];
    for (const id of block.citationIds) { if (typeof id !== "string" || !known.has(id)) return invalid(); ids.push(id); seen.add(id); }
    chars += block.markdown.length; occurrences += ids.length;
    if (chars > 65536 || occurrences > 128) return invalid();
    return { markdown: block.markdown, citationIds: ids };
  });
  if (seen.size !== citations.length || [...seen].some((id, index) => id !== citations[index].id)) return invalid();
  return { status: "ANSWERED", answer: { blocks }, citations };
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
