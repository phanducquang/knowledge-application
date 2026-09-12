import assert from "node:assert/strict";
import test from "node:test";
import {
  authRedirectForStatus,
  buildBackendHeaders,
  isStateChangingMethod,
  logoutRedirectHeaders,
  mapCurrentUser,
  serializeSessionCookies,
} from "./backend-auth.ts";

test("forwards the incoming authenticated session cookies", () => {
  const cookieHeader = serializeSessionCookies([
    { name: "JSESSIONID", value: "opaque-session" },
    { name: "preference", value: "compact" },
  ]);
  const headers = buildBackendHeaders(cookieHeader);

  assert.equal(headers.get("cookie"), "JSESSIONID=opaque-session; preference=compact");
  assert.equal(headers.get("accept"), "application/json");
});

test("maps auth/me presentation fields without adding owner or session data", () => {
  assert.deepEqual(
    mapCurrentUser({ email: "owner@example.com", name: "Owner", picture: null }),
    { email: "owner@example.com", name: "Owner", picture: null },
  );
});

test("redirects private routes on authentication and authorization failures", () => {
  assert.equal(authRedirectForStatus(401), "/login");
  assert.equal(authRedirectForStatus(403), "/login");
  assert.equal(authRedirectForStatus(503), null);
});

test("adds the fetched CSRF token only to state-changing requests", () => {
  const headers = buildBackendHeaders("JSESSIONID=session", { "Content-Type": "application/json" }, {
    token: "csrf-token",
    headerName: "X-CSRF-TOKEN",
  });

  assert.equal(headers.get("x-csrf-token"), "csrf-token");
  assert.equal(headers.get("content-type"), "application/json");
  assert.equal(isStateChangingMethod("POST"), true);
  assert.equal(isStateChangingMethod("PUT"), true);
  assert.equal(isStateChangingMethod("GET"), false);
});

test("forwards the backend session-clearing cookie on logout redirect", () => {
  const headers = logoutRedirectHeaders("JSESSIONID=; Max-Age=0; Path=/; HttpOnly");
  assert.equal(headers.get("set-cookie"), "JSESSIONID=; Max-Age=0; Path=/; HttpOnly");
});
