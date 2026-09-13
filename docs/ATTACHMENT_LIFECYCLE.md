# Attachment Lifecycle and Orphan Cleanup

This document defines the lifecycle contract for image attachments stored through the private S3-compatible object store.

## Canonical reference

Markdown remains the source of truth for attachment liveness:

```text
attachment://<UUID>
```

The object-storage URL and private `object_key` are never persisted into Markdown and are never exposed to the browser.

## Liveness invariant

An attachment object is eligible for deletion only when all of the following are true:

1. the current `Knowledge.content` does not reference its UUID;
2. no retained `knowledge_revision.content` references its UUID;
3. the attachment has remained orphaned for at least the configured grace period.

A reference in any retained revision is enough to keep the object because restoring that revision must continue to render the image.

Reference recognition is intentionally conservative: any canonical `attachment://UUID` occurrence counts as live. A false positive may retain storage longer, while a false negative could destroy recoverable content, so retention is preferred.

## Fresh uploads and autosave race

Image upload and Markdown autosave are separate requests. A newly uploaded attachment therefore begins with `orphaned_at = created_at` instead of being considered immediately referenced.

The default grace period is `PT24H`. This protects the normal sequence:

```text
upload object + metadata
    -> browser receives attachment://UUID
    -> editor inserts Markdown
    -> autosave persists Markdown later
```

When a Knowledge update persists the UUID, `orphaned_at` is cleared. If the UUID is later removed from current Markdown, `orphaned_at` is set again and a fresh grace period starts.

## Cleanup sequence

The periodic cleanup selects only candidates whose `orphaned_at` is older than the grace-period cutoff. Before deleting anything it:

1. acquires the parent Knowledge mutation lock;
2. locks the attachment row;
3. rechecks the current Markdown;
4. rechecks all retained revisions.

If a current/revision reference exists, cleanup clears `orphaned_at` and retains the object.

If no reference exists, cleanup deletes the object from S3-compatible storage first and deletes PostgreSQL metadata only after the storage call succeeds.

PostgreSQL and S3 do not share an ACID transaction. The ordering is deliberately:

```text
S3 delete
    -> metadata delete
```

If storage deletion fails, the transaction fails and metadata remains available for a later retry. If the S3 deletion succeeds but the later PostgreSQL commit fails, retrying the S3 delete is idempotent and the next cleanup run converges.

An already-missing object is therefore safe: S3 `DeleteObject` is treated idempotently and stale metadata can still be removed.

## Knowledge deletion

`knowledge_attachment.knowledge_id` still uses `ON DELETE CASCADE`, so deleting the parent would normally remove the only persisted `object_key` before storage cleanup can run.

Flyway V7 adds `knowledge_attachment_delete_queue`. Before deleting a Knowledge row, the service copies all of that note's object keys into this queue in the same PostgreSQL transaction. The parent delete can then cascade attachment metadata safely.

The periodic cleanup drains queued keys by deleting the S3 object first and removing the queue row afterwards. Failed storage deletion leaves the queue row retryable.

## Concurrency

Knowledge authoring mutations, revision restore, attachment upload, Knowledge deletion and destructive orphan cleanup use the same parent Knowledge pessimistic-lock ordering.

This prevents the dangerous race where cleanup decides an attachment is dead and deletes it immediately before a concurrent autosave successfully adds the reference back to current Markdown.

Upload also participates in the parent lock. If Knowledge deletion commits first, upload fails before writing a new object. If upload commits first, Knowledge deletion waits and then captures the uploaded object key in the deletion queue.

## Cross-note isolation

A copied attachment UUID in another note must never retain or authorize another note's object. Reference-state updates are scoped by `knowledge_id`, and read paths continue to require the exact accessible parent Knowledge item.

## Scheduler configuration

Environment variables:

```text
KNOWLEDGE_ATTACHMENT_CLEANUP_ENABLED=true
KNOWLEDGE_ATTACHMENT_ORPHAN_GRACE_PERIOD=PT24H
KNOWLEDGE_ATTACHMENT_CLEANUP_INTERVAL=PT1H
KNOWLEDGE_ATTACHMENT_CLEANUP_INITIAL_DELAY=PT5M
KNOWLEDGE_ATTACHMENT_CLEANUP_BATCH_SIZE=100
```

The scheduler intentionally runs at low frequency; this is garbage collection, not a real-time deletion protocol.

Spring scheduling is enabled by the backend configuration and the cleanup bean can be disabled with `KNOWLEDGE_ATTACHMENT_CLEANUP_ENABLED=false`. Invalid negative grace periods, non-positive batch sizes or non-positive scheduling intervals fail application startup. A zero initial delay is valid; a negative initial delay is rejected.

The current deployment model does not elect a single cleanup leader. If multiple API replicas run, they may select the same candidate. Parent/attachment database locks serialize destructive orphan work, S3 deletion is idempotent, and queue deletion converges, so duplicate attempts are safe but may perform redundant storage calls. Distributed scheduler coordination can be added only if future scale makes that operational cost meaningful.

## Revision retention interaction

Revision pruning is not implemented yet. If revision retention/pruning is added later, that feature must re-evaluate attachment liveness after removing the last revision that references an attachment. Revision pruning must not bypass this lifecycle contract.

## Historical limitation

The V7 deletion queue prevents new unknown objects specifically when deleting a Knowledge item: it preserves each known object key before attachment metadata cascades away.

It does not eliminate every S3/PostgreSQL gap. Upload still stores the object before persisting attachment metadata. Ordinary persistence failures trigger best-effort object deletion, but if the JVM/process dies after a successful S3 put and before the database row is persisted, that object has no metadata for DB-driven cleanup to discover. Objects orphaned before V7 and objects from this rare upload crash window require a future bucket inventory/reconciliation job.

## Verification

The API test suite includes PostgreSQL + MinIO Testcontainers coverage for:

- fresh-upload grace protection;
- deletion after the grace period;
- revision-only reference retention and restore;
- Knowledge deletion queueing/draining;
- convergence when an object is already missing;
- object-before-metadata deletion ordering;
- storage failure leaving metadata retryable;
- owner/parent-scoped attachment behavior.

Local real-stack fault-injection verification is performed separately when validating the milestone end-to-end.
