import { NextRequest, NextResponse } from "next/server";
import { logoutSession } from "@/lib/api/auth";
import { BackendApiError } from "@/lib/api/backend";
import { logoutRedirectHeaders } from "@/lib/backend-auth";

export async function POST(request: NextRequest) {
  const origin = request.headers.get("origin");
  if (origin && origin !== request.nextUrl.origin) {
    return NextResponse.json(
      { code: "INVALID_ORIGIN", message: "Logout origin is invalid" },
      { status: 403 },
    );
  }

  let setCookie: string | null = null;
  try {
    const response = await logoutSession();
    setCookie = response.headers.get("set-cookie");
  } catch (error) {
    if (!(error instanceof BackendApiError) || ![401, 403].includes(error.status)) {
      throw error;
    }
  }

  return NextResponse.redirect(new URL("/login", request.url), {
    status: 303,
    headers: logoutRedirectHeaders(setCookie),
  });
}
