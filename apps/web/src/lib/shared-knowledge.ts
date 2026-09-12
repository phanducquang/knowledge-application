export interface ApiSharedKnowledgeResponse {
  title: string;
  slug: string;
  summary: string | null;
  content: string;
  collection: string | null;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
}

export interface SharedKnowledgeData {
  title: string;
  slug: string;
  summary: string;
  content: string;
  collection: string | null;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
}

export function mapApiSharedKnowledge(
  response: ApiSharedKnowledgeResponse,
): SharedKnowledgeData {
  return {
    title: response.title,
    slug: response.slug,
    summary: response.summary ?? "",
    content: response.content ?? "",
    collection: response.collection ?? null,
    tags: response.tags ?? [],
    publishedAt: response.publishedAt ?? null,
    updatedAt: response.updatedAt,
  };
}

export function isSharedKnowledgeNotFound(status: number) {
  return status === 404;
}

export function sharedKnowledgeMetadata(article: SharedKnowledgeData) {
  return {
    title: article.title,
    description: article.summary || article.title,
    robots: {
      index: false,
      follow: false,
      noarchive: true,
    },
  };
}
