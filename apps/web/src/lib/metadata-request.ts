export class MetadataRequestError extends Error {
  readonly status: number;
  constructor(status: number) { super("Invalid metadata suggestion request"); this.status = status; }
}
/** Focused JSON-only same-origin boundary; never accept note text or owner context. */
export async function readMetadataId(request: Request): Promise<number> {
  const origin = request.headers.get("origin");
  if (origin) {
    let source: URL; try { source = new URL(origin); } catch { throw new MetadataRequestError(403); }
    if (!["http:", "https:"].includes(source.protocol) || source.origin !== origin
        || source.host !== (request.headers.get("host") ?? new URL(request.url).host)) throw new MetadataRequestError(403);
  }
  if (request.headers.get("sec-fetch-site") === "cross-site") throw new MetadataRequestError(403);
  if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json"
      || Number(request.headers.get("content-length") ?? 0) > 512 || !request.body) throw new MetadataRequestError(400);
  const reader = request.body.getReader(); const parts: Uint8Array[] = []; let size = 0;
  try {
    for (;;) { const { done, value } = await reader.read(); if (done) break; size += value.byteLength;
      if (size > 512) { await reader.cancel(); throw new MetadataRequestError(400); } parts.push(value); }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
    const data: unknown = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    if (!data || typeof data !== "object" || Array.isArray(data) || Object.keys(data).length !== 1 || !("id" in data)
        || typeof data.id !== "number" || !Number.isSafeInteger(data.id) || data.id < 1) throw new MetadataRequestError(400);
    return data.id;
  } catch (error) { if (error instanceof MetadataRequestError) throw error; throw new MetadataRequestError(400); }
  finally { reader.releaseLock(); }
}
