import { KnowledgeListItem } from "@/components/knowledge/knowledge-list-item";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeListProps {
  items: KnowledgeListItemData[];
  heading?: string;
  emptyTitle?: string;
  emptyDescription?: string;
}

export function KnowledgeList({
  items,
  heading = "Recently updated",
  emptyTitle = "Your library is empty",
  emptyDescription = "Create the first note when you have a technical decision, pattern, or reference worth keeping.",
}: KnowledgeListProps) {
  return (
    <section aria-labelledby="knowledge-list-heading">
      <div className="flex items-baseline justify-between gap-4 border-b border-[var(--border)] pb-3">
        <h2 id="knowledge-list-heading" className="text-[13px] font-medium text-[var(--text-muted)]">
          {heading}
        </h2>
        <span className="text-[12px] text-[var(--text-subtle)]">{items.length} notes</span>
      </div>
      {items.length > 0 ? (
        <div>
          {items.map((item) => (
            <KnowledgeListItem key={item.id} item={item} />
          ))}
        </div>
      ) : (
        <div className="py-14 sm:py-16">
          <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-[var(--text-subtle)]">
            {emptyTitle}
          </p>
          <p className="mt-3 max-w-lg text-[15px] leading-7 text-[var(--text-muted)]">
            {emptyDescription}
          </p>
        </div>
      )}
    </section>
  );
}
