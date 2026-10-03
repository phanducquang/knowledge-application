package com.knowledgeapplication.api.ask;

import com.knowledgeapplication.api.knowledge.embedding.KnowledgeEmbeddingRepository.RagChunk;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Total budget includes JSON escaping/metadata; select whole chunks where possible, trim the final chunk safely. */
public final class AskContext {
    private AskContext() {}
    private record Reference(String source, String title, String slug, int chunkIndex, String text) {}
    public record Assembled(String data, List<AskResponse.Source> sources) {}
    public static Assembled assemble(List<RagChunk> chunks, AskProperties props, ObjectMapper mapper) {
        List<Reference> refs=new ArrayList<>();
        Map<Long, AskResponse.Source> sources=new LinkedHashMap<>();
        Map<Long,Integer> counts=new HashMap<>();
        for (var chunk : chunks) {
            if (refs.size()>=props.maxChunks()) break;
            if (chunk.chunkText().isBlank() || counts.getOrDefault(chunk.id(),0)>=props.maxChunksPerKnowledge()) continue;
            if (!sources.containsKey(chunk.id()) && sources.size()>=props.maxSources()) continue;
            String source="note-" + chunk.id();
            String text=chunk.chunkText();
            var ref=new Reference(source,chunk.title(),chunk.slug(),chunk.chunkIndex(),text);
            var candidate=new ArrayList<>(refs); candidate.add(ref);
            boolean truncated=false;
            if (mapper.writeValueAsString(candidate).length()>props.maxContextChars()) {
                // Binary search on serialized size, not raw text length (quotes/newlines may expand).
                int low=0, high=text.length();
                while (low<high) {
                    int mid=(low+high+1)/2;
                    candidate.set(candidate.size()-1,new Reference(source,chunk.title(),chunk.slug(),chunk.chunkIndex(),prefix(text,mid)));
                    if (mapper.writeValueAsString(candidate).length()<=props.maxContextChars()) low=mid; else high=mid-1;
                }
                text=prefix(text,low);
                if (text.isBlank()) break;
                ref=new Reference(source,chunk.title(),chunk.slug(),chunk.chunkIndex(),text); truncated=true;
            }
            refs.add(ref); counts.merge(chunk.id(),1,Integer::sum);
            sources.putIfAbsent(chunk.id(),new AskResponse.Source(chunk.id(),chunk.title(),chunk.slug(),excerpt(text)));
            if (truncated) break;
        }
        return new Assembled(mapper.writeValueAsString(refs),List.copyOf(sources.values()));
    }
    private static String prefix(String text,int length) {
        if (length>0 && length<text.length() && Character.isHighSurrogate(text.charAt(length-1)) && Character.isLowSurrogate(text.charAt(length))) length--;
        return text.substring(0,length);
    }
    private static String excerpt(String text) { return text.length()<=600 ? text : prefix(text,599)+"…"; }
}
