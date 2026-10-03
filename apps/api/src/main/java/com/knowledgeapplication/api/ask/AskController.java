package com.knowledgeapplication.api.ask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ask")
public class AskController {
    private final AskService service;
    public AskController(AskService service) { this.service=service; }
    public record AskRequest(@NotBlank(message="Question must not be blank") @Size(max=2000,message="Question must be at most 2000 characters") String question) {}
    @PostMapping
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store, max-age=0").body(service.ask(request.question()));
    }
}
