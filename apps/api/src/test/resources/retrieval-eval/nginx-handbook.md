# Nginx reverse proxy operations

## Route identity

An Nginx reverse proxy route chooses an upstream for an incoming HTTP request. Confirm the location match and destination before changing timeout values. A correct client address does not prove that the proxy selected the intended backend path.

Use a synthetic upstream that returns a route label and compare exact and prefix locations. Keep the request method and path fixed. Observe whether path rewriting changed the backend request. A forwarded header is evidence only when the hop that supplied it belongs to the declared trust boundary.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:nginx-handbook-route]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Connection establishment

Nginx may fail to connect to an upstream before response reading begins. Distinguish a missing listener, name resolution problem and network boundary from an HTTP response timeout. The error phase determines which diagnostic experiment is useful.

Use a synthetic peer that is first stopped and then available. Verify the transition without sending a real credential. A connect failure should not be described as a slow response merely because the user sees a similar status. Keep one attempt bounded before considering a retry policy.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:nginx-handbook-connect]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Read deadline

Nginx upstream read timeout bounds idle intervals rather than proving one total HTTP operation fits the caller deadline. Align the proxy budget with the Java WebClient caller and identify whether a delayed body or a delayed first response causes the observation.

Use a synthetic peer that sends a response in spaced pieces. Record the phase and elapsed time when the proxy stops waiting. Compare that with the caller's overall timeout. A retry can extend the total duration even if each attempt has a bounded response phase.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:nginx-handbook-read]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Session forwarding

An Nginx route serving an authenticated application must preserve the intended HTTP session boundary. Forwarding cookies to a wrong upstream is not repaired by increasing the read timeout. Keep trusted forwarded-header handling separate from CSRF mutation checks.

Use synthetic session labels, never a real cookie. Verify that the application distinguishes trusted transport metadata from client-manufactured values. The proxy does not become the domain owner resolver. A backend still checks the authenticated principal and state-changing request protection.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:nginx-handbook-headers]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Deployment route

A Docker or Kubernetes deployment can change an upstream address while the public Nginx location remains unchanged. Check service discovery and readiness during replacement. A route that works against one old instance does not prove that every new pod is admitted correctly.

Use a synthetic rolling transition and inspect the selected backend label across requests. Distinguish an unavailable pod from an incorrect service name. Keep a controlled timeout for each route test and do not expose the deployment's private addresses in public error responses.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:nginx-handbook-deploy]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Exhausted retries

A proxy retry policy must remain explicit about which HTTP operations are safe to repeat. A timeout does not prove that the upstream performed no effect. When the allowed attempts are exhausted, return a controlled failure instead of continuing to spin.

Use a synthetic peer that counts attempts and always fails within a bounded period. Observe the count after the caller receives the final failure. Align Nginx and WebClient policies so independent retries do not multiply hidden work. Preserve the final exhausted outcome without leaking response bodies.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:nginx-handbook-failure]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.
