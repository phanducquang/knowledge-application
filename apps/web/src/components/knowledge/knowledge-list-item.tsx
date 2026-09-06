import Link from "next/link";
import { Tag } from "@/components/ui/tag";
import type { KnowledgeListItemData } from "@/types/knowledge";

interface KnowledgeListItemProps {
  item: KnowledgeListItemData;
}

function visibilityClass(visibility: string) {
  if (visibility === "Public") {
    return "text-[var(--success)]";
  }

  if (visibility === "Unlisted") {
    return "text-[var(--warning)]";
  }

  return "text-[var(--text-subtle)]";
}

export function KnowledgeListItem({ item }: KnowledgeListItemProps) {
  return (
    <article className="group -mx-3 border-b border-[var(--border)] px-3 py-5 transition-colors first:pt-0 last:border-b-0 hover:bg-[var(--row-hover)] focus-within:bg-[var(--row-hover)]">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_100px] md:gap-8">
        <div className="min-w-0">
          <Link
            href={item.href ?? `#${item.id}`}
            className="text-[16px] font-medium leading-6 text-[var(--text)] underline-offset-4 transition-colors group-hover:text-[var(--accent-strong)] group-focus-within:text-[var(--accent-strong)] hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[var(--accent)]"
          >
            {item.title}
          </Link>
          <p className="mt-1 max-w-3xl text-[14px] leading-6 text-[var(--text-muted)]">
            {item.description}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1" aria-label="Knowledge metadata">
            <span className="text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--accent-strong)]">{item.collection}</span>
            {item.tags.map((tag) => (
              <Tag key={tag}>{tag}</Tag>
            ))}
            <span className={`text-[12px] ${visibilityClass(item.visibility)}`}>{item.visibility}</span>
          </div>
        </div>
        <time className="text-[12px] tabular-nums text-[var(--text-subtle)] md:pt-1 md:text-right" dateTime={item.updatedAtIso}>
          {item.updatedAt}
        </time>
      </div>
    </article>
  );
}
