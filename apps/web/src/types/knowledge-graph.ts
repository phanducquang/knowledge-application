export interface KnowledgeGraphNodeData {
  id: number;
  slug: string;
  title: string;
  collectionId: number | null;
  collection: string | null;
  tags: string[];
  updatedAt: string;
}

export interface KnowledgeGraphEdgeData {
  sourceId: number;
  targetId: number;
}

export interface KnowledgeGraphData {
  nodes: KnowledgeGraphNodeData[];
  edges: KnowledgeGraphEdgeData[];
}
