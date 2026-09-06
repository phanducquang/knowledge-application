interface SidebarProps {
  onNavigate?: () => void;
}

const collections = ["Backend", "Database", "DevOps"];
const states = ["Private", "Shared", "Drafts"];

function NavLink({ children, active = false, onNavigate }: { children: React.ReactNode; active?: boolean; onNavigate?: () => void }) {
  return (
    <a
      href="#"
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={`block border-l-2 py-1.5 pl-3 pr-2 text-[14px] leading-5 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${
        active
          ? "border-[var(--accent)] bg-[var(--active)] font-medium text-[var(--text)]"
          : "border-transparent text-[var(--text-muted)] hover:border-[var(--border-strong)] hover:text-[var(--text)]"
      }`}
    >
      {children}
    </a>
  );
}

export function Sidebar({ onNavigate }: SidebarProps) {
  return (
    <aside className="flex h-full flex-col bg-[var(--sidebar)] px-4 py-5">
      <div className="mb-6 flex items-center justify-between gap-4 px-2">
        <a href="#" onClick={onNavigate} className="text-[15px] font-semibold tracking-[-0.01em] text-[var(--text)]">
          Knowledge
        </a>
        <span className="text-[11px] text-[var(--text-subtle)]">Personal</span>
      </div>

      <nav className="flex-1 overflow-y-auto" aria-label="Knowledge navigation">
        <div className="space-y-1">
          <NavLink active onNavigate={onNavigate}>All notes</NavLink>
        </div>

        <div className="mt-7">
          <p className="mb-2 px-3 text-[11px] font-medium uppercase tracking-[0.08em] text-[var(--text-subtle)]">Collections</p>
          <div className="space-y-0.5">
            {collections.map((collection) => (
              <NavLink key={collection} onNavigate={onNavigate}>{collection}</NavLink>
            ))}
          </div>
        </div>

        <div className="mt-7">
          <a href="#" onClick={onNavigate} className="block px-3 py-1.5 text-[13px] font-medium text-[var(--text-muted)] hover:text-[var(--text)]">
            Tags
          </a>
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
          className="w-full border border-[var(--border-strong)] bg-[var(--surface)] px-3 py-2 text-left text-[13px] font-medium text-[var(--text)] transition-colors hover:border-[var(--text-subtle)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          + New note
        </button>
      </div>
    </aside>
  );
}
