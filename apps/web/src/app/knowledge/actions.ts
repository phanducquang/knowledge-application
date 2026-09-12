"use server";

import { revalidatePath } from "next/cache";
import {
  createKnowledge,
  getKnowledgeRevision,
  getUnlistedLink,
  KnowledgeApiError,
  listKnowledgeRevisions,
  regenerateUnlistedLink,
  restoreKnowledgeRevision,
  updateKnowledge,
  updateKnowledgeVisibility,
} from "@/lib/api/knowledge";
import type {
  KnowledgeActionError,
  KnowledgeActionResult,
  KnowledgeDraft,
  KnowledgeRevisionDetailActionResult,
  KnowledgeRevisionPageActionResult,
  KnowledgeVisibility,
  UnlistedLinkActionResult,
} from "@/types/knowledge";

function actionFailure(error: unknown): KnowledgeActionResult {
  if (error instanceof KnowledgeApiError) {
    return {
      ok: false,
      error: {
        message:
          error.status === 401 || error.status === 403
            ? "Your session is no longer authorized. Sign in again."
            : error.status >= 500
            ? "The note could not be saved. Check the API and try again."
            : error.message,
        fieldErrors: error.fieldErrors,
      },
    };
  }

  return {
    ok: false,
    error: {
      message: "The note could not be saved. Try again.",
      fieldErrors: {},
    },
  };
}

function linkActionFailure(error: unknown): UnlistedLinkActionResult {
  if (error instanceof KnowledgeApiError) {
    return {
      ok: false,
      error: {
        message:
          error.status === 401 || error.status === 403
            ? "Your session is no longer authorized. Sign in again."
            : error.status >= 500
              ? "The share link could not be loaded. Check the API and try again."
              : error.message,
        fieldErrors: error.fieldErrors,
      },
    };
  }
  return {
    ok: false,
    error: { message: "The share link request failed. Try again.", fieldErrors: {} },
  };
}

function validKnowledgeId(id: number) {
  return Number.isSafeInteger(id) && id > 0;
}

function validVisibility(value: KnowledgeVisibility) {
  return value === "Private" || value === "Unlisted" || value === "Public";
}

function invalidShareInput(): KnowledgeActionResult {
  return {
    ok: false,
    error: { message: "The share request is invalid.", fieldErrors: {} },
  };
}

function revalidateKnowledgeRoutes(slug: string) {
  revalidatePath("/");
  revalidatePath("/search");
  revalidatePath(`/knowledge/${slug}`);
  revalidatePath(`/knowledge/${slug}/edit`);
  revalidatePath(`/knowledge/${slug}/history`);
}

function revisionFailure(error: unknown): KnowledgeActionError {
  if (error instanceof KnowledgeApiError) {
    return {
      message:
        error.status === 401 || error.status === 403
          ? "Your session is no longer authorized. Sign in again."
          : error.status >= 500
            ? "Revision history is temporarily unavailable. Check the API and try again."
            : error.message,
      fieldErrors: error.fieldErrors,
    };
  }
  return { message: "Revision history could not be loaded. Try again.", fieldErrors: {} };
}

export async function createKnowledgeAction(
  draft: KnowledgeDraft,
): Promise<KnowledgeActionResult> {
  try {
    const knowledge = await createKnowledge(draft);
    revalidateKnowledgeRoutes(knowledge.slug);
    return { ok: true, knowledge };
  } catch (error) {
    return actionFailure(error);
  }
}

export async function updateKnowledgeAction(
  id: number,
  slug: string,
  draft: KnowledgeDraft,
): Promise<KnowledgeActionResult> {
  try {
    const knowledge = await updateKnowledge(id, draft);
    revalidateKnowledgeRoutes(slug);
    return { ok: true, knowledge };
  } catch (error) {
    return actionFailure(error);
  }
}

export async function updateKnowledgeVisibilityAction(
  id: number,
  visibility: KnowledgeVisibility,
): Promise<KnowledgeActionResult> {
  if (!validKnowledgeId(id) || !validVisibility(visibility)) {
    return invalidShareInput();
  }

  try {
    const knowledge = await updateKnowledgeVisibility(id, visibility);
    revalidateKnowledgeRoutes(knowledge.slug);
    return { ok: true, knowledge };
  } catch (error) {
    return actionFailure(error);
  }
}

export async function getUnlistedLinkAction(
  id: number,
): Promise<UnlistedLinkActionResult> {
  if (!validKnowledgeId(id)) {
    return linkActionFailure(new Error("Invalid Knowledge ID"));
  }

  try {
    return { ok: true, link: await getUnlistedLink(id) };
  } catch (error) {
    return linkActionFailure(error);
  }
}

export async function regenerateUnlistedLinkAction(
  id: number,
): Promise<UnlistedLinkActionResult> {
  if (!validKnowledgeId(id)) {
    return linkActionFailure(new Error("Invalid Knowledge ID"));
  }

  try {
    return { ok: true, link: await regenerateUnlistedLink(id) };
  } catch (error) {
    return linkActionFailure(error);
  }
}

export async function listKnowledgeRevisionsAction(
  id: number,
  page: number,
): Promise<KnowledgeRevisionPageActionResult> {
  if (!validKnowledgeId(id) || !Number.isSafeInteger(page) || page < 0) {
    return { ok: false, error: revisionFailure(new Error("Invalid revision page")) };
  }
  try {
    return { ok: true, page: await listKnowledgeRevisions(id, page) };
  } catch (error) {
    return { ok: false, error: revisionFailure(error) };
  }
}

export async function getKnowledgeRevisionAction(
  id: number,
  revisionId: number,
): Promise<KnowledgeRevisionDetailActionResult> {
  if (!validKnowledgeId(id) || !validKnowledgeId(revisionId)) {
    return { ok: false, error: revisionFailure(new Error("Invalid revision")) };
  }
  try {
    return { ok: true, revision: await getKnowledgeRevision(id, revisionId) };
  } catch (error) {
    return { ok: false, error: revisionFailure(error) };
  }
}

export async function restoreKnowledgeRevisionAction(
  id: number,
  revisionId: number,
): Promise<KnowledgeActionResult> {
  if (!validKnowledgeId(id) || !validKnowledgeId(revisionId)) {
    return { ok: false, error: revisionFailure(new Error("Invalid revision")) };
  }
  try {
    const knowledge = await restoreKnowledgeRevision(id, revisionId);
    revalidateKnowledgeRoutes(knowledge.slug);
    return { ok: true, knowledge };
  } catch (error) {
    return { ok: false, error: revisionFailure(error) };
  }
}
