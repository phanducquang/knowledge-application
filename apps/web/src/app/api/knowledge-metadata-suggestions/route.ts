import { NextResponse } from "next/server";
import { BackendApiError } from "@/lib/api/backend";
import { suggestKnowledgeMetadata } from "@/lib/api/metadata-suggestions";
import { metadataErrorCode } from "@/lib/metadata-suggestions";
import { MetadataRequestError, readMetadataId } from "@/lib/metadata-request";

const headers = { "Cache-Control": "private, no-store, max-age=0" };
export async function POST(request: Request) {
  try { return NextResponse.json(await suggestKnowledgeMetadata(await readMetadataId(request), request.signal), { headers }); }
  catch (error) {
    const raw = error instanceof BackendApiError || error instanceof MetadataRequestError ? error.status : 503;
    const status = [400, 401, 403, 404].includes(raw) ? raw : 503;
    const code = status === 400 ? "VALIDATION_ERROR" : status === 401 ? "UNAUTHENTICATED" : status === 403 ? "ACCESS_DENIED"
      : status === 404 ? "KNOWLEDGE_NOT_FOUND" : metadataErrorCode(error instanceof BackendApiError ? error.code : undefined);
    return NextResponse.json({ code, message: "Metadata suggestions could not be completed" }, { status, headers });
  }
}
