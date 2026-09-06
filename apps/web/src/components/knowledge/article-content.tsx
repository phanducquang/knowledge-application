export function ArticleContent() {
  return (
    <div className="article-content text-[16px] leading-[1.72] text-[var(--text)]">
      <section id="problem" className="scroll-mt-8">
        <h2 className="mb-4 text-[22px] font-semibold leading-tight tracking-[-0.02em]">Problem</h2>
        <p className="text-[var(--text-muted)]">
          A downstream search service can accept a connection and then take too long to produce a useful response. Without an explicit application-level timeout, that latency leaks into the caller and makes the failure mode harder to classify.
        </p>
        <p className="mt-4 text-[var(--text-muted)]">
          The timeout should belong to the reactive operation that owns the latency budget. Recovery should happen after that boundary so timeout, HTTP, and connection failures can still be observed as distinct causes.
        </p>
      </section>

      <section id="timeout-placement" className="mt-10 scroll-mt-8">
        <h2 className="mb-4 text-[22px] font-semibold leading-tight tracking-[-0.02em]">Timeout placement</h2>
        <p className="text-[var(--text-muted)]">
          Keep the happy-path transformation visible, then apply the timeout before the final recovery step.
        </p>

        <pre className="my-5 overflow-x-auto border-y border-[var(--border-strong)] bg-[var(--surface-muted)] px-4 py-4 text-[13px] leading-6 text-[var(--text)]">
          <code>{`handler.search(query)
    .timeout(handler.getTimeout())
    .doOnError(TimeoutException.class, ex ->
        log.warn("Search timed out. query={}", query))
    .doOnError(ex -> !(ex instanceof TimeoutException), ex ->
        log.error("Search request failed. query={}", query, ex))
    .map(result -> buildEvent("success", result))
    .onErrorResume(error -> Mono.just(
        buildEvent(error instanceof TimeoutException
            ? "timeout"
            : "error", null)
    ));`}</code>
        </pre>

        <blockquote className="my-6 border-l-2 border-[var(--accent)] pl-4 text-[15px] leading-7 text-[var(--text-muted)]">
          Treat timeout as a classification boundary, not as a reason to hide the original reactive flow behind a second recovery abstraction.
        </blockquote>

        <h3 className="mb-3 mt-7 text-[17px] font-semibold tracking-[-0.01em]">Why this order matters</h3>
        <ul className="list-disc space-y-2 pl-5 text-[var(--text-muted)] marker:text-[var(--accent-muted)]">
          <li>`timeout` converts excessive latency into a deterministic error signal.</li>
          <li>`doOnError` observes the failure without consuming it.</li>
          <li>`onErrorResume` is the single place that converts the failure into the application result.</li>
        </ul>
      </section>

      <section id="error-boundaries" className="mt-10 scroll-mt-8">
        <h2 className="mb-4 text-[22px] font-semibold leading-tight tracking-[-0.02em]">Error boundaries</h2>
        <p className="text-[var(--text-muted)]">
          Keep logging and fallback responsibilities separate. A compact comparison makes the intent easier to review later.
        </p>

        <div className="my-6 overflow-x-auto">
          <table className="w-full min-w-[560px] border-collapse text-left text-[13px] leading-5">
            <thead>
              <tr className="border-y border-[var(--border-strong)] text-[11px] uppercase tracking-[0.08em] text-[var(--text-subtle)]">
                <th className="py-2 pr-5 font-semibold">Operator</th>
                <th className="py-2 pr-5 font-semibold">Responsibility</th>
                <th className="py-2 font-semibold">Consumes error</th>
              </tr>
            </thead>
            <tbody className="text-[var(--text-muted)]">
              <tr className="border-b border-[var(--border)]">
                <td className="py-3 pr-5 font-mono text-[12px] text-[var(--text)]">timeout</td>
                <td className="py-3 pr-5">Enforce the latency budget</td>
                <td className="py-3">No</td>
              </tr>
              <tr className="border-b border-[var(--border)]">
                <td className="py-3 pr-5 font-mono text-[12px] text-[var(--text)]">doOnError</td>
                <td className="py-3 pr-5">Observe and log the failure</td>
                <td className="py-3">No</td>
              </tr>
              <tr className="border-b border-[var(--border)]">
                <td className="py-3 pr-5 font-mono text-[12px] text-[var(--text)]">onErrorResume</td>
                <td className="py-3 pr-5">Return the fallback application event</td>
                <td className="py-3">Yes</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section id="decision" className="mt-10 scroll-mt-8">
        <h2 className="mb-4 text-[22px] font-semibold leading-tight tracking-[-0.02em]">Decision</h2>
        <ol className="list-decimal space-y-2 pl-5 text-[var(--text-muted)] marker:font-medium marker:text-[var(--accent-strong)]">
          <li>Each search handler owns its timeout duration.</li>
          <li>The aggregator applies the timeout to the handler call.</li>
          <li>Logging observes the original error before fallback conversion.</li>
          <li>One final recovery step maps the error into `timeout` or `error` status.</li>
        </ol>
      </section>
    </div>
  );
}
