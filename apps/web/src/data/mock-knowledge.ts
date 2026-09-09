import type { KnowledgeArticleData, KnowledgeListItemData } from "@/types/knowledge";

export const mockKnowledgeItems: KnowledgeListItemData[] = [
  {
    id: "spring-webclient-timeout",
    title: "Spring WebClient timeout handling",
    description:
      "Handling downstream timeouts and failure recovery without obscuring the original reactive flow.",
    collection: "Backend",
    tags: ["Spring Boot", "WebClient"],
    visibility: "Private",
    updatedAt: "Sep 6",
    updatedAtIso: "2026-09-06",
    href: "/knowledge/spring-webclient-timeout",
  },
  {
    id: "redis-conditional-auto-configuration",
    title: "Redis conditional auto configuration",
    description:
      "Keeping a shared Spring library from activating Redis until the consuming service explicitly enables it.",
    collection: "Backend",
    tags: ["Redis", "Spring Boot"],
    visibility: "Private",
    updatedAt: "Sep 5",
    updatedAtIso: "2026-09-05",
    href: "/knowledge/redis-conditional-auto-configuration",
  },
  {
    id: "elasticsearch-sorting-strategies",
    title: "Elasticsearch sorting strategies",
    description:
      "Notes on scripted sorting, computed values, and choosing the least expensive option for result ordering.",
    collection: "Database",
    tags: ["Elasticsearch", "Search"],
    visibility: "Private",
    updatedAt: "Sep 4",
    updatedAtIso: "2026-09-04",
    href: "/knowledge/elasticsearch-sorting-strategies",
  },
  {
    id: "nginx-cdn-proxy",
    title: "Nginx CDN proxy configuration",
    description:
      "Forwarding external assets safely while preserving query parameters, TLS host information, and SSRF boundaries.",
    collection: "DevOps",
    tags: ["Nginx", "CDN"],
    visibility: "Unlisted",
    updatedAt: "Aug 20",
    updatedAtIso: "2026-08-20",
    href: "/knowledge/nginx-cdn-proxy",
  },
  {
    id: "jpa-entity-scanning",
    title: "Spring Data JPA entity scanning",
    description:
      "Separating repository scanning from entity scanning when entities live inside a reusable Spring Boot library.",
    collection: "Backend",
    tags: ["JPA", "Spring Boot"],
    visibility: "Public",
    updatedAt: "Aug 17",
    updatedAtIso: "2026-08-17",
    href: "/knowledge/jpa-entity-scanning",
  },
];

export const mockReferenceArticle: KnowledgeArticleData = {
  ...mockKnowledgeItems[0],
  lead:
    "A practical note on where to enforce timeouts in a Reactor chain, how errors propagate, and how to preserve enough context for observability without duplicating recovery logic.",
  readTime: "6 min read",
};

export function getMockKnowledgeArticle(slug: string): KnowledgeArticleData | undefined {
  const item = mockKnowledgeItems.find((knowledgeItem) => knowledgeItem.id === slug);
  if (!item) {
    return undefined;
  }

  if (item.id === mockReferenceArticle.id) {
    return mockReferenceArticle;
  }

  return {
    ...item,
    lead: item.description,
    readTime: "3 min read",
  };
}
