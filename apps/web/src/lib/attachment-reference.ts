export const ATTACHMENT_SCHEME = "attachment://";

const attachmentReferencePattern =
  /^attachment:\/\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/i;

export type ImageAccessContext =
  | { kind: "owner"; knowledgeId: number }
  | { kind: "public"; slug: string }
  | { kind: "shared"; shareToken: string };

export function attachmentIdFromReference(source: string) {
  return attachmentReferencePattern.exec(source)?.[1]?.toLowerCase() ?? null;
}

export function resolveImageSource(source: string, context: ImageAccessContext) {
  const attachmentId = attachmentIdFromReference(source);
  if (!attachmentId) return source;

  if (context.kind === "owner") {
    return `/api/knowledge/${context.knowledgeId}/attachments/${attachmentId}/content`;
  }
  if (context.kind === "public") {
    return `/api/public/knowledge/${encodeURIComponent(context.slug)}/attachments/${attachmentId}/content`;
  }
  return `/api/shared/knowledge/${encodeURIComponent(context.shareToken)}/attachments/${attachmentId}/content`;
}

export function accessibleImageAlt(alt: string | undefined, title: string | undefined) {
  if (/^\d+(?:\.\d+)?$/.test(alt ?? "")) return title ?? "";
  return alt ?? title ?? "";
}
