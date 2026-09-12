import "server-only";

import { cache } from "react";
import { backendLogout, backendRequest } from "@/lib/api/backend";
import {
  mapCurrentUser,
  type BackendCurrentUser,
} from "@/lib/backend-auth";

export const getCurrentUser = cache(async () => {
  const response = await backendRequest<BackendCurrentUser>("/api/auth/me");
  return mapCurrentUser(response);
});

export function logoutSession() {
  return backendLogout();
}
