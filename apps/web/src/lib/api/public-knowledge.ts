import "server-only";

import { cache } from "react";
import {
  publicBackendRequest,
} from "@/lib/api/backend";
import {
  mapApiPublicKnowledge,
  type ApiPublicKnowledgeResponse,
} from "@/lib/public-knowledge";

export { BackendApiError as PublicKnowledgeApiError } from "@/lib/api/backend";

export const getPublicKnowledgeBySlug = cache(async (slug: string) => {
  const response = await publicBackendRequest<ApiPublicKnowledgeResponse>(
    `/api/public/knowledge/${encodeURIComponent(slug)}`,
  );
  return mapApiPublicKnowledge(response);
});
