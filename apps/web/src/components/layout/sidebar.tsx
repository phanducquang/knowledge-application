import Link from "next/link";

interface SidebarProps {
  currentPath?: string;
  onNavigate?: () => void;
  onClose?: () => void;
}

const collections = ["Backend", "Database", "DevOps"];
const states = ["Private", "Shared", "Drafts"];

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
      className={`block border-l-2 py-1.5 pl-3 pr-2 text-[14px] leading-5 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${
        active
          ? "border-[var(--accent)] bg-[var(--active)] font-medium text-[var(--accent-strong)]"
          : "border-transparent text-[var(--text-muted)] hover:bg-[var(--nav-hover)] hover:text-[var(--text)]"
      }`}
    >
      {children}
    </Link>
  );
}

export function Sidebar({ currentPath = "/", onNavigate, onClose }: SidebarProps) {
  return (
    <aside className="flex h-full flex-col bg-[var(--sidebar)] px-4 py-5">
      <div className="mb-6 flex items-center justify-between gap-4 px-2">
        <Link href="/" onClick={onNavigate} className="min-w-0 text-[15px] font-semibold tracking-[-0.015em] text-[var(--text)]">
          Knowledge
        </Link>
        <div className="flex shrink-0 items-center gap-2">
          <span className="text-[11px] font-medium text-[var(--accent)]">Private</span>
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
          <NavLink href="/search" active={currentPath.startsWith("/search")} onNavigate={onNavigate}>Search</NavLink>
        </div>

        <div className="mt-7">
          <p className="mb-2 px-3 text-[10px] font-medium uppercase tracking-[0.12em] text-[var(--text-subtle)]">Collections</p>
          <div className="space-y-0.5">
            {collections.map((collection) => (
              <NavLink key={collection} onNavigate={onNavigate}>{collection}</NavLink>
            ))}
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
        <button
          type="button"
          className="w-full border border-[var(--accent-muted)] bg-transparent px-3 py-2 text-left text-[13px] font-medium text-[var(--accent-strong)] transition-colors hover:bg-[var(--nav-hover)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          + New note
        </button>
      </div>
    </aside>
  );
}
