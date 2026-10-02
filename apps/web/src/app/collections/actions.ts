"use server";

import { revalidatePath } from "next/cache";
import { BackendApiError } from "@/lib/api/backend";
import {
  createCollection,
  deleteCollection,
  renameCollection,
} from "@/lib/api/collections";
import type { CollectionActionResult, DeleteCollectionActionResult } from "@/types/collection";

function validId(id: number) {
  return Number.isSafeInteger(id) && id > 0;
}

function validName(name: string) {
  return typeof name === "string" && name.trim().length > 0 && name.length <= 100;
}

function failure(error: unknown) {
  if (error instanceof BackendApiError) {
    if (error.status === 401 || error.status === 403) {
      return "Your session is no longer authorized. Sign in again.";
    }
    if (error.status >= 500) {
      return "The collection could not be saved. Check the API and try again.";
    }
    return error.message;
  }
  return "The collection request failed. Try again.";
}

function revalidateCollectionViews() {
  revalidatePath("/");
  revalidatePath("/collections");
  revalidatePath("/collections/[id]", "page");
  revalidatePath("/search");
  revalidatePath("/knowledge/[slug]", "page");
  revalidatePath("/knowledge/[slug]/edit", "page");
  revalidatePath("/knowledge/[slug]/history", "page");
  revalidatePath("/k/[slug]", "page");
  revalidatePath("/s/[shareToken]", "page");
}

export async function createCollectionAction(name: string): Promise<CollectionActionResult> {
  if (!validName(name)) return { ok: false, message: "Enter a collection name of at most 100 characters." };
  try {
    const collection = await createCollection(name);
    revalidateCollectionViews();
    return { ok: true, collection };
  } catch (error) {
    return { ok: false, message: failure(error) };
  }
}

export async function renameCollectionAction(id: number, name: string): Promise<CollectionActionResult> {
  if (!validId(id) || !validName(name)) {
    return { ok: false, message: "Enter a collection name of at most 100 characters." };
  }
  try {
    const collection = await renameCollection(id, name);
    revalidateCollectionViews();
    return { ok: true, collection };
  } catch (error) {
    return { ok: false, message: failure(error) };
  }
}

export async function deleteCollectionAction(id: number): Promise<DeleteCollectionActionResult> {
  if (!validId(id)) return { ok: false, message: "The collection request is invalid." };
  try {
    await deleteCollection(id);
    revalidateCollectionViews();
    return { ok: true };
  } catch (error) {
    return { ok: false, message: failure(error) };
  }
}
