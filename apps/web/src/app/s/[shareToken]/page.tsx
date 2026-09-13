import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ExternalKnowledgeArticle } from "@/components/knowledge/external-knowledge-article";
import {
  getSharedKnowledgeByToken,
  SharedKnowledgeApiError,
} from "@/lib/api/shared-knowledge";
import {
  isSharedKnowledgeNotFound,
  sharedKnowledgeMetadata,
} from "@/lib/shared-knowledge";

export const dynamic = "force-dynamic";

interface SharedKnowledgePageProps {
  params: Promise<{ shareToken: string }>;
}

async function sharedKnowledgeOrNotFound(shareToken: string) {
  try {
    return await getSharedKnowledgeByToken(shareToken);
  } catch (error) {
    if (
      error instanceof SharedKnowledgeApiError &&
      isSharedKnowledgeNotFound(error.status)
    ) {
      notFound();
    }
    throw error;
  }
}

export async function generateMetadata({ params }: SharedKnowledgePageProps): Promise<Metadata> {
  const { shareToken } = await params;
  try {
    return sharedKnowledgeMetadata(await getSharedKnowledgeByToken(shareToken));
  } catch (error) {
    if (
      error instanceof SharedKnowledgeApiError &&
      isSharedKnowledgeNotFound(error.status)
    ) {
      return { robots: { index: false, follow: false, noarchive: true } };
    }
    throw error;
  }
}

export default async function SharedKnowledgePage({ params }: SharedKnowledgePageProps) {
  const { shareToken } = await params;
  const article = await sharedKnowledgeOrNotFound(shareToken);
  return (
    <ExternalKnowledgeArticle
      article={article}
      accessLabel="Shared note"
      imageContext={{ kind: "shared", shareToken }}
    />
  );
}
