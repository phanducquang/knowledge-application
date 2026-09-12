import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/api/auth";
import { BackendApiError } from "@/lib/api/backend";

export const dynamic = "force-dynamic";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string | string[] }>;
}) {
  try {
    await getCurrentUser();
    redirect("/");
  } catch (error) {
    if (!(error instanceof BackendApiError) || ![401, 403, 503].includes(error.status)) {
      throw error;
    }
  }

  const params = await searchParams;
  const error = Array.isArray(params.error) ? params.error[0] : params.error;

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-[720px] items-center px-5 py-16 sm:px-8">
      <section className="w-full border-y border-[var(--border-strong)] py-10 sm:py-12">
        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">
          Knowledge / Private workspace
        </p>
        <h1 className="mt-3 text-[32px] font-semibold leading-[1.1] tracking-[-0.035em] text-[var(--text)] sm:text-[36px]">
          Sign in to your knowledge workspace
        </h1>
        <p className="mt-4 max-w-xl text-[15px] leading-7 text-[var(--text-muted)]">
          Continue with the verified Google account authorized for this private, single-owner workspace.
        </p>

        {error === "unauthorized" && (
          <p role="alert" className="mt-5 border-l-2 border-[var(--danger)] pl-3 text-[14px] text-[var(--danger)]">
            Unauthorized account. Use the Google account configured for this workspace.
          </p>
        )}

        <a
          href="/api/auth/login"
          className="mt-7 inline-flex border border-[var(--accent)] bg-[var(--accent)] px-4 py-2.5 text-[14px] font-medium text-[var(--accent-contrast)] transition-colors hover:bg-[var(--accent-strong)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          Continue with Google
        </a>
      </section>
    </main>
  );
}
