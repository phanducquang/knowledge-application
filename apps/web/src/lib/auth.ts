import "server-only";

import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/api/auth";
import { BackendApiError } from "@/lib/api/backend";
import { authRedirectForStatus } from "@/lib/backend-auth";

export async function requireCurrentUser() {
  try {
    return await getCurrentUser();
  } catch (error) {
    if (error instanceof BackendApiError) {
      const destination = authRedirectForStatus(error.status);
      if (destination) {
        redirect(destination);
      }
    }
    throw error;
  }
}
