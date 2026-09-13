import "server-only";

import {
  backendRawResponse,
  backendRequest,
  publicBackendRawResponse,
} from "@/lib/api/backend";

export interface ImageAttachmentResponse {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  markdownSource: string;
}

export function uploadKnowledgeImage(knowledgeId: number, formData: FormData) {
  return backendRequest<ImageAttachmentResponse>(
    `/api/knowledge/${knowledgeId}/attachments/images`,
    { method: "POST", body: formData },
  );
}

export function getOwnerImageResponse(knowledgeId: string, attachmentId: string) {
  return backendRawResponse(
    `/api/knowledge/${encodeURIComponent(knowledgeId)}/attachments/${encodeURIComponent(attachmentId)}/content`,
  );
}

export function getPublicImageResponse(slug: string, attachmentId: string) {
  return publicBackendRawResponse(
    `/api/public/knowledge/${encodeURIComponent(slug)}/attachments/${encodeURIComponent(attachmentId)}/content`,
  );
}

export function getSharedImageResponse(shareToken: string, attachmentId: string) {
  return publicBackendRawResponse(
    `/api/shared/knowledge/${encodeURIComponent(shareToken)}/attachments/${encodeURIComponent(attachmentId)}/content`,
  );
}

export function focusedImageResponse(upstream: Response) {
  const headers = new Headers();
  for (const name of [
    "Content-Type",
    "Content-Length",
    "Content-Disposition",
    "Cache-Control",
    "X-Content-Type-Options",
    "X-Robots-Tag",
  ]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers,
  });
}
