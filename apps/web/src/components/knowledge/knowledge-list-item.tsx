import { Tag } from "@/components/ui/tag";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeListItemProps {
  item: KnowledgeListItemData;
}

export function KnowledgeListItem({ item }: KnowledgeListItemProps) {
  return (
    <article className="border-b border-[var(--border)] py-5 first:pt-0 last:border-b-0">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_100px] md:gap-8">
        <div className="min-w-0">
          <a
            href={`#${item.id}`}
            className="text-[16px] font-medium leading-6 text-[var(--text)] underline-offset-4 hover:text-[var(--accent)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[var(--accent)]"
          >
            {item.title}
          </a>
          <p className="mt-1 max-w-3xl text-[14px] leading-6 text-[var(--text-muted)]">
            {item.description}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1" aria-label="Knowledge metadata">
            <span className="text-[12px] font-medium text-[var(--text-muted)]">{item.collection}</span>
            {item.tags.map((tag) => (
              <Tag key={tag}>{tag}</Tag>
            ))}
            <span className="text-[12px] text-[var(--text-subtle)]">{item.visibility}</span>
          </div>
        </div>
        <time className="text-[12px] text-[var(--text-subtle)] md:pt-1 md:text-right" dateTime={item.updatedAtIso}>
          {item.updatedAt}
        </time>
      </div>
    </article>
  );
}
