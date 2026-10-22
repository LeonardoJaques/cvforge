package com.cvforge.controller;

import com.cvforge.service.JobMatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cvforge.entity.JobComparison;
import com.cvforge.repository.JobComparisonRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobMatchController {
    private final JobMatchService jobMatch;
    private final JobComparisonRepository comparisons;
    private final ObjectMapper mapper;
    public JobMatchController(JobMatchService jobMatch, JobComparisonRepository comparisons, ObjectMapper mapper) {
        this.jobMatch = jobMatch; this.comparisons = comparisons; this.mapper = mapper;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody JobMatchService.Request request,
                                     @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        try { return ResponseEntity.ok(jobMatch.analyze(request, userId)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/comparisons")
    public ResponseEntity<?> save(@RequestBody SaveRequest request,
                                  @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        if (request == null || request.result() == null || !request.result().isObject()
                || request.jobText() == null || request.jobText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A análise e a descrição da vaga são obrigatórias."));
        }
        if (request.jobText().length() > 25_000) return ResponseEntity.badRequest().body(Map.of("message", "A descrição da vaga excede o limite permitido."));
        try {
            String language = "en".equalsIgnoreCase(request.language()) ? "en" : "pt";
            String label = request.profileLabel() == null || request.profileLabel().isBlank() ? "Perfil" : request.profileLabel().trim();
            JobComparison saved = comparisons.save(new JobComparison(userId, request.resumeId(), label.substring(0, Math.min(100, label.length())), request.jobText().trim(), mapper.writeValueAsString(request.result()), language));
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Não foi possível salvar a comparação."));
        }
    }

    @GetMapping("/comparisons")
    public List<ComparisonResponse> list(@RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        return comparisons.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/comparisons/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        return comparisons.findByIdAndUserId(id, userId).map(item -> {
            comparisons.delete(item); return ResponseEntity.noContent().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ComparisonResponse toResponse(JobComparison item) {
        try {
            return new ComparisonResponse(item.getId(), item.getResumeId(), item.getProfileLabel(), item.getJobText(), mapper.readTree(item.getResultJson()), item.getLanguage(), item.getCreatedAt());
        } catch (Exception e) { throw new IllegalStateException("Saved comparison JSON is invalid.", e); }
    }

    public record SaveRequest(Long resumeId, String profileLabel, String jobText, JsonNode result, String language) { }
    public record ComparisonResponse(Long id, Long resumeId, String profileLabel, String jobText, JsonNode result, String language, java.time.LocalDateTime createdAt) { }
}
