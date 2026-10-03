package com.knowledgeapplication.api.ask;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.embedding.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static com.knowledgeapplication.api.ask.AskUnavailableException.Reason.*;

@Service
public class AskService {
    private final CurrentOwner owner;
    private final AskProperties properties;
    private final EmbeddingProperties embeddings;
    private final EmbeddingStrategy strategy;
    private final ObjectProvider<EmbeddingClient> embeddingClients;
    private final ObjectProvider<KnowledgeAnswerClient> answerClients;
    private final KnowledgeEmbeddingRepository repository;
    private final ObjectMapper mapper;
    public AskService(CurrentOwner owner, AskProperties properties, EmbeddingProperties embeddings, EmbeddingStrategy strategy,
            ObjectProvider<EmbeddingClient> embeddingClients, ObjectProvider<KnowledgeAnswerClient> answerClients,
            KnowledgeEmbeddingRepository repository, ObjectMapper mapper) {
        this.owner=owner; this.properties=properties; this.embeddings=embeddings; this.strategy=strategy;
        this.embeddingClients=embeddingClients; this.answerClients=answerClients; this.repository=repository; this.mapper=mapper;
    }
    // Intentionally NOT transactional: neither provider call leases a DB transaction/connection.
    public AskResponse ask(String question) {
        if (question==null || question.isBlank() || question.length()>2000) throw new IllegalArgumentException("Question is invalid");
        var ownerId=owner.id();
        if (!properties.enabled() || answerClients.getIfAvailable()==null) throw new AskUnavailableException(DISABLED);
        if (!embeddings.enabled() || embeddingClients.getIfAvailable()==null) throw new AskUnavailableException(RETRIEVAL);
        question=question.trim();
        float[] vector;
        try {
            var vectors=embeddingClients.getObject().embed(List.of(question));
            EmbeddingVectors.validate(vectors,1,embeddings.dimensions()); vector=vectors.get(0);
        } catch (RuntimeException ex) { throw new AskUnavailableException(RETRIEVAL); }
        AskContext.Assembled context;
        try {
            context=AskContext.assemble(repository.findRagChunks(ownerId,strategy,vector,properties.maxChunks(),properties.maxChunksPerKnowledge()),properties,mapper);
        } catch (RuntimeException ex) { throw new AskUnavailableException(RETRIEVAL); }
        if (context.sourceMap().isEmpty()) return new AskResponse(AskResponse.Status.NO_CONTEXT,null,List.of());
        try {
            var draft=answerClients.getObject().answer(new KnowledgeAnswerClient.Request(question,context.data()));
            return AnswerCitations.validate(draft,context);
        } catch (RuntimeException ex) { throw new AskUnavailableException(GENERATION); }
    }
}
