package com.cvforge.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cvforge.repository.JobComparisonRepository;
import com.cvforge.service.ComparisonResumePdfService;
import com.cvforge.service.JobMatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMatchControllerTest {

    @Mock private JobMatchService jobMatch;
    @Mock private JobComparisonRepository comparisons;
    @Mock private ObjectMapper mapper;
    @Mock private ComparisonResumePdfService comparisonResumePdf;

    @Test
    void logsOpenRouterFailureWithoutLoggingJobOrResumeText() {
        JobMatchController controller = controller();
        when(jobMatch.analyze(any(JobMatchService.Request.class), eq("test-user")))
                .thenThrow(new IllegalStateException("O OpenRouter não concluiu a solicitação (HTTP 503)."));

        Logger logger = (Logger) LoggerFactory.getLogger(JobMatchController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ResponseEntity<?> response;
        try {
            response = controller.analyze(new JobMatchService.Request(null, "PROFILE_PRIVATE", "JOB_PRIVATE", "pt"), "test-user");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("CVFORGE_JOB_ANALYSIS_FAILED")
                        && event.getFormattedMessage().contains("HTTP 503")));
        assertTrue(appender.list.stream().noneMatch(event ->
                event.getFormattedMessage().contains("PROFILE_PRIVATE")
                        || event.getFormattedMessage().contains("JOB_PRIVATE")));
    }

    @Test
    void logsUnexpectedAnalysisFailureWithoutInputData() {
        JobMatchController controller = controller();
        when(jobMatch.analyze(any(JobMatchService.Request.class), eq("test-user")))
                .thenThrow(new NullPointerException("private test details"));

        Logger logger = (Logger) LoggerFactory.getLogger(JobMatchController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ResponseEntity<?> response;
        try {
            response = controller.analyze(new JobMatchService.Request(null, "PROFILE_PRIVATE", "JOB_PRIVATE", "pt"), "test-user");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("CVFORGE_JOB_ANALYSIS_UNEXPECTED")
                        && event.getFormattedMessage().contains("NullPointerException")));
        assertTrue(appender.list.stream().noneMatch(event ->
                event.getFormattedMessage().contains("PROFILE_PRIVATE")
                        || event.getFormattedMessage().contains("JOB_PRIVATE")
                        || event.getFormattedMessage().contains("private test details")));
    }

    @Test
    void returnsPdfOnSuccessfulComparisonResumeGeneration() {
        JobMatchController controller = controller();
        byte[] pdf = new byte[]{37, 80, 68, 70};
        when(comparisonResumePdf.savePreview(eq(8L), eq("test-user"), eq("draft-token")))
                .thenReturn(new ComparisonResumePdfService.PdfDownload(pdf, "curriculo_acme.pdf"));

        ResponseEntity<?> response = controller.generateComparisonResumePdf(8L, new JobMatchController.SavePreviewRequest("draft-token"), "test-user");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("attachment; filename=curriculo_acme.pdf", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(pdf, (byte[]) response.getBody());
    }

    @Test
    void returnsJsonErrorWhenDeepSeekGenerationFails() {
        JobMatchController controller = controller();
        when(comparisonResumePdf.savePreview(eq(8L), eq("test-user"), eq("draft-token")))
                .thenThrow(new IllegalStateException("A IA retornou um currículo em formato inválido. Tente novamente."));

        ResponseEntity<?> response = controller.generateComparisonResumePdf(8L, new JobMatchController.SavePreviewRequest("draft-token"), "test-user");

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertTrue(response.getBody().toString().contains("formato inválido"));
    }

    private JobMatchController controller() {
        return new JobMatchController(jobMatch, comparisons, mapper, comparisonResumePdf);
    }
}
