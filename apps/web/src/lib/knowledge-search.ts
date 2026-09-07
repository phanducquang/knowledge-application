import type { KnowledgeListItemData } from "@/types/knowledge";

function scoreKnowledgeItem(item: KnowledgeListItemData, query: string) {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) {
    return 0;
  }

  const title = item.title.toLowerCase();
  const description = item.description.toLowerCase();
  const collection = item.collection.toLowerCase();
  const tags = item.tags.map((tag) => tag.toLowerCase());

  let score = 0;

  if (title === normalizedQuery) score += 12;
  else if (title.startsWith(normalizedQuery)) score += 8;
  else if (title.includes(normalizedQuery)) score += 6;

  if (collection === normalizedQuery) score += 6;
  else if (collection.includes(normalizedQuery)) score += 3;

  for (const tag of tags) {
    if (tag === normalizedQuery) score += 6;
    else if (tag.includes(normalizedQuery)) score += 3;
  }

  if (description.includes(normalizedQuery)) score += 2;

  return score;
}

export function searchKnowledgeItems(items: KnowledgeListItemData[], query: string) {
  const normalizedQuery = query.trim();
  if (!normalizedQuery) {
    return [];
  }

  return items
    .map((item) => ({ item, score: scoreKnowledgeItem(item, normalizedQuery) }))
    .filter(({ score }) => score > 0)
    .sort((a, b) => b.score - a.score || b.item.updatedAtIso.localeCompare(a.item.updatedAtIso))
    .map(({ item }) => item);
}
