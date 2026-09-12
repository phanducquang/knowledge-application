export default function PublicKnowledgeNotFound() {
  return (
    <main className="min-h-screen bg-[var(--background)] text-[var(--text)]">
      <div className="mx-auto w-full max-w-[760px] px-5 py-16 sm:px-8">
        <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
          Knowledge / Missing
        </p>
        <h1 className="mt-3 text-[32px] font-semibold tracking-[-0.035em]">
          Note not found
        </h1>
        <p className="mt-4 max-w-lg text-[15px] leading-7 text-[var(--text-muted)]">
          This public note does not exist or is no longer available.
        </p>
      </div>
    </main>
  );
}
