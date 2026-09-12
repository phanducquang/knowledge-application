import Link from "next/link";
import type { CurrentUser } from "@/lib/backend-auth";

interface SidebarProps {
  collections: string[];
  currentPath?: string;
  onNavigate?: () => void;
  onClose?: () => void;
  onQuickSearch?: () => void;
  currentUser: CurrentUser;
}

const states = ["Private", "Shared", "Drafts"];

function navItemClass(active: boolean) {
  return `block w-full border-l-2 py-1.5 pl-3 pr-2 text-left text-[14px] leading-5 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${
    active
      ? "border-[var(--accent)] bg-[var(--active)] font-medium text-[var(--accent-strong)]"
      : "border-transparent text-[var(--text-muted)] hover:bg-[var(--nav-hover)] hover:text-[var(--text)]"
  }`;
}

function NavLink({
  children,
  href = "#",
  active = false,
  onNavigate,
}: {
  children: React.ReactNode;
  href?: string;
  active?: boolean;
  onNavigate?: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={navItemClass(active)}
    >
      {children}
    </Link>
  );
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

export function Sidebar({ collections, currentUser, currentPath = "/", onNavigate, onClose, onQuickSearch }: SidebarProps) {
  const searchActive = currentPath.startsWith("/search");

  return (
    <aside className="flex h-full flex-col bg-[var(--sidebar)] px-4 py-5">
      <div className="mb-6 flex items-center justify-between gap-4 px-2">
        <Link href="/" onClick={onNavigate} className="min-w-0 text-[15px] font-semibold tracking-[-0.015em] text-[var(--text)]">
          Knowledge
        </Link>
        <div className="flex shrink-0 items-center gap-1">
          {onQuickSearch && (
            <button
              type="button"
              onClick={onQuickSearch}
              aria-label="Open quick search"
              title="Quick search (Cmd/Ctrl K)"
              className="inline-flex h-8 w-8 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--nav-hover)] hover:text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
            >
              <SearchIcon />
            </button>
          )}
          <span className="px-1 text-[11px] font-medium text-[var(--accent)]">Private</span>
          {onClose && (
            <button
              type="button"
              onClick={onClose}
              aria-label="Close navigation"
              className="inline-flex h-8 w-8 items-center justify-center text-[var(--text-muted)] transition-colors hover:bg-[var(--nav-hover)] hover:text-[var(--text)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
            >
              <svg viewBox="0 0 16 16" aria-hidden="true" className="h-4 w-4 fill-none stroke-current" strokeWidth="1.5" strokeLinecap="round">
                <path d="M4 4l8 8M12 4l-8 8" />
              </svg>
            </button>
          )}
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto" aria-label="Knowledge navigation">
        <div className="space-y-1">
          <NavLink href="/" active={currentPath === "/"} onNavigate={onNavigate}>All notes</NavLink>
          <NavLink href="/search" active={searchActive} onNavigate={onNavigate}>Search</NavLink>
        </div>

        <div className="mt-7">
          <p className="mb-2 px-3 text-[10px] font-medium uppercase tracking-[0.12em] text-[var(--text-subtle)]">Collections</p>
          <div className="space-y-0.5">
            {collections.map((collection) => (
              <NavLink key={collection} onNavigate={onNavigate}>{collection}</NavLink>
            ))}
            {collections.length === 0 && (
              <p className="px-3 py-1.5 text-[12px] text-[var(--text-subtle)]">No collections yet</p>
            )}
          </div>
        </div>

        <div className="mt-7">
          <Link href="#" onClick={onNavigate} className="block px-3 py-1.5 text-[13px] font-medium text-[var(--text-muted)] transition-colors hover:bg-[var(--nav-hover)] hover:text-[var(--text)]">
            Tags
          </Link>
        </div>

        <div className="mt-7 border-t border-[var(--border)] pt-4">
          <div className="space-y-0.5">
            {states.map((state) => (
              <NavLink key={state} onNavigate={onNavigate}>{state}</NavLink>
            ))}
          </div>
        </div>
      </nav>

      <div className="border-t border-[var(--border)] pt-4">
        <Link
          href="/knowledge/new"
          onClick={onNavigate}
          className="block w-full border border-[var(--accent-muted)] bg-transparent px-3 py-2 text-left text-[13px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--nav-hover)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          + New note
        </Link>
        <div className="mt-4 border-t border-[var(--border)] pt-4">
          <p className="truncate px-1 text-[11px] text-[var(--text-subtle)]" title={currentUser.email}>
            {currentUser.email}
          </p>
          <form action="/api/auth/logout" method="post" className="mt-2">
            <button
              type="submit"
              className="px-1 text-[12px] font-medium text-[var(--text-muted)] underline-offset-4 transition-colors hover:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
            >
              Sign out
            </button>
          </form>
        </div>
      </div>
    </aside>
  );
}
