import "server-only";

import { cache } from "react";
import { publicBackendRequest } from "@/lib/api/backend";
import {
  mapApiSharedKnowledge,
  type ApiSharedKnowledgeResponse,
} from "@/lib/shared-knowledge";

export { BackendApiError as SharedKnowledgeApiError } from "@/lib/api/backend";

export const getSharedKnowledgeByToken = cache(async (shareToken: string) => {
  const response = await publicBackendRequest<ApiSharedKnowledgeResponse>(
    `/api/shared/knowledge/${encodeURIComponent(shareToken)}`,
  );
  return mapApiSharedKnowledge(response);
});
