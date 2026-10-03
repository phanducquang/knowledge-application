"use client";
import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";
import { AskSession, MAX_QUESTION_CHARS, type AskErrorCode } from "@/lib/ask-knowledge";
import { SourceLinkedAnswer } from "./source-linked-answer";

const errors: Record<AskErrorCode, string> = {
  ASK_DISABLED: "Ask My Knowledge is disabled. Enable Gemini generation on the backend to use it.",
  ASK_RETRIEVAL_UNAVAILABLE: "Knowledge retrieval is unavailable. Embeddings may be disabled, quota-limited or temporarily unavailable.",
  ASK_UNAVAILABLE: "The answer could not be generated. Gemini may be quota-limited or temporarily unavailable. Your question is preserved.",
  VALIDATION_ERROR: "Enter a non-blank question of at most 2000 characters.",
  UNAUTHENTICATED: "Your session has expired. Sign in to ask your Knowledge.",
  ACCESS_DENIED: "This request was not authorized. Sign in again before retrying.",
};
export function AskKnowledge() {
  const [session] = useState(() => new AskSession());
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
  useEffect(() => () => session.cancel(), [session]);
  const loading = state.status === "loading";
  return <div className="max-w-[760px]">
    <form onSubmit={event => { event.preventDefault(); void session.submit(); }}>
      <label htmlFor="ask-question" className="mb-2 block text-[11px] font-medium uppercase tracking-[0.1em] text-[var(--text-subtle)]">Question</label>
      <textarea id="ask-question" rows={5} maxLength={MAX_QUESTION_CHARS} value={state.draft}
        onChange={event => session.draft(event.target.value)}
        onKeyDown={event => { if (event.key === "Enter" && (event.metaKey || event.ctrlKey) && !event.nativeEvent.isComposing) { event.preventDefault(); void session.submit(); } }}
        aria-describedby="ask-question-help" placeholder="How did I configure WebClient timeouts?"
        className="block min-h-[144px] w-full resize-y rounded-[4px] border border-[var(--border-strong)] bg-[var(--surface)] px-4 py-3 text-[16px] leading-7 text-[var(--text)] placeholder:text-[var(--text-subtle)] focus:border-[var(--accent)] focus:outline focus:outline-1 focus:outline-[var(--accent)]" />
      <div id="ask-question-help" className="mt-2 flex flex-wrap justify-between gap-2 text-[12px] text-[var(--text-subtle)]">
        <span>Enter for a new line · Cmd/Ctrl + Enter to ask</span><span className="tabular-nums">{state.draft.length} / {MAX_QUESTION_CHARS}</span>
      </div>
      <p className="mt-4 text-[12px] leading-5 text-[var(--text-muted)]">Your question and selected private note text are sent to Gemini. Free Tier may use submitted content to improve Google products; review provider terms before enabling. Usage limits apply. Questions and answers are not saved by this application.</p>
      <div className="mt-5 flex items-center gap-4">
        <button type="submit" disabled={loading || !state.draft.trim()}
          className="min-h-10 rounded-[4px] bg-[var(--accent)] px-5 py-2 text-[14px] font-medium text-white transition-colors hover:bg-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] disabled:cursor-not-allowed disabled:opacity-50">
          {loading ? "Asking…" : "Ask"}
        </button>
        {loading && <button type="button" onClick={() => session.clear()} className="text-[13px] text-[var(--text-muted)] underline underline-offset-4">Cancel view</button>}
      </div>
    </form>
    <div className="mt-8" role="status" aria-live="polite" aria-atomic="true">
      {loading && <p className="text-[14px] leading-6 text-[var(--text-muted)]">Retrieving current notes and preparing an answer…</p>}
      {state.result?.status === "ANSWERED" && <p className="sr-only">Answer ready. Sources are listed below.</p>}
      {state.status === "idle" && <p className="text-[14px] leading-6 text-[var(--text-subtle)]">Ask a focused question about something you have recorded. Answers use current indexed notes, not the web or revision history.</p>}
      {state.status === "error" && <div className="border-t border-[var(--border)] pt-5">
        <p className="text-[14px] leading-6 text-[var(--text-muted)]">{errors[state.error ?? "ASK_UNAVAILABLE"]}</p>
        {state.error === "UNAUTHENTICATED" || state.error === "ACCESS_DENIED"
          ? <Link href="/login" className="mt-3 inline-block text-[13px] text-[var(--accent-strong)] underline underline-offset-4">Sign in</Link>
          : <button type="button" onClick={() => void session.submit()} className="mt-3 text-[13px] text-[var(--accent-strong)] underline underline-offset-4">Try again</button>}
      </div>}
      {state.result?.status === "NO_CONTEXT" && <div className="border-t border-[var(--border)] pt-5">
        <h2 className="text-[18px] font-medium text-[var(--text)]">No current indexed context</h2>
        <p className="mt-2 text-[14px] leading-6 text-[var(--text-muted)]">No compatible indexed notes are available yet. New or recently edited notes may still be indexing. No answer generation was requested.</p>
      </div>}
    </div>
    {state.result?.status === "ANSWERED" && state.result.answer && <SourceLinkedAnswer answer={state.result.answer} citations={state.result.citations} />}
  </div>;
}
