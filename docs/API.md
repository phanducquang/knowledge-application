# API Direction

No API implementation exists yet. This file records only the agreed boundary and naming direction.

## API ownership

Spring Boot owns domain rules, persistence and authorization.

Next.js must not be the authoritative layer for knowledge visibility or ownership checks.

## Expected resource areas

Future API groups are expected around concepts such as:

```text
/auth
/knowledge
/tags
/collections
/search
/sharing
/attachments
/revisions
```

Exact endpoints and request/response contracts are intentionally deferred until feature implementation.

## Visibility

The API must eventually model at least:

```text
PRIVATE
UNLISTED
PUBLIC
```

Changing visibility must be an explicit backend operation.

## Public access direction

Public knowledge should use a stable slug-oriented URL.

Unlisted knowledge should use a non-guessable token/link.

The API must not expose private content merely because a caller knows an internal numeric ID.
