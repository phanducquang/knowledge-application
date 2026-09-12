import assert from "node:assert/strict";
import test from "node:test";
import { runSerializedVisibilityChange } from "./share-visibility-coordinator.ts";

test("serializes an in-flight autosave, pending draft flush, visibility patch and later autosave", async () => {
  const writes: string[] = [];
  let releaseInFlight: (() => void) | undefined;
  const inFlight = new Promise<void>((resolve) => {
    releaseInFlight = () => {
      writes.push("PUT old snapshot PRIVATE");
      resolve();
    };
  });

  const change = runSerializedVisibilityChange({
    inFlightSave: inFlight,
    flushPendingDraft: async () => {
      writes.push("PUT latest draft PRIVATE");
      return { ok: true };
    },
    patchVisibility: async () => {
      writes.push("PATCH visibility PUBLIC");
      return { ok: true };
    },
  });

  await Promise.resolve();
  assert.equal(writes.length, 0);
  releaseInFlight?.();
  assert.equal((await change).ok, true);
  writes.push("PUT later edit PUBLIC");

  assert.deepEqual(writes, [
    "PUT old snapshot PRIVATE",
    "PUT latest draft PRIVATE",
    "PATCH visibility PUBLIC",
    "PUT later edit PUBLIC",
  ]);
});

test("does not patch visibility when the pending draft flush fails", async () => {
  let patched = false;
  const result = await runSerializedVisibilityChange<{ ok: boolean; reason?: string }>({
    inFlightSave: null,
    flushPendingDraft: async () => ({ ok: false, reason: "save failed" }),
    patchVisibility: async () => {
      patched = true;
      return { ok: true };
    },
  });

  assert.equal(result.ok, false);
  assert.equal(patched, false);
});
