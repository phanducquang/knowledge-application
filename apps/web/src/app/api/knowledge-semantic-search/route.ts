import { NextRequest, NextResponse } from "next/server";
import { BackendApiError } from "@/lib/api/backend";
import { searchSemanticKnowledge } from "@/lib/api/knowledge-semantic-search";
import { semanticErrorCode } from "@/lib/knowledge-semantic-search";

const headers = { "Cache-Control": "private, no-store, max-age=0" };
export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams.get("q") ?? "";
  const limit = Number(request.nextUrl.searchParams.get("limit") ?? "20");
  if (!query.trim() || query.length > 200 || !Number.isInteger(limit) || limit < 1 || limit > 50)
    return NextResponse.json({ code: "VALIDATION_ERROR", message: "Search query or limit is invalid" }, { status: 400, headers });
  try {
    return NextResponse.json(await searchSemanticKnowledge(query, limit, request.signal), { headers });
  } catch (error) {
    const status = error instanceof BackendApiError ? error.status : 503;
    const code = status === 401 ? "UNAUTHENTICATED" : status === 403 ? "ACCESS_DENIED"
      : semanticErrorCode(error instanceof BackendApiError ? error.code : undefined);
    return NextResponse.json({ code, message: "Semantic search is unavailable" }, {
      status: status === 401 || status === 403 || status === 400 ? status : 503, headers,
    });
  }
}
