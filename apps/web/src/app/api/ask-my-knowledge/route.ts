import { NextResponse } from "next/server";
import { BackendApiError } from "@/lib/api/backend";
import { askKnowledge } from "@/lib/api/ask-knowledge";
import { askErrorCode } from "@/lib/ask-knowledge";
import { AskRequestError, readAskQuestion } from "@/lib/ask-request";

const headers = { "Cache-Control": "private, no-store, max-age=0" };
export async function POST(request: Request) {
  try {
    const question = await readAskQuestion(request);
    return NextResponse.json(await askKnowledge(question, request.signal), { headers });
  } catch (error) {
    const status = error instanceof AskRequestError || error instanceof BackendApiError ? error.status : 503;
    const code = status === 401 ? "UNAUTHENTICATED" : status === 403 ? "ACCESS_DENIED" : status === 400 ? "VALIDATION_ERROR"
      : askErrorCode(error instanceof BackendApiError ? error.code : undefined);
    return NextResponse.json({ code, message: "Ask My Knowledge request could not be completed" },
      { status: [400, 401, 403].includes(status) ? status : 503, headers });
  }
}
