package com.cvforge.service;

import com.cvforge.entity.JobComparison;
import com.cvforge.entity.Resume;
import com.cvforge.repository.JobComparisonRepository;
import com.cvforge.repository.ResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComparisonResumePdfServiceTest {
    @Mock private JobComparisonRepository comparisons;
    @Mock private ResumeRepository resumes;
    @Mock private OpenRouterClient ai;
    @Mock private PdfGenerationService pdf;
    private final ObjectMapper mapper = new ObjectMapper();
    private ComparisonResumePdfService service;

    @BeforeEach
    void setUp() {
        service = new ComparisonResumePdfService(comparisons, resumes, ai, pdf, mapper, "deepseek/deepseek-chat");
    }

    @Test
    void previewsMarkdownWrappedDeepSeekResumeWithoutSavingUntilConfirmation() throws Exception {
        JobComparison comparison = comparison();
        Resume original = new Resume("user-1", "Original", """
                {"name":"Ana Silva","headline":"Engenheira","email":"ana.private@example.com","phone":"555-1234","location":"São Paulo","summary":"Resumo original","experience":[{"role":"Engenheira","company":"Origem Ltda","period":"2020-2024","description":"Entrega de sistemas"}],"education":[{"course":"Sistemas","institution":"Universidade","period":"2016-2020"}],"projects":[{"name":"Projeto A","description":"Projeto original","link":"https://example.com"}],"skills":["Java","SQL"],"links":["https://example.com"]}
                """, "ATS", "CVForge");
        original.setId(4L);
        when(comparisons.findByIdAndUserId(8L, "user-1")).thenReturn(Optional.of(comparison));
        when(resumes.findByIdAndUserId(4L, "user-1")).thenReturn(Optional.of(original));
        when(ai.complete(anyString(), anyString(), eq(7000), eq("deepseek/deepseek-chat"))).thenReturn("""
                ```json
                {"language":"pt","headline":"Engenheira de software","summary":"Resumo direcionado à vaga","experienceDescriptions":["Entregou sistemas alinhados às necessidades do negócio"],"projectDescriptions":["Projeto A aplicado a dados"],"skills":["Java","Python"]}
                ```
                """);
        when(resumes.save(any(Resume.class))).thenAnswer(invocation -> {
            Resume saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        when(comparisons.save(any(JobComparison.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pdf.generatePdfFromJson(anyString(), eq("ATS"))).thenReturn(new byte[]{37, 80, 68, 70});

        var preview = service.preview(8L, "user-1");

        assertEquals("original_acme_corp.pdf", preview.filename());
        assertFalse(preview.token().isBlank());
        assertEquals("Ana Silva", preview.data().path("name").asText());
        assertTrue(preview.changes().stream().anyMatch(change -> change.section().equals("summary") && change.before().equals("Resumo original") && change.after().equals("Resumo direcionado à vaga")));
        verify(resumes, never()).save(any());
        verify(comparisons, never()).save(any());
        var result = service.savePreview(8L, "user-1", preview.token());
        assertEquals("original_acme_corp.pdf", result.filename());
        assertArrayEquals(new byte[]{37, 80, 68, 70}, result.bytes());
        assertEquals(42L, comparison.getTailoredResumeId());
        ArgumentCaptor<Resume> savedCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumes).save(savedCaptor.capture());
        Resume saved = savedCaptor.getValue();
        assertEquals("original_acme_corp", saved.getTitle());
        JsonNode data = mapper.readTree(saved.getJsonData());
        assertEquals("Ana Silva", data.path("name").asText());
        assertEquals("ana.private@example.com", data.path("email").asText());
        assertEquals("pt", data.path("_pdfLanguage").asText());
        assertEquals("Resumo direcionado à vaga", data.path("summary").asText());
        assertEquals("Entregou sistemas alinhados às necessidades do negócio", data.path("experience").get(0).path("description").asText());
        assertEquals("Projeto A aplicado a dados", data.path("projects").get(0).path("description").asText());
        assertEquals(2, data.path("skills").size());
        assertEquals("Java", data.path("skills").get(0).asText());
        assertEquals("SQL", data.path("skills").get(1).asText());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(ai).complete(systemCaptor.capture(), promptCaptor.capture(), eq(7000), eq("deepseek/deepseek-chat"));
        assertTrue(systemCaptor.getValue().contains("analysis keywords"));
        assertTrue(promptCaptor.getValue().contains("Databricks"));
        assertFalse(promptCaptor.getValue().contains("ana.private@example.com"));
        assertFalse(promptCaptor.getValue().contains("555-1234"));
        verify(comparisons).save(comparison);
        verify(pdf).generatePdfFromJson(saved.getJsonData(), "ATS");
    }

    @Test
    void reusesPreviouslySavedTailoredResumeWithoutCallingDeepSeekAgain() {
        JobComparison comparison = comparison();
        comparison.setTailoredResumeId(42L);
        Resume saved = new Resume("user-1", "original_acme_corp", "{}", "ATS", "CVForge DeepSeek");
        saved.setId(42L);
        when(comparisons.findByIdAndUserId(8L, "user-1")).thenReturn(Optional.of(comparison));
        when(resumes.findByIdAndUserId(42L, "user-1")).thenReturn(Optional.of(saved));
        when(pdf.generatePdfFromJson("{}", "ATS")).thenReturn(new byte[]{1});

        var result = service.savePreview(8L, "user-1", null);

        assertEquals("original_acme_corp.pdf", result.filename());
        assertArrayEquals(new byte[]{1}, result.bytes());
        verifyNoInteractions(ai);
        verify(resumes, never()).save(any());
    }

    @Test
    void rejectsInvalidModelLanguageAndDoesNotSaveResume() {
        JobComparison comparison = comparison();
        Resume original = new Resume("user-1", "Original", "{\"name\":\"Ana\",\"skills\":[]}", "ATS", "CVForge");
        original.setId(4L);
        when(comparisons.findByIdAndUserId(8L, "user-1")).thenReturn(Optional.of(comparison));
        when(resumes.findByIdAndUserId(4L, "user-1")).thenReturn(Optional.of(original));
        when(ai.complete(anyString(), anyString(), eq(7000), eq("deepseek/deepseek-chat"))).thenReturn("{\"language\":\"fr\"}");

        assertThrows(IllegalStateException.class, () -> service.preview(8L, "user-1"));
        verify(resumes, never()).save(any());
        verify(comparisons, never()).save(any());
    }

    private JobComparison comparison() {
        JobComparison comparison = new JobComparison("user-1", 4L, "Original", "Desenvolvedor para Acme Corp", """
                {"companyName":"Acme Corp","score":82,"keywords":["Databricks","Python"],"resumeSuggestions":["Destacar experiência analítica existente"]}
                """, "pt");
        ReflectionTestUtils.setField(comparison, "id", 8L);
        return comparison;
    }
}
