package com.knowledgeapplication.api.ask;
import java.util.List;

public record AskResponse(Status status, Answer answer, List<Citation> citations) {
    public enum Status { ANSWERED, NO_CONTEXT }
    public record Answer(List<Block> blocks) {}
    public record Block(String markdown, List<String> citationIds) {}
    public record Source(long id, String title, String slug) {}
    public record Citation(String id, Source source, int chunkIndex, String evidence) {}
}
