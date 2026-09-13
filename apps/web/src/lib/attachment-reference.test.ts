import assert from "node:assert/strict";
import test from "node:test";
import {
  accessibleImageAlt,
  attachmentIdFromReference,
  resolveImageSource,
} from "./attachment-reference.ts";

const id = "550e8400-e29b-41d4-a716-446655440000";
const reference = `attachment://${id}`;

test("accepts only canonical attachment UUID references", () => {
  assert.equal(attachmentIdFromReference(reference), id);
  assert.equal(attachmentIdFromReference("attachment://../../secret"), null);
  assert.equal(attachmentIdFromReference("https://storage.example/private-key"), null);
});

test("resolves the same stable Markdown reference through each access context", () => {
  assert.equal(
    resolveImageSource(reference, { kind: "owner", knowledgeId: 42 }),
    `/api/knowledge/42/attachments/${id}/content`,
  );
  assert.equal(
    resolveImageSource(reference, { kind: "public", slug: "spring guide" }),
    `/api/public/knowledge/spring%20guide/attachments/${id}/content`,
  );
  assert.equal(
    resolveImageSource(reference, { kind: "shared", shareToken: "secret/token" }),
    `/api/shared/knowledge/secret%2Ftoken/attachments/${id}/content`,
  );
});

test("does not rewrite ordinary image URLs", () => {
  const external = "https://images.example/diagram.png";
  assert.equal(resolveImageSource(external, { kind: "owner", knowledgeId: 42 }), external);
});

test("uses an ImageBlock caption as alt text when Crepe serializes ratio in alt", () => {
  assert.equal(accessibleImageAlt("1.777", "Architecture diagram"), "Architecture diagram");
  assert.equal(accessibleImageAlt("1", undefined), "");
  assert.equal(accessibleImageAlt("Meaningful alt", "Caption"), "Meaningful alt");
  assert.equal(accessibleImageAlt(undefined, undefined), "");
});
