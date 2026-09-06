interface TagProps {
  children: React.ReactNode;
}

export function Tag({ children }: TagProps) {
  return <span className="text-[12px] text-[var(--text-muted)]">#{children}</span>;
}
