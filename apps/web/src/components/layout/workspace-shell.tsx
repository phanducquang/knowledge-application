"use client";

import { useEffect, useState } from "react";
import { Sidebar } from "@/components/layout/sidebar";

interface WorkspaceShellProps {
  children: React.ReactNode;
}

export function WorkspaceShell({ children }: WorkspaceShellProps) {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    document.body.style.overflow = mobileNavOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileNavOpen]);

  return (
    <div className="min-h-screen bg-[var(--background)] text-[var(--text)]">
      <div className="hidden lg:fixed lg:inset-y-0 lg:left-0 lg:block lg:w-[236px] lg:border-r lg:border-[var(--border)]">
        <Sidebar />
      </div>

      <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-[var(--border)] bg-[var(--background)] px-5 lg:hidden">
        <button
          type="button"
          onClick={() => setMobileNavOpen(true)}
          className="border border-[var(--border-strong)] bg-[var(--surface)] px-3 py-1.5 text-[13px] font-medium text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
          aria-expanded={mobileNavOpen}
          aria-controls="mobile-navigation"
        >
          Menu
        </button>
        <span className="text-[14px] font-semibold">Knowledge</span>
        <button type="button" className="text-[13px] font-medium text-[var(--accent)]">New note</button>
      </header>

      {mobileNavOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Knowledge navigation">
          <button
            type="button"
            aria-label="Close navigation"
            className="absolute inset-0 bg-black/20"
            onClick={() => setMobileNavOpen(false)}
          />
          <div id="mobile-navigation" className="relative h-full w-[min(82vw,300px)] border-r border-[var(--border)]">
            <div className="absolute right-3 top-3 z-10">
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                className="px-2 py-1 text-[12px] text-[var(--text-muted)] hover:text-[var(--text)]"
              >
                Close
              </button>
            </div>
            <Sidebar onNavigate={() => setMobileNavOpen(false)} />
          </div>
        </div>
      )}

      <main className="lg:pl-[236px]">{children}</main>
    </div>
  );
}
