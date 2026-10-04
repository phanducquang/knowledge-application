# Redis cache operations strategy

## Expiration choice

Redis cache TTL expresses an acceptable staleness window, not a promise that every source update appears immediately. Choose expiration independently from deletion of durable records. An expired cache entry must be distinguishable from a source that no longer exists.

Use synthetic values whose revisions can be observed without containing user data. Exercise a hit, an expired entry and a durable-source miss. Record which path repopulates the cache and which returns a missing record. Never turn a timeout while loading the source into a fabricated successful value.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:redis-strategy-ttl]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Refresh ownership

A Redis cache stampede happens when concurrent misses trigger the same expensive source work. A short lock can assign refresh ownership to one caller, while others follow an explicit fallback. The lock is a coordination tool rather than a durable queue.

Use synthetic refresh labels and verify that a caller releases only its own lock. Exercise expiration of the lock while work is delayed. A late owner must not delete a newer owner's guard. Keep waiting bounded and record concurrency counts instead of private cache contents.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:redis-strategy-refresh]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Durable fallback

A Redis miss can load from PostgreSQL, but the source query still needs a suitable plan and bounded transaction. Caching does not justify leaving an inefficient lookup unmeasured. A shared miss path can create database pressure precisely when the cache is least available.

Use one synthetic query shape and an EXPLAIN baseline. Compare one refresh owner with several uncoordinated callers. Separate index behavior from lock waiting in the database. A timeout must leave the old value or a controlled failure according to policy, not overwrite the cache with a false success.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:redis-strategy-source]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Deployment recovery

Redis configuration changes may accompany Docker or Kubernetes replacement of the application. Check the configured service name from the running container, not from the developer host. Container localhost does not refer to the shared cache service.

Use a synthetic deployment and test cache reachability before admission to traffic. Decide whether Redis is required for readiness or merely an optional optimization. A failed readiness probe and an unavailable cache are related only if the application's declared serving contract makes them so.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:redis-strategy-deployment]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Request budget

A Java HTTP caller that waits for Redis refresh can exceed its deadline even if the backend source call has a response timeout. Keep the caller budget, refresh wait and any safe retry distinct. A cache fallback is useful only if it completes within the remaining time.

Use a synthetic slow WebClient source and a short cache refresh guard. Observe one HTTP attempt with retry disabled before enabling a bounded repeat. Record whether the deadline ends local observation while source work continues; do not claim that cancellation guarantees no remote effect.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:redis-strategy-http]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Outage recovery

During a Redis outage, a background retry loop can consume capacity without producing useful cache values. Stop an exhausted refresh attempt and leave pending work for a later bounded cycle. Protect the durable source from a sudden flood of simultaneous callers.

Use synthetic failure transitions and count actual attempts after the operation ends. Apply a bounded concurrency policy and keep retry ownership explicit. Recovery must not reset unrelated application quotas or treat an unavailable cache as permission to bypass the durable source's access checks.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:redis-strategy-outage]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.
