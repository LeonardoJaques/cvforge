package com.cvforge.controller;

import com.cvforge.application.port.in.JobMatchUseCases;
import com.cvforge.application.port.in.JobComparisonUseCases;
import com.cvforge.application.port.in.ComparisonResumePdfUseCases;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.model.JobComparisonData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private static final Logger log = LoggerFactory.getLogger(JobMatchController.class);
    private final JobMatchUseCases jobMatch;
    private final JobComparisonUseCases comparisons;
    private final ObjectMapper mapper;
    private final ComparisonResumePdfUseCases comparisonResumePdf;
    public JobMatchController(JobMatchUseCases jobMatch, JobComparisonUseCases comparisons, ObjectMapper mapper, ComparisonResumePdfUseCases comparisonResumePdf) {
        this.jobMatch=jobMatch; this.comparisons=comparisons; this.mapper=mapper; this.comparisonResumePdf=comparisonResumePdf;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody JobMatchUseCases.Request request,
                                     @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        try { return ResponseEntity.ok(jobMatch.analyze(request, userId)); }
        catch (IllegalArgumentException e) {
            log.warn("CVFORGE_JOB_ANALYSIS_REJECTED reason={}", safeLogMessage(e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        catch (IllegalStateException e) {
            log.error("CVFORGE_JOB_ANALYSIS_FAILED reason={}", safeLogMessage(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
        catch (Exception e) {
            log.error("CVFORGE_JOB_ANALYSIS_UNEXPECTED exception_type={}", e.getClass().getName());
            return ResponseEntity.internalServerError().body(Map.of("message", "Não foi possível analisar esta vaga."));
        }
    }

    private String safeLogMessage(String message) {
        if (message == null || message.isBlank()) return "unknown";
        String singleLine = message.replaceAll("[\\r\\n\\t]", " ");
        return singleLine.substring(0, Math.min(240, singleLine.length()));
    }

    @PostMapping("/comparisons")
    public ResponseEntity<?> save(@RequestBody SaveRequest request,
                                  @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        try {
            JobComparisonData saved = comparisons.save(userId, request==null?null:request.resumeId(),
                    request==null?null:request.profileLabel(), request==null?null:request.jobText(),
                    request==null?null:request.result(), request==null?null:request.language());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Não foi possível salvar a comparação."));
        }
    }

    @GetMapping("/comparisons")
    public List<ComparisonResponse> list(@RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        return comparisons.list(userId).stream().map(this::toResponse).toList();
    }

    @PostMapping(value="/comparisons/{id}/resume-preview", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewComparisonResume(@PathVariable Long id, @RequestHeader(value="Remote-User", required=false, defaultValue="anonimo") String userId) {
      try { return ResponseEntity.ok(comparisonResumePdf.preview(id,userId)); }
      catch(IllegalArgumentException e) { return jsonError(HttpStatus.BAD_REQUEST,e.getMessage()); }
      catch(IllegalStateException e) {
        log.warn("CVFORGE_COMPARISON_RESUME_PREVIEW_REJECTED reason={}",safeLogMessage(e.getMessage()));
        return jsonError(HttpStatus.BAD_GATEWAY,e.getMessage());
      } catch(Exception e) {
        log.error("CVFORGE_COMPARISON_RESUME_PREVIEW_UNEXPECTED exception_type={}",e.getClass().getName());
        return jsonError(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível gerar a prévia do currículo.");
      }
    }

    @PostMapping(value="/comparisons/{id}/resume.pdf", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> generateComparisonResumePdf(@PathVariable Long id, @RequestBody(required=false) SavePreviewRequest request,
            @RequestHeader(value="Remote-User", required=false, defaultValue="anonimo") String userId) {
      try {
        ComparisonResumePdfUseCases.PdfDownload file=comparisonResumePdf.savePreview(id,userId,request==null?null:request.token());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=" + file.filename()).contentType(MediaType.APPLICATION_PDF).body(file.bytes());
      } catch(IllegalArgumentException e) { return jsonError(HttpStatus.BAD_REQUEST,e.getMessage()); }
        catch(IllegalStateException e) {
          log.warn("CVFORGE_COMPARISON_RESUME_SAVE_REJECTED reason={}",safeLogMessage(e.getMessage()));
          return jsonError(HttpStatus.BAD_GATEWAY,e.getMessage());
        } catch(Exception e) {
          log.error("CVFORGE_COMPARISON_RESUME_SAVE_UNEXPECTED exception_type={}",e.getClass().getName());
          return jsonError(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível salvar o currículo em PDF.");
        }
    }

    private ResponseEntity<Map<String, String>> jsonError(HttpStatus status, String message) {
        String safeMessage = message == null || message.isBlank() ? "Não foi possível gerar o currículo em PDF." : message;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of("message", safeMessage));
    }

    @DeleteMapping("/comparisons/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        return comparisons.delete(id, userId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private ComparisonResponse toResponse(JobComparisonData item) {
        try {
            return new ComparisonResponse(item.getId(), item.getResumeId(), item.getProfileLabel(), item.getJobText(), mapper.readTree(item.getResultJson()), item.getLanguage(), item.getCreatedAt(), item.getTailoredResumeId());
        } catch (Exception e) { throw new IllegalStateException("Saved comparison JSON is invalid.", e); }
    }

    public record SaveRequest(Long resumeId, String profileLabel, String jobText, JsonNode result, String language) { }
    public record SavePreviewRequest(String token) { }
    public record ComparisonResponse(Long id, Long resumeId, String profileLabel, String jobText, JsonNode result, String language, java.time.LocalDateTime createdAt, Long tailoredResumeId) { }
}
