import { AskKnowledge } from "@/components/ask/ask-knowledge";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { requireCurrentUser } from "@/lib/auth";
import { listKnowledge } from "@/lib/api/knowledge";
import { toKnowledgeListItem } from "@/lib/knowledge-mapping";

export const dynamic = "force-dynamic";
export const metadata = { title: "Ask My Knowledge", robots: { index: false, follow: false } };
export default async function AskPage() {
  await requireCurrentUser();
  const items = (await listKnowledge()).map(toKnowledgeListItem);
  return <WorkspaceShell items={items}>
    <div className="mx-auto w-full max-w-[1080px] px-5 py-8 sm:px-8 sm:py-10 lg:px-12 lg:py-12">
      <header className="mb-8 border-b border-[var(--border)] pb-6">
        <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Knowledge / Ask</p>
        <h1 className="text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[34px]">Ask My Knowledge</h1>
        <p className="mt-3 max-w-2xl text-[14px] leading-6 text-[var(--text-muted)]">Get a single answer grounded in your current notes, with sources you can open and check.</p>
      </header>
      <AskKnowledge />
    </div>
  </WorkspaceShell>;
}
