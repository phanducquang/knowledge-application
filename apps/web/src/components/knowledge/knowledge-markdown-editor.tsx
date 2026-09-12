"use client";

import { useEffect, useRef, useState } from "react";

type EditorStatus = "loading" | "ready" | "error";
type CrepeInstance = import("@milkdown/crepe").Crepe;

interface KnowledgeMarkdownEditorProps {
  initialMarkdown: string;
  name?: string;
  onMarkdownChange: (markdown: string) => void;
}

export function KnowledgeMarkdownEditor({
  initialMarkdown,
  name = "content",
  onMarkdownChange,
}: KnowledgeMarkdownEditorProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [markdown, setMarkdown] = useState(initialMarkdown);
  const [status, setStatus] = useState<EditorStatus>("loading");

  useEffect(() => {
    const root = rootRef.current;
    if (!root) {
      return;
    }

    let crepe: CrepeInstance | null = null;
    let cancelled = false;

    const mountEditor = async () => {
      try {
        const { Crepe } = await import("@milkdown/crepe");

        const instance = new Crepe({
          root,
          defaultValue: initialMarkdown,
          features: {
            [Crepe.Feature.TopBar]: true,
            [Crepe.Feature.AI]: false,
            // Image upload belongs to the storage phase. Keep the authoring
            // engine ready for it without exposing a control that cannot yet save.
            [Crepe.Feature.ImageBlock]: false,
          },
          featureConfigs: {
            [Crepe.Feature.Placeholder]: {
              text: "Type / for commands or start writing...",
              mode: "block",
            },
            [Crepe.Feature.TopBar]: {
              headingOptions: [
                { label: "Text", level: null },
                { label: "H2", level: 2 },
                { label: "H3", level: 3 },
                { label: "H4", level: 4 },
              ],
            },
            [Crepe.Feature.CodeMirror]: {
              searchPlaceholder: "Search language...",
              noResultText: "No matching language",
            },
          },
        });

        instance.on((listener) => {
          listener.markdownUpdated((_ctx, nextMarkdown, previousMarkdown) => {
            if (nextMarkdown !== previousMarkdown) {
              setMarkdown(nextMarkdown);
              onMarkdownChange(nextMarkdown);
            }
          });
        });

        await instance.create();

        if (cancelled) {
          instance.destroy();
          return;
        }

        crepe = instance;
        setStatus("ready");
      } catch (error) {
        console.error("Failed to initialize Milkdown Crepe editor", error);
        if (!cancelled) {
          setStatus("error");
        }
      }
    };

    void mountEditor();

    return () => {
      cancelled = true;
      crepe?.destroy();
      root.replaceChildren();
    };
  }, [initialMarkdown, onMarkdownChange]);

  return (
    <div className="knowledge-markdown-editor relative" data-editor-status={status}>
      <div ref={rootRef} />

      {status === "loading" && (
        <p className="pointer-events-none absolute left-4 top-4 text-[12px] text-[var(--text-subtle)]">
          Loading editor…
        </p>
      )}

      {status === "error" && (
        <div className="min-h-[360px] px-4 py-4 text-[13px] text-[var(--danger)]">
          The Markdown editor could not be initialized.
        </div>
      )}

      <textarea name={name} value={markdown} readOnly hidden aria-hidden="true" />
    </div>
  );
}
