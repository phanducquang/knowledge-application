package com.knowledgeapplication.api.ask;

import com.knowledgeapplication.api.knowledge.embedding.KnowledgeEmbeddingRepository.RagChunk;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.knowledgeapplication.api.ask.AskServiceTest.askProps;

class AnswerCitationsTest {
    AskContext.Assembled context=AskContext.assemble(List.of(
            new RagChunk(12,"Backend title","backend-title",2,"Exact chunk two."),
            new RagChunk(12,"Backend title","backend-title",5,"Exact chunk five.")),askProps(true),new ObjectMapper());
    AnswerDraft draft(String text,String... refs) { return new AnswerDraft(List.of(new AnswerDraft.Block(text,List.of(refs)))); }
    @Test void referencesArePerChunkOpaqueDeterministicAndRequestLocal() {
        assertThat(context.sourceMap().keySet()).containsExactly("S1","S2");
        assertThat(context.sourceMap().get("S2").chunk().chunkIndex()).isEqualTo(5);
        assertThat(context.data()).doesNotContain("note-12","knowledgeId");
        assertThat(new ObjectMapper().readTree(context.data()).get(0).path("sourceRef").asString()).isEqualTo("S1");
    }
    @Test void numberingUsesFirstAnswerAppearanceAndReusesTheExactChunkAcrossBlocks() {
        var response=AnswerCitations.validate(new AnswerDraft(List.of(new AnswerDraft.Block("First",List.of("S2","S1")),
                new AnswerDraft.Block("Again",List.of("S2")))),context);
        assertThat(response.answer().blocks()).extracting(AskResponse.Block::citationIds).containsExactly(List.of("C1","C2"),List.of("C1"));
        assertThat(response.citations()).extracting(AskResponse.Citation::id).containsExactly("C1","C2");
        assertThat(response.citations()).extracting(AskResponse.Citation::chunkIndex).containsExactly(5,2);
        assertThat(response.citations()).extracting(AskResponse.Citation::evidence).containsExactly("Exact chunk five.","Exact chunk two.");
        assertThat(response.citations().get(0).source()).isEqualTo(new AskResponse.Source(12,"Backend title","backend-title"));
    }
    @Test void rejectsEveryInvalidDraftAtomicallyIncludingKnownPlusUnknown() {
        var invalid=new ArrayList<AnswerDraft>(Arrays.asList(null,new AnswerDraft(null),new AnswerDraft(List.of()),
                draft(" ","S1"),draft("x"),draft("x",""),draft("x","note-12"),draft("x","S999"),
                draft("x","S1","S999"),draft("x","S1","S1"),draft("x".repeat(8193),"S1"),
                new AnswerDraft(Collections.nCopies(25,new AnswerDraft.Block("x",List.of("S1")))),
                new AnswerDraft(Collections.nCopies(9,new AnswerDraft.Block("x".repeat(8192),List.of("S1"))))));
        for(var draft:invalid) assertThatThrownBy(()->AnswerCitations.validate(draft,context))
                .isInstanceOf(AskUnavailableException.class).hasNoCause();
    }
    @Test void referenceComplexityCannotGrowIntoThousandsOfOccurrences() {
        var chunks=new ArrayList<RagChunk>(); var refs=new ArrayList<String>();
        for(int i=0;i<8;i++) { chunks.add(new RagChunk(i+1,"Title","title",0,"text")); refs.add("S"+(i+1)); }
        var props=askProps(true); props=new AskProperties(true,props.baseUrl(),props.model(),props.connectTimeout(),props.readTimeout(),1200,24000,8,2,8);
        var ctx=AskContext.assemble(chunks,props,new ObjectMapper());
        var draft=new AnswerDraft(Collections.nCopies(17,new AnswerDraft.Block("x",refs)));
        assertThatThrownBy(()->AnswerCitations.validate(draft,ctx)).isInstanceOf(AskUnavailableException.class);
    }
    @Test void evidenceIsExactCompactUnicodeSafeAndOnlyFromIncludedContext() {
        String text="a".repeat(399)+"😀"+"tail".repeat(300);
        var chunk=new RagChunk(1,"Unicode","unicode",7,text);
        var ctx=AskContext.assemble(List.of(chunk),askProps(true),new ObjectMapper());
        String evidence=AnswerCitations.validate(draft("Answer","S1"),ctx).citations().get(0).evidence();
        assertThat(evidence).hasSize(399); assertThat(text).contains(evidence);
        assertThat(Character.isHighSurrogate(evidence.charAt(evidence.length()-1))).isFalse();
        var forged=new AskContext.Assembled("[]",Map.of("S1",new AskContext.Selected(chunk,"Fabricated evidence")));
        assertThatThrownBy(()->AnswerCitations.validate(draft("Answer","S1"),forged)).isInstanceOf(AskUnavailableException.class);
    }
    @Test void truncatedContextCannotCiteUnsentChunkSuffixOrUnusedSource() {
        var props=askProps(true); props=new AskProperties(true,props.baseUrl(),props.model(),props.connectTimeout(),props.readTimeout(),1200,1024,8,2,6);
        var ctx=AskContext.assemble(List.of(new RagChunk(1,"Title","title",0,"prefix "+"x".repeat(4000)+"UNSENT SUFFIX"),
                new RagChunk(2,"Other","other",0,"Never sent")),props,new ObjectMapper());
        assertThat(ctx.sourceMap().keySet()).containsExactly("S1");
        var response=AnswerCitations.validate(draft("Answer","S1"),ctx);
        assertThat(response.citations().get(0).evidence()).hasSizeLessThanOrEqualTo(400).doesNotContain("UNSENT SUFFIX");
        assertThat(ctx.sourceMap().get("S1").includedText()).contains(response.citations().get(0).evidence());
        assertThatThrownBy(()->AnswerCitations.validate(draft("Answer","S2"),ctx)).isInstanceOf(AskUnavailableException.class);
    }
}
