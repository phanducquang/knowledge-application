"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { addSuggestedTags, fetchMetadataSuggestion, MetadataSession, metadataErrorMessage } from "@/lib/metadata-suggestions";

const action = "min-h-9 text-[12px] font-medium text-[var(--accent-strong)] underline-offset-4 hover:underline disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--accent)]";
export function MetadataSuggestions({ id, flushDraft, draftKey, currentTags, applySummary, applyTags }: {
  id?: number; flushDraft: () => Promise<string>; draftKey: string; currentTags: string[];
  applySummary: (value: string) => void; applyTags: (value: string[]) => void;
}) {
  const [session] = useState(() => new MetadataSession(signal => fetchMetadataSuggestion(id!, signal)));
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
  useEffect(() => () => session.dismiss(), [session]);
  const remaining = state.result?.tags.filter(t => !currentTags.some(e => e.toLowerCase() === t.toLowerCase())) ?? [];
  return <section aria-label="AI metadata suggestions" className="mt-3 border-b border-[var(--border)] pb-3 text-[12px] leading-5">
    <button type="button" className={action} disabled={!id || state.busy} onClick={() => void session.submit(flushDraft)}>
      {state.busy ? "Saving & suggesting…" : "Suggest summary & tags"}
    </button>
    <p className="text-[11px] text-[var(--text-subtle)]">{id
      ? "Saves current edits, then sends this note’s text to Gemini. Review before applying."
      : "Create this note first to request AI suggestions."}</p>
    {state.error && <p role="alert" className="mt-2 text-[var(--danger)]">{metadataErrorMessage(state.error)}</p>}
    {state.result && <div className="mt-3" aria-live="polite">
      <div className="flex items-center justify-between gap-3"><span className="text-[10px] uppercase tracking-[0.1em] text-[var(--text-subtle)]">Suggested metadata</span>
        <button type="button" className={action} onClick={session.dismiss}>Dismiss suggestions</button></div>
      {state.sourceDraft !== draftKey && <p className="text-[var(--warning)]">The note changed after the request. Review these earlier suggestions before applying.</p>}
      <p className="whitespace-pre-wrap break-words text-[var(--text-muted)]">{state.result.summary}</p>
      <button type="button" className={action} onClick={() => applySummary(state.result!.summary)}>Apply summary</button>
      <ul className="flex flex-wrap gap-x-4 gap-y-1" aria-label="Suggested tags">{remaining.map(tag => <li key={tag.toLowerCase()} className="flex min-w-0 max-w-full items-center gap-2">
        <span className="min-w-0 break-words">{tag}</span><button type="button" className={`${action} shrink-0`} disabled={currentTags.length >= 20}
          aria-label={`Add tag ${tag}`} onClick={() => applyTags(addSuggestedTags(currentTags, [tag]))}>Add</button></li>)}</ul>
      {remaining.length > 0 && <button type="button" className={action} disabled={currentTags.length >= 20}
        onClick={() => applyTags(addSuggestedTags(currentTags, remaining))}>Apply all tags</button>}
      {currentTags.length >= 20 && remaining.length > 0 && <p className="text-[var(--text-subtle)]">This note already has the maximum 20 tags.</p>}
    </div>}
  </section>;
}
