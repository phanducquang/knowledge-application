package com.knowledgeapplication.api.ask;

public class AskUnavailableException extends RuntimeException {
    public enum Reason { DISABLED, RETRIEVAL, GENERATION }
    private final Reason reason;
    public AskUnavailableException(Reason reason) {
        super(switch(reason) {
            case DISABLED -> "Ask My Knowledge is disabled";
            case RETRIEVAL -> "Knowledge retrieval is temporarily unavailable";
            case GENERATION -> "Answer generation is temporarily unavailable";
        });
        this.reason=reason;
    }
    public String code() { return switch(reason) {
        case DISABLED -> "ASK_DISABLED"; case RETRIEVAL -> "ASK_RETRIEVAL_UNAVAILABLE"; case GENERATION -> "ASK_UNAVAILABLE";
    }; }
}
