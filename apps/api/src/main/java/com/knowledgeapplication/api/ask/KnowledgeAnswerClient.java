package com.knowledgeapplication.api.ask;

/** Provider-neutral single-turn boundary. Context is serialized untrusted reference DATA. */
public interface KnowledgeAnswerClient {
    record Request(String question, String referenceData) {
        @Override public String toString() { return "AnswerRequest[redacted]"; }
    }
    String answer(Request request);
}
