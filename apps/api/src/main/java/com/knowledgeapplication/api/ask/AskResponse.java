package com.knowledgeapplication.api.ask;
import java.util.List;

public record AskResponse(Status status, String answer, List<Source> sources) {
    public enum Status { ANSWERED, NO_CONTEXT }
    public record Source(long id, String title, String slug, String excerpt) {}
}
