import type { KnowledgeListItemData } from "@/types/knowledge";

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
  },
];
