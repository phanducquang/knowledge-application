import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MarkerType, ReactFlow } from "@xyflow/react";
import { KnowledgeGraphNode } from "../components/graph/knowledge-graph-node.ts";
import {
  directGraphNeighbors, filterKnowledgeGraph, focusedGraphNode, graphNoteHref,
  layoutKnowledgeGraph, prepareKnowledgeFlow,
} from "./knowledge-graph.ts";
import type { KnowledgeGraphData, KnowledgeGraphNodeData } from "../types/knowledge-graph.ts";

function note(id: number, collectionId: number | null): KnowledgeGraphNodeData {
  return { id, title: `Note ${id}`, slug: `note-${id}`, collectionId,
    collection: collectionId === null ? null : `Collection ${collectionId}`, tags: ["Java"], updatedAt: "2026-10-01T12:00:00Z" };
}
const graph: KnowledgeGraphData = {
  nodes: [note(1, 10), note(2, 10), note(3, 20), note(4, null), note(5, 10)],
  edges: [{ sourceId: 1, targetId: 2 }, { sourceId: 2, targetId: 1 }, { sourceId: 2, targetId: 3 }, { sourceId: 3, targetId: 4 }],
};

test("keeps compact DTO metadata, directed edges and isolated nodes in deterministic layout", () => {
  const original = JSON.stringify(graph);
  const positions = layoutKnowledgeGraph(graph);
  const flow = prepareKnowledgeFlow(graph, positions);
  assert.deepEqual(flow.nodes.map((node) => node.id), ["1", "2", "3", "4", "5"]);
  assert.deepEqual(flow.nodes[0].data.note, graph.nodes[0]);
  assert.deepEqual(flow.edges.map(({ source, target }) => [source, target]), [["1", "2"], ["2", "1"], ["2", "3"], ["3", "4"]]);
  assert.deepEqual(flow.edges[0].markerEnd, { type: MarkerType.ArrowClosed, color: "var(--border-strong)" });
  assert.deepEqual(layoutKnowledgeGraph(graph), positions);
  assert.ok([...positions.values()].every(({ x, y }) => Number.isFinite(x) && Number.isFinite(y)));
  assert.equal(JSON.stringify(graph), original);
});

test("filters by actual Collection ID and requires both endpoints to remain visible", () => {
  const filtered = filterKnowledgeGraph(graph, 10);
  assert.deepEqual(filtered.nodes.map((node) => node.id), [1, 2, 5]);
  assert.deepEqual(filtered.edges, graph.edges.slice(0, 2));
  assert.deepEqual(filterKnowledgeGraph(graph, null), graph);
  assert.deepEqual(filterKnowledgeGraph(graph, 999), { nodes: [], edges: [] });
  assert.equal(filterKnowledgeGraph(graph, 20).edges.length, 0);
});

test("focus highlights direct incoming/outgoing neighbors and incident edges only", () => {
  assert.equal(focusedGraphNode(graph, "note-2")?.id, 2);
  assert.deepEqual([...directGraphNeighbors(graph, 2)].sort(), [1, 2, 3]);
  const flow = prepareKnowledgeFlow(graph, layoutKnowledgeGraph(graph), "note-2");
  assert.equal(flow.nodes[1].data.focused, true);
  assert.equal(flow.nodes[0].data.neighbor, true);
  assert.equal(flow.nodes[2].data.neighbor, true);
  assert.equal(flow.nodes[3].style?.opacity, 0.35); // multi-hop is not a direct neighbor
  assert.equal(flow.nodes[4].style?.opacity, 0.35);
  assert.equal(flow.edges[2].style?.opacity, 1);
  assert.equal(flow.edges[3].style?.opacity, 0.25);
  assert.equal(focusedGraphNode(graph, "missing-note"), undefined);
  assert.ok(prepareKnowledgeFlow(graph, layoutKnowledgeGraph(graph), "missing-note").nodes.every((node) => node.style?.opacity === 1));
  assert.ok(prepareKnowledgeFlow(filterKnowledgeGraph(graph, 20), layoutKnowledgeGraph(graph), "note-2").nodes.every((node) => !node.data.focused));
});

test("renders actual graph nodes and arrows, including an isolated readable destination", () => {
  const flow = prepareKnowledgeFlow(graph, layoutKnowledgeGraph(graph));
  const html = renderToStaticMarkup(createElement(ReactFlow, {
    nodes: flow.nodes, edges: flow.edges, nodeTypes: { knowledge: KnowledgeGraphNode },
    width: 1000, height: 600, nodesConnectable: false, nodesDraggable: false,
  }));
  assert.match(html, /Note 5/);
  assert.match(html, /href="\/knowledge\/note-5"/);
  assert.match(html, /Collection 10/);
  assert.match(html, /Unfiled/);
  assert.match(html, /data-testid="rf__edge-1-2"/);
  assert.match(html, /data-testid="rf__edge-2-1"/);
  assert.match(html, /marker-end=/);
  assert.equal((html.match(/data-id="5"/g) ?? []).length, 1);
});

test("empty/single-node graphs remain valid and navigation encodes the stable slug", () => {
  const empty = { nodes: [], edges: [] };
  assert.deepEqual(prepareKnowledgeFlow(empty, layoutKnowledgeGraph(empty)), empty);
  const single = { nodes: [note(1, null)], edges: [] };
  assert.equal(prepareKnowledgeFlow(single, layoutKnowledgeGraph(single)).nodes.length, 1);
  assert.deepEqual([...directGraphNeighbors(single, 1)], [1]);
  assert.equal(graphNoteHref("note-1"), "/knowledge/note-1");
  assert.equal(graphNoteHref("unsafe/slug"), "/knowledge/unsafe%2Fslug");
});

test("uses authenticated no-store transport and isolates graph client state from external readers", () => {
  const page = readFileSync(new URL("../app/graph/page.tsx", import.meta.url), "utf8");
  const api = readFileSync(new URL("api/knowledge-graph.ts", import.meta.url), "utf8");
  const client = readFileSync(new URL("../components/graph/knowledge-graph.tsx", import.meta.url), "utf8");
  const sidebar = readFileSync(new URL("../components/layout/sidebar.tsx", import.meta.url), "utf8");
  assert.match(page, /await requireCurrentUser\(\)/);
  assert.match(page, /getKnowledgeGraph\(\)/);
  assert.match(page, /typeof focus === "string"/);
  assert.match(api, /import "server-only"/);
  assert.match(api, /backendRequest<KnowledgeGraphData>\("\/api\/knowledge\/graph"\)/);
  assert.match(client, /"use client"/);
  assert.match(client, /router.push\(graphNoteHref\(node.data.note.slug\)\)/);
  assert.match(client, /nodesConnectable=\{false\}/);
  assert.match(client, /deleteKeyCode=\{null\}/);
  assert.match(client, /panOnDrag zoomOnPinch/);
  assert.match(client, /Your graph is empty/);
  assert.match(client, /flex-wrap/);
  assert.match(sidebar, /href="\/graph" active=\{currentPath === "\/graph"\}/);
  for (const path of ["../app/knowledge/actions.ts", "../app/collections/actions.ts"]) {
    assert.match(readFileSync(new URL(path, import.meta.url), "utf8"), /revalidatePath\("\/graph"\)/);
  }
  for (const path of ["../components/knowledge/external-knowledge-article.tsx", "../app/k/[slug]/page.tsx", "../app/s/[shareToken]/page.tsx"]) {
    assert.doesNotMatch(readFileSync(new URL(path, import.meta.url), "utf8"), /KnowledgeGraph|getKnowledgeGraph|\/graph/);
  }
});
