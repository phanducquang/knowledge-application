"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "@/components/layout/sidebar";
import { QuickSearchOverlay } from "@/components/search/quick-search-overlay";
import type { KnowledgeListItemData } from "@/types/knowledge";
import type { CurrentUser } from "@/lib/backend-auth";

interface WorkspaceShellClientProps {
  children: React.ReactNode;
  items: KnowledgeListItemData[];
  currentUser: CurrentUser;
}

function SearchIcon() {
  return (
    <svg
      viewBox="0 0 18 18"
      aria-hidden="true"
      className="h-4 w-4 fill-none stroke-current"
      strokeWidth="1.5"
    >
      <circle cx="7.5" cy="7.5" r="4.75" />
      <path d="m11 11 4 4" strokeLinecap="round" />
    </svg>
  );
}

export function WorkspaceShellClient({ children, items, currentUser }: WorkspaceShellClientProps) {
  const pathname = usePathname();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [quickSearchOpen, setQuickSearchOpen] = useState(false);
  const collections = useMemo(
    () =>
      Array.from(
        new Set(items.map((item) => item.collection).filter((value): value is string => Boolean(value))),
      ).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" })),
    [items],
  );

  const openQuickSearch = useCallback(() => {
    setMobileNavOpen(false);
    setQuickSearchOpen(true);
  }, []);

  useEffect(() => {
    document.body.style.overflow = mobileNavOpen || quickSearchOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileNavOpen, quickSearchOpen]);

  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      if (event.defaultPrevented) {
        return;
      }

      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        openQuickSearch();
      }
    };

    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, [openQuickSearch]);

  return (
    <div className="min-h-screen bg-[var(--background)] text-[var(--text)]">
      <div className="hidden lg:fixed lg:inset-y-0 lg:left-0 lg:block lg:w-[236px] lg:border-r lg:border-[var(--border)]">
        <Sidebar
          collections={collections}
          currentPath={pathname}
          onQuickSearch={openQuickSearch}
          currentUser={currentUser}
        />
      </div>

      <header className="sticky top-0 z-20 grid h-14 grid-cols-[1fr_auto_1fr] items-center border-b border-[var(--border)] bg-[var(--background)] px-5 lg:hidden">
        <button
          type="button"
          onClick={() => setMobileNavOpen(true)}
          className="justify-self-start border border-[var(--border-strong)] bg-[var(--surface)] px-3 py-1.5 text-[13px] font-medium text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
          aria-expanded={mobileNavOpen}
          aria-controls="mobile-navigation"
        >
          Menu
        </button>

        <span className="text-[14px] font-semibold">Knowledge</span>

        <div className="flex items-center justify-self-end gap-1">
          <button
            type="button"
            onClick={openQuickSearch}
            aria-label="Open quick search"
            className="inline-flex h-9 w-9 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--nav-hover)] hover:text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
          >
            <SearchIcon />
          </button>
          <Link href="/knowledge/new" className="text-[13px] font-medium text-[var(--accent)]">
            New note
          </Link>
        </div>
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
            <Sidebar
              collections={collections}
              currentPath={pathname}
              onNavigate={() => setMobileNavOpen(false)}
              onClose={() => setMobileNavOpen(false)}
              currentUser={currentUser}
            />
          </div>
        </div>
      )}

      <QuickSearchOverlay
        key={quickSearchOpen ? "quick-search-open" : "quick-search-closed"}
        open={quickSearchOpen}
        items={items}
        onClose={() => setQuickSearchOpen(false)}
      />

      <main className="lg:pl-[236px]">{children}</main>
    </div>
  );
}
