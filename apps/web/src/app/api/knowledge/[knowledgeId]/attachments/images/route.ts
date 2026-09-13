import { NextResponse } from "next/server";
import { BackendApiError } from "@/lib/api/backend";
import { uploadKnowledgeImage } from "@/lib/api/attachments";

export async function POST(
  request: Request,
  context: { params: Promise<{ knowledgeId: string }> },
) {
  const { knowledgeId: knowledgeIdValue } = await context.params;
  const knowledgeId = Number(knowledgeIdValue);
  if (!Number.isSafeInteger(knowledgeId) || knowledgeId <= 0) {
    return NextResponse.json(
      { code: "MALFORMED_REQUEST", message: "Knowledge ID is invalid" },
      { status: 400 },
    );
  }

  try {
    const formData = await request.formData();
    return NextResponse.json(await uploadKnowledgeImage(knowledgeId, formData));
  } catch (error) {
    if (error instanceof BackendApiError) {
      return NextResponse.json(
        { code: error.code, message: error.message, fieldErrors: error.fieldErrors },
        { status: error.status },
      );
    }
    return NextResponse.json(
      { code: "IMAGE_UPLOAD_FAILED", message: "Image upload failed" },
      { status: 503 },
    );
  }
}
