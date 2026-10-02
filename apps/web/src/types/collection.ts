export interface CollectionData {
  id: number;
  name: string;
  knowledgeCount: number;
}

export type CollectionActionResult =
  | { ok: true; collection: CollectionData }
  | { ok: false; message: string };

export type DeleteCollectionActionResult =
  | { ok: true }
  | { ok: false; message: string };
