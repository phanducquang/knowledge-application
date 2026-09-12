import { NextRequest, NextResponse } from "next/server";
import { KnowledgeApiError, searchKnowledge } from "@/lib/api/knowledge";

export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams.get("q") ?? "";
  const limitValue = request.nextUrl.searchParams.get("limit") ?? "20";
  const limit = Number(limitValue);

  if (!query.trim() || !Number.isInteger(limit) || limit < 1 || limit > 50) {
    return NextResponse.json(
      { code: "VALIDATION_ERROR", message: "Search query or limit is invalid" },
      { status: 400 },
    );
  }

  try {
    return NextResponse.json(await searchKnowledge(query, limit));
  } catch (error) {
    const status = error instanceof KnowledgeApiError ? error.status : 500;
    if (status === 401) {
      return NextResponse.json(
        { code: "UNAUTHENTICATED", message: "Authentication is required" },
        { status: 401 },
      );
    }
    if (status === 403) {
      return NextResponse.json(
        { code: "ACCESS_DENIED", message: "This account cannot access the workspace" },
        { status: 403 },
      );
    }
    const safeStatus = status >= 400 && status < 500 ? status : 503;
    return NextResponse.json(
      { code: "KNOWLEDGE_SEARCH_UNAVAILABLE", message: "Knowledge search is currently unavailable" },
      { status: safeStatus },
    );
  }
}
