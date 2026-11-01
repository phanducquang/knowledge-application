interface TagProps {
  children: React.ReactNode;
}

export function Tag({ children }: TagProps) {
  return <span className="min-w-0 max-w-full text-[12px] text-[var(--accent-muted)] [overflow-wrap:anywhere]">#{children}</span>;
}
