# PostgreSQL performance investigations

## Plan baseline

PostgreSQL performance work starts with an observed query and a plan, not an index created by habit. EXPLAIN shows whether the planner expects a narrow lookup or a broad scan. Separate a selective lookup from an ordered listing because they can require different access paths.

Use a synthetic table with fixed rows and record the query shape before the proposed change. Compare the same predicate and ordering after an index is added. A faster warm-cache run alone does not prove that the planner changed its choice. Keep the experiment's row distribution stable.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:postgres-performance-plan]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## JSON containment

PostgreSQL JSONB containment is a different access pattern from scalar equality. A GIN index can serve operators that a scalar B-tree does not. Check that the operator class matches the expression rather than assuming every condition on a JSON payload benefits equally.

Use a synthetic payload shape and compare a matching containment predicate with a path extraction predicate. Record the exact expression in the plan. Do not infer that an index is unused merely because the query still examines heap rows; explain which part of the predicate is actually supported.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:postgres-performance-json]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Lock wait

A PostgreSQL query can spend its time waiting for a lock rather than scanning data. A new index cannot remove a blocking transaction that holds the target row. Distinguish statement duration from useful execution and inspect the lifetime of the transaction that owns the conflicting lock.

Use two synthetic connections: one begins an update and holds the transaction, while the other attempts bounded work. Release the first transaction and observe the second. A timeout must produce a controlled failure, not a hidden retry that creates an unbounded queue. Keep this separate from advisory coordination.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:postgres-performance-locks]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Connection pressure

A Java application can exhaust its PostgreSQL connection pool while the database itself remains healthy. Inspect leases and transaction boundaries before increasing the pool. Holding a connection during unrelated HTTP work can turn a slow downstream service into database starvation.

Run a synthetic Java flow that releases its database transaction before contacting an HTTP peer. Compare it with a deliberately incorrect flow that keeps the lease open. Record active leases and queueing, not private SQL parameters. Increasing a timeout changes waiting policy but does not remove the unnecessary lease.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Supporting procedure [eval:postgres-performance-pool]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Cache interaction

Redis caching can lower repeated PostgreSQL reads but does not change the durable ownership of data. A cache TTL and query plan should be evaluated separately. A stale value is not fixed by adding a database index, and a slow miss path is not fixed by extending expiration indefinitely.

Use a synthetic cached value and explicitly exercise hit, miss and expiration transitions. Keep the query behind a miss identical across runs. Describe the acceptable staleness window and whether a concurrent refresh uses a short lock. Never treat a missing Redis value as proof that the database row was deleted.

Run the experiment in a fixed sequence: prepare the synthetic peer, perform one operation, observe completion, and then restore the peer. Do not change several settings together. Record aggregate counts and phase labels rather than raw payloads. If the observation contradicts the stated boundary, inspect the actual running configuration before adding a larger budget. A second run with identical conditions should reach the same operational conclusion.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Supporting procedure [eval:postgres-performance-cache]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.

## Maintenance boundary

PostgreSQL indexing and row cleanup consume work while serving application requests. A large background operation should be bounded and observed independently from interactive queries. A build of a new index is not an excuse to change every timeout and retry setting at the same time.

Use a synthetic workload and compare an interactive EXPLAIN plan before, during and after the background work. Record whether latency is caused by a lock or by resource contention. Preserve a controlled retry budget for the Java caller and do not turn a maintenance investigation into an unbounded automatic loop.

Keep the interpretation narrow. This section describes one bounded investigation, not a universal deployment recommendation. An operator still needs to compare the observed behavior with the application's serving contract and resource limits. Document what this experiment cannot prove, especially remote cancellation or the absence of a completed effect after a response was lost. Separate a local observation from a guarantee about the whole system.

Before changing a setting, preserve a reproducible baseline using a disposable environment. Write down the expected success transition, the controlled failure transition and the observation that distinguishes them. A successful start is not sufficient: the experiment must also explain what happens when a dependency is unavailable for longer than the operation permits. Do not use a real account or production payload to make the demonstration appear realistic.

Supporting procedure [eval:postgres-performance-maintenance]: keep the bounded investigation and its final outcome explicit. Use a disposable peer and a fixed observation sequence, and preserve the declared success/failure distinction.

- Record the initial serving state.
- Perform one bounded synthetic operation.
- Verify both completion and controlled failure.
- Restore the original experiment state.
