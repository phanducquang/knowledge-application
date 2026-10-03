# WebClient timeout retry HTTP Java playbook

## Response phase [eval:client-response]

This synthetic Java WebClient procedure begins by identifying which HTTP phase is hanging. A response timeout governs waiting for response activity after a request has been sent. It is not automatically a complete end-to-end budget. Establish one caller deadline and ensure every downstream operation fits inside it. Record the expected normal response duration and the maximum acceptable wait before choosing a value. A timeout is a bounded failure, never an instruction to return a successful empty body. This section deliberately includes enough operational explanation to form a meaningful Markdown chunk at the real default chunk size, rather than reducing the exercise to isolated titles.

Use a small HTTP test service that delays its response. The Java WebClient call should stop within its configured response timeout, while an ordinary fast response should still succeed. Verify both behaviors. Compare elapsed time with a broad testing tolerance rather than a precise millisecond assertion. The purpose is to expose a missing deadline without producing a flaky timer test. Keep retry disabled during this first experiment so a second attempt cannot disguise the response-phase result.

```java
HttpClient http = HttpClient.create().responseTimeout(Duration.ofSeconds(5));
WebClient client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
```

## Connection phase [eval:client-connection]

A Java WebClient HTTP request may fail before the response phase begins. Distinguish connection establishment from waiting for response activity. A timeout in one phase does not necessarily configure the other. Document the failure type observed by the caller and preserve that distinction when translating errors at the application boundary. Avoid a catch-all branch that treats every timeout as the same kind of business result. Record a short synthetic experiment showing a failed connection separately from a delayed successful connection, without storing request bodies or any private destination.

The procedure uses a local test peer and a bounded deadline. It does not instruct an operator to weaken transport validation or to forward confidential credentials to an arbitrary address. A retry of a connection failure may be permitted by application policy, but it is not the default assumption in this note. First confirm that the Java WebClient timeout behavior is correct for one HTTP attempt. Then identify which operations can safely be repeated. Explicitly document whether a caller abandons the operation or waits for a controlled retry. A hidden repeat can consume the remaining deadline and make the observed latency larger than the original response timeout.

- Identify the failure phase.
- Keep the synthetic peer isolated.
- Check one HTTP attempt before considering retry.

## Caller deadline [eval:client-deadline]

One HTTP caller can perform several Java WebClient operations. The sum of their individual timeout budgets may exceed the time available to the caller. Plan the overall deadline before allocating phase limits. The caller should stop work that can no longer produce a useful response within that deadline. A response timeout alone does not imply that every transformation or later HTTP operation is bounded. Review the composed flow and confirm that a timeout exception reaches the application boundary without being translated into a fabricated successful response.

For the synthetic example, assume an overall ten-second deadline and a five-second response timeout. The remaining time must cover preparation, response handling and any explicitly permitted retry. These values are examples for a local procedure, not production recommendations. Observe a fast response, a delayed response and a peer that never supplies response activity. Keep the resulting Java WebClient behavior separate from HTTP status handling: a completed failure status is not necessarily a timeout. A retry policy must distinguish these cases. Record only aggregate timing and the phase label, avoiding payloads. A diagnostic trace is useful only if it preserves the deadline semantics being investigated.

## Controlled retry [eval:client-retry]

Retry is an application decision after a Java WebClient HTTP failure. Before enabling it, identify whether the operation is safe to repeat and whether enough of the caller deadline remains. A timeout does not prove that the remote peer performed no work. Repeating an unsafe operation can duplicate an effect even when no response was observed. Do not automatically retry merely because a response was hanging. For a safe read, limit the attempt count and use a bounded delay; for an unsafe operation, require a separately designed repeat-safety contract.

Test the policy with a synthetic local HTTP peer that records only the number of attempts. Confirm that the Java WebClient call makes one attempt when retry is disabled. When an explicitly bounded retry is enabled for the experiment, confirm that the attempt count never exceeds the declared maximum and that the caller deadline still wins. The response timeout applies to one attempt, not necessarily the entire repeated sequence. A retry that succeeds after an excessive delay can still violate the original service expectation. Keep the procedure explicit about this distinction. Do not transform a retry exhaustion exception into a successful empty response just to silence the failure.

## Exhaustion outcome [eval:client-exhaustion]

When a bounded Java WebClient retry policy has used every permitted HTTP attempt, return a clear controlled failure. Preserve the distinction between an exhausted repeat policy and the original timeout phase. The caller needs to know that no further attempt will be made by this operation. A background component may resume independently later, but this note does not describe an unbounded retry loop. Do not continue spinning while the peer remains unavailable. A hanging response must remain bounded even when the repeat policy is configured incorrectly; the caller deadline supplies the final limit.

The synthetic procedure verifies that no extra HTTP attempt appears after exhaustion. Observe the attempt count after the caller receives failure, not only during the first timeout. Record the declared maximum and the actual count in the test description. The Java WebClient outcome should not leak a private response body or destination credential. It should make the operational failure legible without pretending the remote peer never executed the request. This section is intentionally later than the first two highly similar chunks. Retrieval evaluation can therefore expose a useful-section miss when a per-note chunk cap selects the early timeout discussion instead of this exhaustion procedure.

## Cancellation boundary [eval:client-cancel]

Cancellation of a Java WebClient HTTP subscriber stops the local flow from observing later results. It does not universally prove that remote work was canceled or that usage was refunded. Treat local cancellation and remote completion as different facts. A timeout may cause the local caller to abandon an operation while the peer is still processing it. The procedure should explain that limitation without adding a hidden retry to recover the abandoned response. When the caller deadline expires, suppress obsolete results so they cannot replace the outcome of a newer operation.

Use a synthetic delayed peer to examine the sequence. Begin one Java WebClient call, abandon the local subscription before the HTTP response arrives, and inspect only the bounded local state changes. Do not claim that this experiment proves every remote server will stop work. If the application subsequently issues another request, record it as a new explicit operation rather than a continuation of the canceled one. Its timeout and retry policy must have their own budget. This separation prevents an apparently harmless refresh from becoming a silent unbounded repeat mechanism. Keep logs aggregate and free of response bodies.

## Observability procedure [eval:client-observe]

For a Java WebClient HTTP timeout investigation, collect the phase label, elapsed duration and aggregate attempt count. These are sufficient to distinguish many bounded response failures from unbounded hanging work. Do not store request or response bodies merely to create a timing trace. A retry policy should contribute an explicit count and final exhausted status, not a long sequence of raw exceptions containing private content. Preserve the caller deadline alongside the per-attempt response timeout so an operator can understand why the overall operation stopped.

Compare a normal fast HTTP response with a delayed synthetic response using the same Java WebClient configuration. Review the configured values and the observed outcome together. If the response timeout did not trigger, inspect whether it was applied to the actual underlying client rather than a separate unused instance. If elapsed time is much larger than expected, inspect the retry count and the caller deadline. Do not change several settings at once because the resulting experiment would no longer identify the cause. This procedure is descriptive synthetic content; it is not executed by the retrieval evaluation harness.

## Boundary checklist [eval:client-checklist]

Before finishing a Java WebClient HTTP timeout change, check response phase, connection phase, caller deadline and retry exhaustion separately. Each is a different part of the request lifecycle. Keep the values explicit in the synthetic procedure and avoid treating a response timeout as proof that all remote effects were canceled. A hanging operation should have a bounded local outcome, and an ordinary fast response should remain successful. Review that a controlled failure remains a failure rather than becoming an apparently valid empty response.

- Verify one HTTP attempt with retry disabled.
- Distinguish completed status handling from timeout exceptions.
- Bound any explicitly allowed retry by count and caller deadline.
- Preserve an exhausted outcome after the final attempt.
- Keep payloads and destination credentials out of diagnostic output.

The checklist closes the synthetic Java WebClient playbook. It includes headings, paragraphs, lists, a fenced example and long source sections so the real Markdown chunker has work to do. Retrieval evaluation must use its configured default chunk size and overlap rather than shrinking production settings to manufacture more chunks. The text intentionally shares the main timeout vocabulary across sections, making the per-note diversity limit observable without claiming that a simple offline vocabulary can infer the precise useful paragraph for every natural-language query.
