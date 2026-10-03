"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ReactFlow, ReactFlowProvider, useNodesInitialized, useReactFlow } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { KnowledgeGraphNode } from "./knowledge-graph-node";
import {
  directGraphNeighbors, filterKnowledgeGraph, focusedGraphNode, graphNoteHref,
  layoutKnowledgeGraph, prepareKnowledgeFlow, type KnowledgeFlowNode,
} from "@/lib/knowledge-graph";
import type { KnowledgeGraphData } from "@/types/knowledge-graph";
import type { CollectionData } from "@/types/collection";

const nodeTypes = { knowledge: KnowledgeGraphNode };
const controlClass = "min-h-9 border border-[var(--border-strong)] bg-[var(--surface)] px-3 py-1.5 text-[13px] text-[var(--text-muted)] hover:text-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]";

interface GraphProps {
  graph: KnowledgeGraphData;
  collections: CollectionData[];
  initialFocus?: string;
}

function GraphWorkspace({ graph, collections, initialFocus }: GraphProps) {
  const router = useRouter();
  const { fitView, zoomIn, zoomOut } = useReactFlow<KnowledgeFlowNode>();
  const nodesInitialized = useNodesInitialized();
  const [collectionId, setCollectionId] = useState<number | null>(null);
  const [focusSlug, setFocusSlug] = useState(initialFocus ?? "");
  const visible = useMemo(() => filterKnowledgeGraph(graph, collectionId), [graph, collectionId]);
  const positions = useMemo(() => layoutKnowledgeGraph(visible), [visible]);
  const flow = useMemo(() => prepareKnowledgeFlow(visible, positions, focusSlug), [visible, positions, focusSlug]);
  const focused = focusedGraphNode(visible, focusSlug);
  const neighbors = useMemo(() => directGraphNeighbors(visible, focused?.id), [visible, focused?.id]);

  useEffect(() => {
    if (!nodesInitialized || visible.nodes.length === 0) return;
    // Wait for the controlled nodes/handles to reach the canvas before fitting.
    const frame = requestAnimationFrame(() => {
      void fitView({ padding: 0.2, maxZoom: 1, nodes: focused ? [...neighbors].map((id) => ({ id: String(id) })) : undefined });
    });
    return () => cancelAnimationFrame(frame);
  }, [visible, focused, neighbors, fitView, nodesInitialized]);

  if (graph.nodes.length === 0) {
    return <p className="py-12 text-[14px] text-[var(--text-muted)]">Your graph is empty. Create a note, then use [[stable-slug]] links to connect it to others.</p>;
  }

  return (
    <section aria-label="Interactive knowledge graph" className="min-w-0">
      <div className="mb-4 flex min-w-0 flex-wrap items-end gap-3">
        <label className="flex min-w-0 flex-col gap-1 text-[11px] text-[var(--text-subtle)]">
          Collection
          <select className={`${controlClass} w-[min(240px,100%)] max-w-full`} value={collectionId ?? ""} onChange={(event) => setCollectionId(event.target.value ? Number(event.target.value) : null)}>
            <option value="">All Collections</option>
            {collections.map((collection) => <option key={collection.id} value={collection.id}>{collection.name}</option>)}
          </select>
        </label>
        <label className="flex min-w-0 flex-col gap-1 text-[11px] text-[var(--text-subtle)]">
          Focus note
          <select className={`${controlClass} w-[min(280px,100%)] max-w-full`} value={focused?.slug ?? ""} onChange={(event) => setFocusSlug(event.target.value)}>
            <option value="">All visible notes</option>
            {visible.nodes.map((note) => <option key={note.id} value={note.slug}>{note.title}</option>)}
          </select>
        </label>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={controlClass} onClick={() => void zoomOut()} aria-label="Zoom out">−</button>
          <button type="button" className={controlClass} onClick={() => void zoomIn()} aria-label="Zoom in">+</button>
          <button type="button" className={controlClass} onClick={() => void fitView({ padding: 0.2, maxZoom: 1 })}>Fit view</button>
          {focused && <button type="button" className={controlClass} onClick={() => setFocusSlug("")}>Clear focus</button>}
        </div>
      </div>
      <p className="mb-3 text-[12px] leading-5 text-[var(--text-subtle)]" aria-live="polite">
        {visible.nodes.length} notes / {visible.edges.length} directed links. {focused ? `Focused: ${focused.title}. Direct neighbors are emphasized.` : "Drag the canvas to pan; pinch or use the zoom controls. Click a note to read."}
      </p>
      {visible.nodes.length === 0 ? (
        <p className="border-t border-[var(--border)] py-12 text-[14px] text-[var(--text-muted)]">No notes in this Collection.</p>
      ) : (
        <div className="h-[min(65vh,680px)] min-h-[360px] w-full min-w-0 border border-[var(--border)] bg-[var(--background)]">
          <ReactFlow
            nodes={flow.nodes} edges={flow.edges} nodeTypes={nodeTypes}
            onNodeClick={(_, node) => router.push(graphNoteHref(node.data.note.slug))}
            nodesDraggable={false} nodesConnectable={false} nodesFocusable={false} edgesFocusable={false}
            edgesReconnectable={false} elementsSelectable={false} deleteKeyCode={null}
            panOnDrag zoomOnPinch zoomOnScroll={false} preventScrolling={false}
            minZoom={0.1} maxZoom={2} fitView fitViewOptions={{ maxZoom: 1, padding: 0.2 }}
            aria-label="Current notes and directed wiki links"
          />
        </div>
      )}
    </section>
  );
}

export function KnowledgeGraph(props: GraphProps) {
  return <ReactFlowProvider key={props.initialFocus ?? ""}><GraphWorkspace {...props} /></ReactFlowProvider>;
}
