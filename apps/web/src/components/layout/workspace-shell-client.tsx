"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "@/components/layout/sidebar";
import { QuickSearchOverlay } from "@/components/search/quick-search-overlay";
import type { KnowledgeListItemData } from "@/types/knowledge";
import type { CurrentUser } from "@/lib/backend-auth";
import type { CollectionData } from "@/types/collection";

interface WorkspaceShellClientProps {
  children: React.ReactNode;
  items: KnowledgeListItemData[];
  collections: CollectionData[];
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

export function WorkspaceShellClient({ children, items, collections, currentUser }: WorkspaceShellClientProps) {
  const pathname = usePathname();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [quickSearchOpen, setQuickSearchOpen] = useState(false);
  const navigationRef = useRef<HTMLDivElement>(null);

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
    if (!mobileNavOpen) return;
    const previousFocus = document.activeElement as HTMLElement | null;
    const navigation = navigationRef.current;
    const focusable = () => Array.from(navigation?.querySelectorAll<HTMLElement>(
      'a[href], button:not(:disabled), input:not(:disabled), [tabindex="0"]',
    ) ?? []);
    navigation?.querySelector<HTMLElement>('[aria-label="Close navigation"]')?.focus();

    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setMobileNavOpen(false);
      }
      if (event.key !== "Tab") return;
      const controls = focusable();
      const first = controls[0];
      const last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    };
    const desktop = window.matchMedia("(min-width: 1024px)");
    const handleResize = () => { if (desktop.matches) setMobileNavOpen(false); };
    document.addEventListener("keydown", handleKey);
    desktop.addEventListener("change", handleResize);
    return () => {
      document.removeEventListener("keydown", handleKey);
      desktop.removeEventListener("change", handleResize);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [mobileNavOpen]);

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
      <a href="#main-content" className="skip-link" inert={mobileNavOpen || quickSearchOpen}>Skip to content</a>
      <div inert={mobileNavOpen || quickSearchOpen} className="hidden lg:fixed lg:inset-y-0 lg:left-0 lg:block lg:w-[236px] lg:border-r lg:border-[var(--border)]">
        <Sidebar
          collections={collections}
          currentPath={pathname}
          onQuickSearch={openQuickSearch}
          currentUser={currentUser}
        />
      </div>

      <header inert={mobileNavOpen || quickSearchOpen} className="sticky top-0 z-20 flex min-h-14 items-center justify-between gap-2 border-b border-[var(--border)] bg-[var(--background)] px-4 lg:hidden">
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
          <Link href="/knowledge/new" className="inline-flex min-h-11 items-center whitespace-nowrap px-1 text-[13px] font-medium text-[var(--accent)]">
            New note
          </Link>
        </div>
      </header>

      {mobileNavOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Knowledge navigation">
          <button
            type="button"
            aria-label="Close navigation"
            tabIndex={-1}
            className="absolute inset-0 bg-[var(--overlay-backdrop)]"
            onClick={() => setMobileNavOpen(false)}
          />
          <div ref={navigationRef} id="mobile-navigation" className="relative h-dvh w-[min(88%,320px)] border-r border-[var(--border)]">
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

      <main id="main-content" tabIndex={-1} inert={mobileNavOpen || quickSearchOpen} className="min-w-0 lg:pl-[236px]">{children}</main>
    </div>
  );
}
