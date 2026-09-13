import "server-only";

import { cookies } from "next/headers";
import {
  buildBackendHeaders,
  isStateChangingMethod,
  serializeSessionCookies,
  type BackendCsrfToken,
} from "@/lib/backend-auth";

interface ApiErrorResponse {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

export class BackendApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly fieldErrors: Record<string, string> = {},
    message = "Backend API request failed",
  ) {
    super(message);
    this.name = "BackendApiError";
  }
}

function apiBaseUrl() {
  return (process.env.KNOWLEDGE_API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

export function browserApiBaseUrl() {
  return (
    process.env.KNOWLEDGE_API_BROWSER_BASE_URL ??
    process.env.KNOWLEDGE_API_BASE_URL ??
    "http://localhost:8080"
  ).replace(/\/$/, "");
}

async function incomingCookieHeader() {
  return serializeSessionCookies((await cookies()).getAll());
}

async function fetchBackend(
  path: string,
  cookieHeader: string,
  init?: RequestInit,
  csrf?: BackendCsrfToken,
) {
  try {
    return await fetch(`${apiBaseUrl()}${path}`, {
      ...init,
      cache: "no-store",
      headers: buildBackendHeaders(cookieHeader, init?.headers, csrf),
    });
  } catch {
    throw new BackendApiError(
      503,
      "BACKEND_API_UNAVAILABLE",
      {},
      "The backend API is currently unavailable",
    );
  }
}

async function throwBackendError(response: Response): Promise<never> {
  let error: ApiErrorResponse = {};
  try {
    error = (await response.json()) as ApiErrorResponse;
  } catch {
    // Authentication failures and infrastructure responses may intentionally have no JSON body.
  }

  throw new BackendApiError(
    response.status,
    error.code ?? (response.status === 401 ? "UNAUTHENTICATED" : "BACKEND_API_ERROR"),
    error.fieldErrors ?? {},
    error.message ?? (response.status === 401 ? "Authentication is required" : "Backend API request failed"),
  );
}

async function csrfToken(cookieHeader: string) {
  const response = await fetchBackend("/api/auth/csrf", cookieHeader);
  if (!response.ok) {
    return throwBackendError(response);
  }
  return (await response.json()) as BackendCsrfToken;
}

export async function backendRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const cookieHeader = await incomingCookieHeader();
  const csrf = isStateChangingMethod(init?.method) ? await csrfToken(cookieHeader) : undefined;
  const response = await fetchBackend(path, cookieHeader, init, csrf);

  if (!response.ok) {
    return throwBackendError(response);
  }

  return (await response.json()) as T;
}

export async function backendRawResponse(path: string, init?: RequestInit) {
  const cookieHeader = await incomingCookieHeader();
  const csrf = isStateChangingMethod(init?.method) ? await csrfToken(cookieHeader) : undefined;
  return fetchBackend(path, cookieHeader, init, csrf);
}

export async function publicBackendRawResponse(path: string) {
  return fetchBackend(path, "", { method: "GET" });
}

export async function publicBackendRequest<T>(path: string): Promise<T> {
  const response = await fetchBackend(path, "", { method: "GET" });
  if (!response.ok) {
    return throwBackendError(response);
  }
  return (await response.json()) as T;
}

export async function backendLogout() {
  const cookieHeader = await incomingCookieHeader();
  const csrf = await csrfToken(cookieHeader);
  const response = await fetchBackend(
    "/api/auth/logout",
    cookieHeader,
    { method: "POST" },
    csrf,
  );
  if (!response.ok) {
    return throwBackendError(response);
  }
  return response;
}
