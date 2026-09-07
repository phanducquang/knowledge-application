import { KnowledgeListItem } from "@/components/knowledge/knowledge-list-item";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeListProps {
  items: KnowledgeListItemData[];
}

export function KnowledgeList({ items }: KnowledgeListProps) {
  return (
    <section aria-labelledby="knowledge-list-heading">
      <div className="flex items-baseline justify-between gap-4 border-b border-[var(--border)] pb-3">
        <h2 id="knowledge-list-heading" className="text-[13px] font-medium text-[var(--text-muted)]">
          Recently updated
        </h2>
        <span className="text-[12px] text-[var(--text-subtle)]">{items.length} notes</span>
      </div>
      <div>
        {items.map((item) => (
          <KnowledgeListItem key={item.id} item={item} />
        ))}
      </div>
    </section>
  );
}
