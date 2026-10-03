import "server-only";
import { backendRequest } from "@/lib/api/backend";
import { mapAskResponse, type AskResult } from "@/lib/ask-knowledge";

export async function askKnowledge(question: string, signal?: AbortSignal): Promise<AskResult> {
  // Existing POST transport forwards HttpOnly session and obtains SAME-session Spring CSRF token.
  return mapAskResponse(await backendRequest<unknown>("/api/ask", { method: "POST", signal,
    headers: { "Content-Type": "application/json" }, body: JSON.stringify({ question }) }));
}
