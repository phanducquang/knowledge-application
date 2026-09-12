"use client";

import { useEffect } from "react";

export default function ApplicationError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[760px] items-center px-5 py-16 sm:px-8">
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--danger)]">
          Knowledge / Unavailable
        </p>
        <h1 className="mt-3 text-[30px] font-semibold tracking-[-0.035em] text-[var(--text)]">
          The workspace could not be loaded.
        </h1>
        <p className="mt-4 max-w-lg text-[15px] leading-7 text-[var(--text-muted)]">
          Check that the Knowledge API and PostgreSQL are running, then try again.
        </p>
        <button
          type="button"
          onClick={reset}
          className="mt-6 border border-[var(--accent-muted)] px-3 py-2 text-[13px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--row-hover)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          Try again
        </button>
      </div>
    </main>
  );
}
