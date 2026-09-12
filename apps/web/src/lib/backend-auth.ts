export interface SessionCookie {
  name: string;
  value: string;
}

export interface BackendCsrfToken {
  token: string;
  headerName: string;
}

export interface BackendCurrentUser {
  email: string;
  name: string | null;
  picture: string | null;
}

export interface CurrentUser {
  email: string;
  name: string | null;
  picture: string | null;
}

export function serializeSessionCookies(cookies: SessionCookie[]) {
  return cookies.map(({ name, value }) => `${name}=${value}`).join("; ");
}

export function buildBackendHeaders(
  cookieHeader: string,
  initialHeaders?: HeadersInit,
  csrf?: BackendCsrfToken,
) {
  const headers = new Headers(initialHeaders);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (cookieHeader) {
    headers.set("Cookie", cookieHeader);
  }
  if (csrf) {
    headers.set(csrf.headerName, csrf.token);
  }
  return headers;
}

export function authRedirectForStatus(status: number) {
  return status === 401 || status === 403 ? "/login" : null;
}

export function mapCurrentUser(response: BackendCurrentUser): CurrentUser {
  return {
    email: response.email,
    name: response.name ?? null,
    picture: response.picture ?? null,
  };
}

export function isStateChangingMethod(method?: string) {
  return !["GET", "HEAD", "OPTIONS", "TRACE"].includes((method ?? "GET").toUpperCase());
}

export function logoutRedirectHeaders(setCookie: string | null) {
  const headers = new Headers();
  if (setCookie) {
    headers.set("Set-Cookie", setCookie);
  }
  return headers;
}
