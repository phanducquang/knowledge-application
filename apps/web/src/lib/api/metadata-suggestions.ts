import "server-only";
import { backendRequest } from "@/lib/api/backend";
import { mapMetadataSuggestion } from "@/lib/metadata-suggestions";

export async function suggestKnowledgeMetadata(id: number, signal?: AbortSignal) {
  return mapMetadataSuggestion(await backendRequest<unknown>(`/api/knowledge/${id}/ai/metadata-suggestions`, { method: "POST", signal }));
}
