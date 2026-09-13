"use client";

import { useEffect, useRef, useState } from "react";
import { resolveImageSource } from "@/lib/attachment-reference";

type EditorStatus = "loading" | "ready" | "error";
type CrepeInstance = import("@milkdown/crepe").Crepe;

interface KnowledgeMarkdownEditorProps {
  initialMarkdown: string;
  name?: string;
  onMarkdownChange: (markdown: string) => void;
  knowledgeId?: number;
}

export function KnowledgeMarkdownEditor({
  initialMarkdown,
  name = "content",
  onMarkdownChange,
  knowledgeId,
}: KnowledgeMarkdownEditorProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [markdown, setMarkdown] = useState(initialMarkdown);
  const [status, setStatus] = useState<EditorStatus>("loading");
  const [uploadError, setUploadError] = useState<string | null>(null);

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
        const uploadImage = async (file: File) => {
          if (!knowledgeId) {
            throw new Error("Create this note before uploading images");
          }
          setUploadError(null);
          const formData = new FormData();
          formData.set("file", file);
          const response = await fetch(`/api/knowledge/${knowledgeId}/attachments/images`, {
            method: "POST",
            body: formData,
          });
          if (!response.ok) {
            let message = "Image upload failed. Try again.";
            try {
              const error = (await response.json()) as { message?: string };
              if (error.message) message = error.message;
            } catch {
              // Preserve the safe generic message for non-JSON infrastructure failures.
            }
            setUploadError(message);
            throw new Error(message);
          }
          const attachment = (await response.json()) as { markdownSource: string };
          return attachment.markdownSource;
        };

        const instance = new Crepe({
          root,
          defaultValue: initialMarkdown,
          features: {
            [Crepe.Feature.TopBar]: true,
            [Crepe.Feature.AI]: false,
            [Crepe.Feature.ImageBlock]: Boolean(knowledgeId),
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
            ...(knowledgeId
              ? {
                  [Crepe.Feature.ImageBlock]: {
                    onUpload: uploadImage,
                    proxyDomURL: (source: string) =>
                      resolveImageSource(source, { kind: "owner", knowledgeId }),
                    onImageLoadError: () => {
                      setUploadError("The image could not be loaded. Check your session and try again.");
                    },
                  },
                }
              : {}),
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
  }, [initialMarkdown, knowledgeId, onMarkdownChange]);

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

      {uploadError && (
        <p className="border-t border-[var(--border)] px-4 py-2 text-[12px] text-[var(--danger)]" role="alert">
          {uploadError}
        </p>
      )}

      <textarea name={name} value={markdown} readOnly hidden aria-hidden="true" />
    </div>
  );
}
