"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import type {
  KnowledgeActionResult,
  KnowledgeVisibility,
  UnlistedLinkActionResult,
  UnlistedLinkData,
} from "@/types/knowledge";

interface KnowledgeShareActionProps {
  slug: string;
  title: string;
  visibility: KnowledgeVisibility;
  onVisibilityChange: (visibility: KnowledgeVisibility) => Promise<KnowledgeActionResult>;
  onLoadUnlistedLink: () => Promise<UnlistedLinkActionResult>;
  onRegenerateUnlistedLink: () => Promise<UnlistedLinkActionResult>;
}

const visibilityOptions: KnowledgeVisibility[] = ["Private", "Unlisted", "Public"];

const visibilityDescriptions: Record<KnowledgeVisibility, string> = {
  Private: "Only you can access this note. No external share link is available.",
  Unlisted: "Anyone with the secret link can view this note. It is not listed or indexed.",
  Public: "Anyone can view this note. The public page may be indexed by search engines.",
};

export function KnowledgeShareAction({
  slug,
  title,
  visibility,
  onVisibilityChange,
  onLoadUnlistedLink,
  onRegenerateUnlistedLink,
}: KnowledgeShareActionProps) {
  const triggerRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const requestSequenceRef = useRef(0);
  const [open, setOpen] = useState(false);
  const [origin, setOrigin] = useState("");
  const [unlistedLink, setUnlistedLink] = useState<UnlistedLinkData | null>(null);
  const [pending, setPending] = useState<"visibility" | "link" | "regenerate" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmRegeneration, setConfirmRegeneration] = useState(false);
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");

  const shareUrl = useMemo(() => {
    if (!origin) return null;
    if (visibility === "Public") return `${origin}/k/${slug}`;
    if (visibility === "Unlisted" && unlistedLink) return `${origin}${unlistedLink.path}`;
    return null;
  }, [origin, slug, unlistedLink, visibility]);

  const loadUnlistedLink = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    setPending("link");
    setError(null);
    const result = await onLoadUnlistedLink();
    if (requestSequence !== requestSequenceRef.current) return;

    if (result.ok) setUnlistedLink(result.link);
    else setError(result.error.message);
    setPending(null);
  }, [onLoadUnlistedLink]);

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const trigger = triggerRef.current;
    document.body.style.overflow = "hidden";
    const frame = window.requestAnimationFrame(() => {
      dialogRef.current?.querySelector<HTMLElement>('[role="radio"][aria-checked="true"]')?.focus();
    });
    return () => {
      window.cancelAnimationFrame(frame);
      document.body.style.overflow = previousOverflow;
      if (trigger?.isConnected) trigger.focus();
    };
  }, [open]);

  useEffect(() => {
    if (!open || pending || dialogRef.current?.contains(document.activeElement)) return;
    const frame = window.requestAnimationFrame(() => {
      dialogRef.current?.querySelector<HTMLElement>('[role="radio"][aria-checked="true"]')?.focus();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [open, pending, visibility]);

  useEffect(() => {
    if (copyState !== "copied") return;
    const timeout = window.setTimeout(() => setCopyState("idle"), 1800);
    return () => window.clearTimeout(timeout);
  }, [copyState]);

  const closeDialog = () => {
    requestSequenceRef.current += 1;
    setOpen(false);
  };

  const trapTabFocus = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "Tab") return;
    const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])') ?? [])
      .filter((element) => element.tabIndex >= 0 && element.getClientRects().length > 0);
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  };

  const persistVisibility = async (nextVisibility: KnowledgeVisibility) => {
    if (nextVisibility === visibility || pending) return;
    const requestSequence = ++requestSequenceRef.current;
    setPending("visibility");
    setError(null);
    setCopyState("idle");
    setConfirmRegeneration(false);
    const result = await onVisibilityChange(nextVisibility);
    if (requestSequence !== requestSequenceRef.current) return;

    if (!result.ok) {
      setError(result.error.message);
      setPending(null);
      return;
    }
    if (nextVisibility !== "Unlisted") setUnlistedLink(null);
    setPending(null);
    if (nextVisibility === "Unlisted") void loadUnlistedLink();
  };

  const regenerateLink = async () => {
    if (!confirmRegeneration) {
      setConfirmRegeneration(true);
      setError(null);
      return;
    }
    const requestSequence = ++requestSequenceRef.current;
    setPending("regenerate");
    setError(null);
    const result = await onRegenerateUnlistedLink();
    if (requestSequence !== requestSequenceRef.current) return;

    if (result.ok) {
      setUnlistedLink(result.link);
      setConfirmRegeneration(false);
      setCopyState("idle");
    } else setError(result.error.message);
    setPending(null);
  };

  const copyShareUrl = async () => {
    if (!shareUrl) return;
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopyState("copied");
    } catch {
      setCopyState("failed");
    }
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => {
          requestSequenceRef.current += 1;
          setOrigin(window.location.origin);
          setUnlistedLink(null);
          setPending(null);
          setError(null);
          setConfirmRegeneration(false);
          setCopyState("idle");
          setOpen(true);
          if (visibility === "Unlisted") void loadUnlistedLink();
        }}
        className="inline-flex min-h-11 sm:min-h-9 items-center border border-[var(--accent-muted)] px-3 text-[14px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--accent-soft)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:text-[13px]"
      >Share</button>

      {open && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center px-4 py-4 sm:px-6 sm:py-6">
          <button type="button" tabIndex={-1} aria-label="Close share dialog" onClick={closeDialog} className="absolute inset-0 cursor-default bg-[var(--overlay-backdrop)]" />
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="share-dialog-title"
            aria-describedby="share-dialog-description"
            aria-busy={pending !== null}
            onKeyDown={(event) => {
              trapTabFocus(event);
              if (event.key === "Escape") {
                event.preventDefault();
                closeDialog();
              }
            }}
            className="relative max-h-[calc(100dvh-2rem)] w-full max-w-[520px] overflow-y-auto overscroll-contain rounded-[6px] border border-[var(--border-strong)] bg-[var(--surface)] shadow-[var(--shadow-dialog)] sm:max-h-[calc(100dvh-3rem)]"
          >
            <div className="flex items-start justify-between gap-6 border-b border-[var(--border)] px-5 py-4 sm:px-6 sm:py-5">
              <div className="min-w-0">
                <p className="text-[12px] font-medium text-[var(--text-subtle)]">Share note</p>
                <h2 id="share-dialog-title" className="mt-1.5 break-words text-[20px] font-medium tracking-[-0.02em] text-[var(--text)] sm:text-[22px]">{title}</h2>
                <p id="share-dialog-description" className="sr-only">Choose who can access this note and copy its share link when one is available.</p>
              </div>
              <button type="button" onClick={closeDialog} aria-label="Close share dialog" className="inline-flex h-11 w-11 shrink-0 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--row-hover)] hover:text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] sm:h-8 sm:w-8">
                <svg viewBox="0 0 16 16" aria-hidden="true" className="h-4 w-4 fill-none stroke-current" strokeWidth="1.5" strokeLinecap="round"><path d="M4 4l8 8M12 4l-8 8" /></svg>
              </button>
            </div>

            <div className="px-5 py-5 sm:px-6">
              <section aria-labelledby="share-visibility-label">
                <h3 id="share-visibility-label" className="text-[12px] font-medium text-[var(--text-subtle)]">Visibility</h3>
                <div className="mt-2 flex w-full border-b border-[var(--border)]" role="radiogroup" aria-label="Share visibility" onKeyDown={(event) => {
                  const direction = event.key === "ArrowRight" || event.key === "ArrowDown" ? 1
                    : event.key === "ArrowLeft" || event.key === "ArrowUp" ? -1 : 0;
                  if (pending || (!direction && event.key !== "Home" && event.key !== "End")) return;
                  event.preventDefault();
                  const currentIndex = visibilityOptions.indexOf(visibility);
                  const nextIndex = event.key === "Home" ? 0 : event.key === "End" ? visibilityOptions.length - 1
                    : (currentIndex + direction + visibilityOptions.length) % visibilityOptions.length;
                  event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]?.focus();
                  void persistVisibility(visibilityOptions[nextIndex]);
                }}>
                  {visibilityOptions.map((option) => {
                    const selected = visibility === option;
                    return (
                      <button key={option} type="button" role="radio" aria-checked={selected} tabIndex={selected ? 0 : -1} disabled={pending !== null} onClick={() => void persistVisibility(option)} className={`relative min-h-11 min-w-0 flex-1 whitespace-nowrap px-1 pb-2 pt-1 text-center text-[14px] font-medium leading-5 transition-colors disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${selected ? "text-[var(--accent-strong)]" : "text-[var(--text-muted)] hover:text-[var(--text)]"}`}>
                        {option}<span aria-hidden="true" className={`absolute inset-x-2 -bottom-px h-px ${selected ? "bg-[var(--accent)]" : "bg-transparent"}`} />
                      </button>
                    );
                  })}
                </div>
                <p className="mt-3 text-[13px] leading-5 text-[var(--text-muted)]">{visibilityDescriptions[visibility]}</p>
                {pending === "visibility" && <p className="mt-2 text-[12px] text-[var(--text-subtle)]" aria-live="polite">Saving visibility…</p>}
                {error && <p className="mt-2 border-l-2 border-[var(--danger)] pl-3 text-[12px] leading-5 text-[var(--danger)]" role="alert">{error}</p>}
              </section>

              <section className="mt-6 border-t border-[var(--border)] pt-5" aria-labelledby="share-link-label">
                <div className="flex items-baseline justify-between gap-4">
                  <h3 id="share-link-label" className="text-[12px] font-medium text-[var(--text-subtle)]">Share link</h3>
                  <span className="text-[12px] text-[var(--text-subtle)]" aria-live="polite">{copyState === "copied" ? "Copied" : copyState === "failed" ? "Copy failed" : ""}</span>
                </div>
                {pending === "link" ? (
                  <p role="status" className="mt-2 text-[13px] leading-5 text-[var(--text-subtle)]">Loading secret link…</p>
                ) : shareUrl ? (
                  <>
                    <div className="mt-2 flex min-w-0 border border-[var(--border-strong)] bg-[var(--background)] focus-within:border-[var(--accent)] focus-within:ring-1 focus-within:ring-[var(--accent)]">
                      <input type="text" readOnly value={shareUrl} onFocus={(event) => event.currentTarget.select()} aria-label="Share link" className="h-11 min-w-0 flex-1 border-0 bg-transparent px-3 text-[16px] sm:text-[13px] text-[var(--text-muted)] outline-none" />
                      <button type="button" disabled={pending !== null} onClick={() => void copyShareUrl()} className="shrink-0 border-l border-[var(--border)] px-3 text-[13px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--row-hover)] hover:text-[var(--accent)] disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--accent)]">{copyState === "copied" ? "Copied" : "Copy"}</button>
                    </div>
                    {visibility === "Unlisted" && (
                      <div className="mt-3">
                        {confirmRegeneration && <p className="mb-2 text-[12px] leading-5 text-[var(--danger)]">The current link will stop working immediately. Click again to confirm.</p>}
                        <button type="button" disabled={pending !== null} onClick={() => void regenerateLink()} className="inline-flex min-h-11 items-center text-[12px] font-medium text-[var(--accent-strong)] underline underline-offset-4 transition-colors hover:text-[var(--accent)] disabled:cursor-wait disabled:opacity-60 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]">{pending === "regenerate" ? "Regenerating…" : confirmRegeneration ? "Confirm regenerate link" : "Regenerate secret link"}</button>
                        {confirmRegeneration && <button type="button" disabled={pending !== null} onClick={() => setConfirmRegeneration(false)} className="ml-4 inline-flex min-h-11 items-center text-[12px] text-[var(--text-muted)] hover:text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]">Cancel</button>}
                      </div>
                    )}
                  </>
                ) : (
                  <div className="mt-2">
                    <p className="text-[13px] leading-5 text-[var(--text-subtle)]">{visibility === "Private" ? "External sharing is disabled while this note is private." : "The secret link is not available right now."}</p>
                    {visibility === "Unlisted" && error && <button type="button" onClick={() => void loadUnlistedLink()} className="mt-2 inline-flex min-h-11 items-center text-[12px] font-medium text-[var(--accent-strong)] underline underline-offset-4 hover:text-[var(--accent)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]">Retry loading link</button>}
                  </div>
                )}
              </section>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
