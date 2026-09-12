export interface MutationResult {
  ok: boolean;
}

interface SerializedVisibilityChange<R extends MutationResult> {
  inFlightSave: Promise<unknown> | null;
  flushPendingDraft: () => Promise<R | null>;
  patchVisibility: () => Promise<R>;
}

export async function runSerializedVisibilityChange<R extends MutationResult>({
  inFlightSave,
  flushPendingDraft,
  patchVisibility,
}: SerializedVisibilityChange<R>): Promise<R> {
  if (inFlightSave) await inFlightSave;
  const flushResult = await flushPendingDraft();
  if (flushResult && !flushResult.ok) return flushResult;
  return patchVisibility();
}
