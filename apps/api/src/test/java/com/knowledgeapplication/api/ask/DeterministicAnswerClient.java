package com.knowledgeapplication.api.ask;
import java.util.*;

/** Test-only capturing fake; never connects to any provider. */
public class DeterministicAnswerClient implements KnowledgeAnswerClient {
    public final List<Request> requests=new ArrayList<>();
    public String output="Use the timeout configuration in the supplied notes.";
    public RuntimeException failure;
    @Override public String answer(Request request) {
        requests.add(request);
        if(failure!=null) throw failure;
        return output;
    }
}
