import "server-only";

import { cache } from "react";
import { backendRawResponse, backendRequest, BackendApiError } from "@/lib/api/backend";
import { mapApiKnowledge, type ApiKnowledgeResponse } from "@/lib/knowledge-mapping";
import type { CollectionData } from "@/types/collection";

export const listCollections = cache(() =>
  backendRequest<CollectionData[]>("/api/collections"),
);

export function getCollection(id: number) {
  return backendRequest<CollectionData>(`/api/collections/${id}`);
}

export async function listCollectionKnowledge(id: number) {
  const notes = await backendRequest<ApiKnowledgeResponse[]>(
    `/api/collections/${id}/knowledge`,
  );
  return notes.map(mapApiKnowledge);
}

export function createCollection(name: string) {
  return backendRequest<CollectionData>("/api/collections", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

export function renameCollection(id: number, name: string) {
  return backendRequest<CollectionData>(`/api/collections/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

export async function deleteCollection(id: number) {
  const response = await backendRawResponse(`/api/collections/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    let message = "The collection could not be deleted.";
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // A security or infrastructure response may have no JSON body.
    }
    throw new BackendApiError(response.status, "COLLECTION_DELETE_FAILED", {}, message);
  }
}
