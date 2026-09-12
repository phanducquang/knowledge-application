export interface ApiPublicKnowledgeResponse {
  title: string;
  slug: string;
  summary: string | null;
  content: string;
  collection: string | null;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
}

export interface PublicKnowledgeData {
  title: string;
  slug: string;
  summary: string;
  content: string;
  collection: string | null;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
}

export function mapApiPublicKnowledge(
  response: ApiPublicKnowledgeResponse,
): PublicKnowledgeData {
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

export function isPublicKnowledgeNotFound(status: number) {
  return status === 404;
}

export function publicKnowledgeMetadata(article: PublicKnowledgeData) {
  return {
    title: article.title,
    description: article.summary || article.title,
    robots: {
      index: true,
      follow: true,
    },
  };
}
