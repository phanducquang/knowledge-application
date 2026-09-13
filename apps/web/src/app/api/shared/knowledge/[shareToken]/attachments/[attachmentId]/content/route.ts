import { NextResponse } from "next/server";
import { focusedImageResponse, getSharedImageResponse } from "@/lib/api/attachments";

export async function GET(
  _request: Request,
  context: { params: Promise<{ shareToken: string; attachmentId: string }> },
) {
  const { shareToken, attachmentId } = await context.params;
  try {
    return focusedImageResponse(await getSharedImageResponse(shareToken, attachmentId));
  } catch {
    return NextResponse.json(
      { code: "SHARED_IMAGE_UNAVAILABLE", message: "Shared image is unavailable" },
      { status: 503 },
    );
  }
}
