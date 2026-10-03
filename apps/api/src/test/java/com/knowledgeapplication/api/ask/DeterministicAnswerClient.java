package com.knowledgeapplication.api.ask;
import java.util.*;

/** Test-only capturing fake; never connects to any provider. */
public class DeterministicAnswerClient implements KnowledgeAnswerClient {
    public final List<Request> requests=new ArrayList<>();
    public AnswerDraft output;
    public RuntimeException failure;
    @Override public AnswerDraft answer(Request request) {
        requests.add(request);
        if(failure!=null) throw failure;
        if(output!=null) return output;
        var refs=new ArrayList<String>();
        for(var source:new tools.jackson.databind.ObjectMapper().readTree(request.referenceData())) refs.add(source.path("sourceRef").asString());
        return new AnswerDraft(List.of(new AnswerDraft.Block("Use the timeout configuration in the supplied notes.",List.copyOf(refs))));
    }
}
