import { NextResponse } from "next/server";
import { focusedImageResponse, getPublicImageResponse } from "@/lib/api/attachments";

export async function GET(
  _request: Request,
  context: { params: Promise<{ slug: string; attachmentId: string }> },
) {
  const { slug, attachmentId } = await context.params;
  try {
    return focusedImageResponse(await getPublicImageResponse(slug, attachmentId));
  } catch {
    return NextResponse.json(
      { code: "PUBLIC_IMAGE_UNAVAILABLE", message: "Public image is unavailable" },
      { status: 503 },
    );
  }
}
