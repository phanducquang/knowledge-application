package com.knowledgeapplication.api.knowledge.controller;

import com.knowledgeapplication.api.exception.ApiExceptionHandler;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeControllerValidationTest {

    private KnowledgeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(KnowledgeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new KnowledgeController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsWhitespaceOnlyTitleWithCleanFieldError() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "summary": null,
                                  "content": "",
                                  "visibility": "PRIVATE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.title").value("Title must not be blank"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedVisibilityWithoutReturningImplementationDetails() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Valid title",
                                  "content": "",
                                  "visibility": "NOT_A_VISIBILITY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request could not be parsed"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsTagThatIsEmptyAfterHashNormalization() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Valid title",
                                  "content": "",
                                  "visibility": "PRIVATE",
                                  "tags": ["###"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingVisibilityOnFocusedPatch() throws Exception {
        mockMvc.perform(patch("/api/knowledge/1/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.visibility").value("Visibility is required"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedVisibilityOnFocusedPatch() throws Exception {
        mockMvc.perform(patch("/api/knowledge/1/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"NOT_A_VISIBILITY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(service);
    }
}
