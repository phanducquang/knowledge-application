import Link from "next/link";
import { WorkspaceShell } from "@/components/layout/workspace-shell";

export default function KnowledgeNotFound() {
  return (
    <WorkspaceShell>
      <div className="mx-auto w-full max-w-[760px] px-5 py-16 sm:px-8 lg:px-12">
        <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Knowledge / Missing</p>
        <h1 className="mt-3 text-[32px] font-semibold tracking-[-0.035em] text-[var(--text)]">Note not found</h1>
        <p className="mt-4 max-w-lg text-[15px] leading-7 text-[var(--text-muted)]">
          This mock reference route currently contains only the approved reading-screen sample.
        </p>
        <Link href="/" className="mt-6 inline-block text-[13px] font-medium text-[var(--accent-strong)] underline underline-offset-4">
          Return to all notes
        </Link>
      </div>
    </WorkspaceShell>
  );
}
