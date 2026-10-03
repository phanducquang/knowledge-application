package com.knowledgeapplication.api.ask;

import com.knowledgeapplication.api.knowledge.embedding.KnowledgeEmbeddingRepository.RagChunk;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Total budget includes JSON escaping/metadata; select whole chunks where possible, trim the final chunk safely. */
public final class AskContext {
    private AskContext() {}
    private record Reference(String sourceRef, String title, String slug, int chunkIndex, String text) {}
    public record Selected(RagChunk chunk, String includedText) {}
    public record Assembled(String data, Map<String,Selected> sourceMap) {
        @Override public String toString() { return "AskContext[redacted]"; }
    }
    public static Assembled assemble(List<RagChunk> chunks, AskProperties props, ObjectMapper mapper) {
        List<Reference> refs=new ArrayList<>();
        Map<String,Selected> sourceMap=new LinkedHashMap<>();
        Set<Long> notes=new HashSet<>();
        Map<Long,Integer> counts=new HashMap<>();
        for (var chunk : chunks) {
            if (refs.size()>=props.maxChunks()) break;
            if (chunk.chunkText().isBlank() || counts.getOrDefault(chunk.id(),0)>=props.maxChunksPerKnowledge()) continue;
            if (!notes.contains(chunk.id()) && notes.size()>=props.maxSources()) continue;
            if (sourceMap.values().stream().anyMatch(s -> s.chunk().id()==chunk.id() && s.chunk().chunkIndex()==chunk.chunkIndex())) continue;
            String source="S" + (refs.size()+1);
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
            notes.add(chunk.id()); sourceMap.put(source,new Selected(chunk,text));
            if (truncated) break;
        }
        return new Assembled(mapper.writeValueAsString(refs),Collections.unmodifiableMap(sourceMap));
    }
    private static String prefix(String text,int length) {
        if (length>0 && length<text.length() && Character.isHighSurrogate(text.charAt(length-1)) && Character.isLowSurrogate(text.charAt(length))) length--;
        return text.substring(0,length);
    }
}
