package com.knowledgeapplication.api.retrieval.eval;

/** Only allowlisted local safety outcomes may be exposed; provider/config exception text is never copied. */
final class LiveEvaluationFailure extends IllegalStateException {
    enum Kind {
        RUN_BUDGET("Synthetic evaluation request/token budget exceeded; stopped before provider call"),
        DAILY_QUOTA("Configured daily quota exhausted; no daily waiting/retry"),
        MINUTE_BATCH("A single batch exceeds configured minute token budget"),
        WAIT_EXHAUSTED("Bounded local quota waiting exhausted"),
        INTERRUPTED("Live evaluation interrupted");
        final String message;
        Kind(String message) { this.message=message; }
    }
    LiveEvaluationFailure(Kind kind) { super(kind.message); }
}
