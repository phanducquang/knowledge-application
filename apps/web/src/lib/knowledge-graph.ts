import dagre from "@dagrejs/dagre";
import { MarkerType, Position, type Node, type Edge } from "@xyflow/react";
import type { KnowledgeGraphData, KnowledgeGraphNodeData } from "../types/knowledge-graph.ts";

export const GRAPH_NODE_WIDTH = 224;
export const GRAPH_NODE_HEIGHT = 84;
export type KnowledgeFlowNode = Node<{ note: KnowledgeGraphNodeData; focused: boolean; neighbor: boolean }>;

export function graphNoteHref(slug: string) {
  return `/knowledge/${encodeURIComponent(slug)}`;
}

export function filterKnowledgeGraph(graph: KnowledgeGraphData, collectionId: number | null): KnowledgeGraphData {
  const nodes = collectionId === null ? graph.nodes : graph.nodes.filter((note) => note.collectionId === collectionId);
  const ids = new Set(nodes.map((node) => node.id));
  return { nodes, edges: graph.edges.filter((edge) => ids.has(edge.sourceId) && ids.has(edge.targetId)) };
}

export function focusedGraphNode(graph: KnowledgeGraphData, slug?: string) {
  return graph.nodes.find((node) => node.slug === slug);
}

export function directGraphNeighbors(graph: KnowledgeGraphData, focusedId?: number): Set<number> {
  const ids = new Set<number>();
  if (focusedId === undefined) return ids;
  ids.add(focusedId);
  for (const edge of graph.edges) {
    if (edge.sourceId === focusedId) ids.add(edge.targetId);
    if (edge.targetId === focusedId) ids.add(edge.sourceId);
  }
  return ids;
}

// Presentation-only layout. The DTO and canonical Markdown are never mutated.
export function layoutKnowledgeGraph(graph: KnowledgeGraphData) {
  const layout = new dagre.graphlib.Graph();
  layout.setGraph({ rankdir: "LR", nodesep: 36, ranksep: 100, marginx: 32, marginy: 32 });
  layout.setDefaultEdgeLabel(() => ({}));
  for (const note of graph.nodes) layout.setNode(String(note.id), { width: GRAPH_NODE_WIDTH, height: GRAPH_NODE_HEIGHT });
  for (const edge of graph.edges) layout.setEdge(String(edge.sourceId), String(edge.targetId));
  dagre.layout(layout);
  return new Map(graph.nodes.map((note) => {
    const point = layout.node(String(note.id));
    return [note.id, { x: point.x - GRAPH_NODE_WIDTH / 2, y: point.y - GRAPH_NODE_HEIGHT / 2 }];
  }));
}

export function prepareKnowledgeFlow(graph: KnowledgeGraphData, positions: ReturnType<typeof layoutKnowledgeGraph>, focusSlug?: string) {
  const focused = focusedGraphNode(graph, focusSlug);
  const neighbors = directGraphNeighbors(graph, focused?.id);
  const nodes: KnowledgeFlowNode[] = graph.nodes.map((note) => ({
    id: String(note.id), type: "knowledge",
    data: { note, focused: note.id === focused?.id, neighbor: neighbors.has(note.id) },
    position: positions.get(note.id) ?? { x: 0, y: 0 },
    width: GRAPH_NODE_WIDTH, height: GRAPH_NODE_HEIGHT,
    sourcePosition: Position.Right, targetPosition: Position.Left,
    handles: [
      { type: "source", position: Position.Right, x: GRAPH_NODE_WIDTH, y: GRAPH_NODE_HEIGHT / 2 },
      { type: "target", position: Position.Left, x: 0, y: GRAPH_NODE_HEIGHT / 2 },
    ],
    draggable: false, connectable: false, selectable: false,
    style: { opacity: !focused || neighbors.has(note.id) ? 1 : 0.35 },
  }));
  const edges: Edge[] = graph.edges.map(({ sourceId, targetId }) => {
    const emphasized = focused && (sourceId === focused.id || targetId === focused.id);
    const color = emphasized ? "var(--accent)" : "var(--border-strong)";
    return {
      id: `${sourceId}-${targetId}`, source: String(sourceId), target: String(targetId),
      type: "default", selectable: false,
      markerEnd: { type: MarkerType.ArrowClosed, color },
      style: { stroke: color, strokeWidth: emphasized ? 2 : 1.25, opacity: !focused || emphasized ? 1 : 0.25 },
    };
  });
  return { nodes, edges };
}
