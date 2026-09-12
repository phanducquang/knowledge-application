import { NextResponse } from "next/server";
import { browserApiBaseUrl } from "@/lib/api/backend";

export function GET() {
  return NextResponse.redirect(`${browserApiBaseUrl()}/oauth2/authorization/google`);
}
