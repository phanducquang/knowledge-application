import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ExternalKnowledgeArticle } from "@/components/knowledge/external-knowledge-article";
import {
  getPublicKnowledgeBySlug,
  PublicKnowledgeApiError,
} from "@/lib/api/public-knowledge";
import {
  isPublicKnowledgeNotFound,
  publicKnowledgeMetadata,
} from "@/lib/public-knowledge";

export const dynamic = "force-dynamic";

interface PublicKnowledgePageProps {
  params: Promise<{ slug: string }>;
}

async function publicKnowledgeOrNotFound(slug: string) {
  try {
    return await getPublicKnowledgeBySlug(slug);
  } catch (error) {
    if (
      error instanceof PublicKnowledgeApiError &&
      isPublicKnowledgeNotFound(error.status)
    ) {
      notFound();
    }
    throw error;
  }
}

export async function generateMetadata({ params }: PublicKnowledgePageProps): Promise<Metadata> {
  const { slug } = await params;
  try {
    return publicKnowledgeMetadata(await getPublicKnowledgeBySlug(slug));
  } catch (error) {
    if (
      error instanceof PublicKnowledgeApiError &&
      isPublicKnowledgeNotFound(error.status)
    ) {
      return { robots: { index: false, follow: false } };
    }
    throw error;
  }
}

export default async function PublicKnowledgePage({ params }: PublicKnowledgePageProps) {
  const { slug } = await params;
  const article = await publicKnowledgeOrNotFound(slug);
  return <ExternalKnowledgeArticle article={article} accessLabel="Public note" />;
}
