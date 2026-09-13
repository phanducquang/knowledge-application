"use client";

import { useEffect, useId, useRef, useState } from "react";
import {
  createMermaidRenderGuard,
  mermaidRenderId,
} from "@/lib/mermaid";

type DiagramState =
  | { status: "loading" }
  | { status: "ready"; svg: string }
  | { status: "error" };

type MermaidApi = (typeof import("mermaid"))["default"];

let mermaidPromise: Promise<MermaidApi> | null = null;

function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import("mermaid").then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: "strict",
        htmlLabels: false,
        suppressErrorRendering: true,
        maxTextSize: 50_000,
        maxEdges: 500,
        secure: [
          "secure",
          "securityLevel",
          "startOnLoad",
          "htmlLabels",
          "suppressErrorRendering",
          "maxTextSize",
          "maxEdges",
          "theme",
          "themeVariables",
          "themeCSS",
        ],
        theme: "base",
        themeVariables: {
          background: "#fcfbf7",
          primaryColor: "#ddeae6",
          primaryTextColor: "#1d211e",
          primaryBorderColor: "#557e79",
          secondaryColor: "#f1f0ea",
          secondaryTextColor: "#1d211e",
          secondaryBorderColor: "#c5c7bf",
          tertiaryColor: "#f7f5f0",
          tertiaryTextColor: "#1d211e",
          tertiaryBorderColor: "#d9d8d0",
          lineColor: "#557e79",
          textColor: "#1d211e",
          mainBkg: "#ddeae6",
          nodeBorder: "#557e79",
          clusterBkg: "#f1f0ea",
          clusterBorder: "#c5c7bf",
          edgeLabelBackground: "#fcfbf7",
          actorBkg: "#f1f0ea",
          actorBorder: "#557e79",
          actorTextColor: "#1d211e",
          signalColor: "#24534f",
          signalTextColor: "#1d211e",
          labelBoxBkgColor: "#fcfbf7",
          labelBoxBorderColor: "#c5c7bf",
          labelTextColor: "#1d211e",
          noteBkgColor: "#f1f0ea",
          noteBorderColor: "#936629",
          noteTextColor: "#1d211e",
          fontFamily: "var(--font-geist-sans), Arial, Helvetica, sans-serif",
        },
      });
      return mermaid;
    });
  }

  return mermaidPromise;
}

export function KnowledgeMermaidDiagram({ source }: { source: string }) {
  const reactId = useId();
  const guard = useRef<ReturnType<typeof createMermaidRenderGuard> | null>(null);
  const [state, setState] = useState<DiagramState>({ status: "loading" });

  if (guard.current == null) {
    guard.current = createMermaidRenderGuard();
  }

  useEffect(() => {
    const renderGuard = guard.current;
    if (!renderGuard) return;

    const attempt = renderGuard.begin();
    setState({ status: "loading" });

    void loadMermaid()
      .then((mermaid) => mermaid.render(mermaidRenderId(reactId, attempt), source))
      .then(({ svg }) => {
        if (renderGuard.isCurrent(attempt)) {
          setState({ status: "ready", svg });
        }
      })
      .catch(() => {
        if (renderGuard.isCurrent(attempt)) {
          setState({ status: "error" });
        }
      });

    return () => renderGuard.invalidate();
  }, [reactId, source]);

  if (state.status === "error") {
    return (
      <figure className="knowledge-mermaid my-6 max-w-full overflow-hidden border-y border-[var(--border-strong)] bg-[var(--surface-muted)]">
        <figcaption className="border-b border-[var(--border)] px-4 py-3 text-[12px] text-[var(--danger)]">
          Diagram could not be rendered. The Mermaid source is shown below.
        </figcaption>
        <pre className="max-w-full overflow-x-auto px-4 py-4 text-[13px] leading-6 text-[var(--text)]">
          <code>{source}</code>
        </pre>
      </figure>
    );
  }

  if (state.status === "loading") {
    return (
      <div
        className="knowledge-mermaid my-6 min-h-32 border-y border-[var(--border)] bg-[var(--surface-muted)] px-4 py-6 text-[12px] text-[var(--text-subtle)]"
        role="status"
        aria-live="polite"
      >
        Rendering diagram…
      </div>
    );
  }

  return (
    <figure
      className="knowledge-mermaid my-6 max-w-full overflow-x-auto border-y border-[var(--border)] bg-[var(--surface)] px-3 py-5 sm:px-5"
      aria-label="Mermaid diagram"
      // Mermaid generates this SVG locally under strict security; raw Markdown HTML remains disabled.
      dangerouslySetInnerHTML={{ __html: state.svg }}
    />
  );
}
