"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getUnlistedLinkAction,
  regenerateUnlistedLinkAction,
  updateKnowledgeVisibilityAction,
} from "@/app/knowledge/actions";
import { KnowledgeShareAction } from "@/components/knowledge/knowledge-share-action";
import type { KnowledgeVisibility } from "@/types/knowledge";

interface ReadingKnowledgeShareActionProps {
  id: number;
  slug: string;
  title: string;
  initialVisibility: KnowledgeVisibility;
}

export function ReadingKnowledgeShareAction({
  id,
  slug,
  title,
  initialVisibility,
}: ReadingKnowledgeShareActionProps) {
  const router = useRouter();
  const [visibility, setVisibility] = useState(initialVisibility);

  const changeVisibility = useCallback(async (nextVisibility: KnowledgeVisibility) => {
    const result = await updateKnowledgeVisibilityAction(id, nextVisibility);
    if (result.ok) {
      setVisibility(result.knowledge.visibility);
      router.refresh();
    }
    return result;
  }, [id, router]);

  const loadUnlistedLink = useCallback(() => getUnlistedLinkAction(id), [id]);
  const regenerateUnlistedLink = useCallback(() => regenerateUnlistedLinkAction(id), [id]);

  return (
    <KnowledgeShareAction
      slug={slug}
      title={title}
      visibility={visibility}
      onVisibilityChange={changeVisibility}
      onLoadUnlistedLink={loadUnlistedLink}
      onRegenerateUnlistedLink={regenerateUnlistedLink}
    />
  );
}
