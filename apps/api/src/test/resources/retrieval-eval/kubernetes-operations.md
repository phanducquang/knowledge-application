# Kubernetes deployment operations

## Readiness admission

Kubernetes readiness decides whether a pod receives traffic. Liveness recovery and readiness admission are separate decisions. A delayed dependency may justify removing one replica from service without restarting the whole deployment. Start by identifying the dependency needed to serve a request and whether the readiness endpoint tests that dependency or merely reports that the process exists.

Use a synthetic deployment with a fast-starting process and deliberately delayed initialization. Observe the interval between process startup and admission into the serving endpoints. The rollout must not send traffic during that interval. Preserve the old replica until the replacement can serve, and test a recovery transition as well as initial startup.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:kubernetes-operations-admission]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Image preparation

A Docker image used by Kubernetes should include only the runtime artifacts the process needs. A successful image build does not prove the running pod has the expected entry point, working directory or file permissions. Compare the local runtime invocation with the workload's declared command before blaming the cluster.

Use a synthetic image with an explicit revision label and a small Java startup check. Record which image digest reaches the pod and verify that the same digest was tested. A rollout using an old cached artifact can make a configuration fix appear ineffective. Never place real registry credentials in the procedure.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:kubernetes-operations-image]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Rollout budget

Kubernetes rolling updates replace pods gradually rather than instantly. Readiness is required but cannot compensate for an impossible capacity budget. Check whether surge replicas can be scheduled and whether unavailable replicas remain below the declared bound. Observe old and new replicas during transition, not only after the rollout ends.

A Docker image preparation failure must be distinguished from a scheduling shortage. Exercise a synthetic rollout where one replacement cannot start. The old serving replica should remain useful within the declared budget. Retry with a corrected image only as an explicit test step; a rapid backoff loop does not create missing capacity.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:kubernetes-operations-rollout]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Dependency recovery

A Kubernetes pod may depend on Redis caching without requiring that cache for every response. Decide which routes can fall back to a durable source and which genuinely cannot serve. Making liveness depend on every optional dependency can restart healthy processes during a shared outage.

Simulate Redis unavailability and record whether the application follows its documented fallback. Keep readiness changes bounded and test the transition back after recovery. A dependency failure should not manufacture a successful response if the required source is absent, but a cache miss is not itself a missing record.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:kubernetes-operations-dependency]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Ingress route

Kubernetes traffic may pass through Nginx or another HTTP proxy before reaching a pod. A timeout at the ingress hop is not automatically a pod failure. Compare the proxy's upstream budget with the application's request deadline and identify which hop stopped waiting first.

Inspect forwarded headers only through trusted hops. A synthetic slow HTTP peer can separate an ingress deadline from readiness admission. Keep backend payloads out of traces and record a hop label plus elapsed duration. Changing the pod probe does not fix an incorrectly routed upstream or a proxy that cannot connect.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:kubernetes-operations-ingress]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Graceful shutdown

A Kubernetes rollout eventually terminates an old pod. During shutdown, stop admission of new HTTP work, allow bounded in-flight work to finish and then exit. The grace interval must cover the documented local drain, not an unbounded retry sequence. A process that keeps accepting work until it is killed hides a transition defect.

Exercise shutdown with a synthetic slow HTTP operation and distinguish completed work from canceled local observation. Log aggregate counts only. Verify that readiness withdrawal precedes the final exit and that the replacement is already admitted. Do not assume remote work was canceled because the old local process stopped.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:kubernetes-operations-shutdown]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.
