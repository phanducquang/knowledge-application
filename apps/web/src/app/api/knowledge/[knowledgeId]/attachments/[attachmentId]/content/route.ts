import { NextResponse } from "next/server";
import { focusedImageResponse, getOwnerImageResponse } from "@/lib/api/attachments";

export async function GET(
  _request: Request,
  context: { params: Promise<{ knowledgeId: string; attachmentId: string }> },
) {
  const { knowledgeId, attachmentId } = await context.params;
  try {
    return focusedImageResponse(await getOwnerImageResponse(knowledgeId, attachmentId));
  } catch {
    return NextResponse.json(
      { code: "IMAGE_CONTENT_UNAVAILABLE", message: "Image content is unavailable" },
      { status: 503 },
    );
  }
}
