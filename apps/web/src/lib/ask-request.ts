import { MAX_QUESTION_CHARS } from "./ask-knowledge.ts";

export class AskRequestError extends Error {
  readonly status: number;
  constructor(status: number) { super("Invalid Ask request"); this.status = status; }
}
/** Focused BFF anti-CSRF boundary: only same-origin JSON, bounded even without Content-Length. */
export async function readAskQuestion(request: Request): Promise<string> {
  const origin = request.headers.get("origin");
  // Next may canonicalize request.url to an INTERNAL hostname. Browser Host is the external target.
  // Do not trust an arbitrary X-Forwarded-Host header; deployment proxy must preserve Host.
  if (origin) {
    let source: URL;
    try { source = new URL(origin); } catch { throw new AskRequestError(403); }
    const host = request.headers.get("host") ?? new URL(request.url).host;
    if (!["http:", "https:"].includes(source.protocol) || source.origin !== origin || source.host !== host) throw new AskRequestError(403);
  }
  if (request.headers.get("sec-fetch-site") === "cross-site") throw new AskRequestError(403);
  if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") throw new AskRequestError(400);
  if (Number(request.headers.get("content-length") ?? 0) > 16384 || !request.body) throw new AskRequestError(400);
  const reader = request.body.getReader();
  const parts: Uint8Array[] = []; let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read(); if (done) break;
      size += value.byteLength;
      if (size > 16384) { await reader.cancel(); throw new AskRequestError(400); }
      parts.push(value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
    const body: unknown = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    if (!body || typeof body !== "object" || Array.isArray(body) || Object.keys(body).length !== 1 || !("question" in body)
      || typeof body.question !== "string" || !body.question.trim() || body.question.length > MAX_QUESTION_CHARS) throw new AskRequestError(400);
    return body.question.trim();
  } catch (error) { if (error instanceof AskRequestError) throw error; throw new AskRequestError(400); }
  finally { reader.releaseLock(); }
}
