import "server-only";
import { backendRequest } from "@/lib/api/backend";
import type { KnowledgeGraphData } from "@/types/knowledge-graph";

export function getKnowledgeGraph() {
  return backendRequest<KnowledgeGraphData>("/api/knowledge/graph");
}
