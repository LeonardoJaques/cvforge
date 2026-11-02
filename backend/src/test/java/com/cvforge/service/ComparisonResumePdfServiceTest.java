package com.cvforge.service;

import com.cvforge.application.model.JobComparisonData;
import com.cvforge.application.model.ResumeData;
import com.cvforge.application.port.in.ComparisonResumePdfUseCases;
import com.cvforge.application.port.out.JobComparisonStore;
import com.cvforge.application.port.out.PdfRenderingPort;
import com.cvforge.application.port.out.ResumeStore;
import com.cvforge.application.port.out.TextCompletionPort;
import com.cvforge.application.service.ComparisonResumePdfApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComparisonResumePdfApplicationServiceTest {
    @Mock private JobComparisonStore comparisons;
    @Mock private ResumeStore resumes;
    @Mock private TextCompletionPort ai;
    @Mock private PdfRenderingPort pdf;
    private final ObjectMapper mapper = new ObjectMapper();
    private ComparisonResumePdfApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ComparisonResumePdfApplicationService(comparisons, resumes, ai, pdf, mapper, "deepseek/deepseek-chat");
    }

    @Test
    void previewsAndSavesResumeThroughPortsWithoutPersistingBeforeConfirmation() throws Exception {
        JobComparisonData comparison = comparison();
        ResumeData original = resume(4L, "Original", """
                {"name":"Ana Silva","headline":"Engenheira","email":"ana.private@example.com","phone":"555-1234","location":"São Paulo","summary":"Resumo original","experience":[{"role":"Engenheira","company":"Origem Ltda","period":"2020-2024","description":"Entrega de sistemas"}],"education":[{"course":"Sistemas","institution":"Universidade","period":"2016-2020"}],"projects":[{"name":"Projeto A","description":"Projeto original","link":"https://example.com"}],"skills":["Java","SQL"],"links":["https://example.com"]}
                """);
        when(comparisons.findByIdAndUser(8L, "user-1")).thenReturn(Optional.of(comparison));
        when(comparisons.findRecentlyTailoredByUser(eq("user-1"), any())).thenReturn(List.of());
        when(resumes.findByIdAndUser(4L, "user-1")).thenReturn(Optional.of(original));
        when(ai.complete(anyString(), anyString(), eq(7000), eq("deepseek/deepseek-chat"))).thenReturn("""
                ```json
                {"language":"pt","headline":"Engenheira de software","summary":"Resumo direcionado à vaga","experienceDescriptions":["Entregou sistemas alinhados às necessidades do negócio"],"projectDescriptions":["Projeto A aplicado a dados"],"skills":["Java","Python"]}
                ```
                """);
        when(resumes.save(any(ResumeData.class))).thenAnswer(invocation -> ((ResumeData) invocation.getArgument(0)).withIdAndTimestamps(42L, null, null, 0));
        when(comparisons.save(any(JobComparisonData.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        ArgumentCaptor<ResumeData> savedCaptor = ArgumentCaptor.forClass(ResumeData.class);
        verify(resumes).save(savedCaptor.capture());
        ResumeData saved = savedCaptor.getValue();
        assertEquals("original_acme_corp", saved.title());
        JsonNode data = mapper.readTree(saved.jsonData());
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
        verify(pdf).generatePdfFromJson(saved.jsonData(), "ATS");
    }

    @Test
    void reusesPreviouslySavedResumeWithoutCallingAiAgain() {
        JobComparisonData comparison = comparison();
        comparison.setTailoredResumeId(42L);
        ResumeData saved = resume(42L, "original_acme_corp", "{}");
        when(comparisons.findByIdAndUser(8L, "user-1")).thenReturn(Optional.of(comparison));
        when(resumes.findByIdAndUser(42L, "user-1")).thenReturn(Optional.of(saved));
        when(pdf.generatePdfFromJson("{}", "ATS")).thenReturn(new byte[]{1});
        var result = service.savePreview(8L, "user-1", null);
        assertEquals("original_acme_corp.pdf", result.filename());
        assertArrayEquals(new byte[]{1}, result.bytes());
        verifyNoInteractions(ai);
        verify(resumes, never()).save(any());
    }

    @Test
    void rejectsInvalidModelLanguageAndDoesNotSaveResume() {
        JobComparisonData comparison = comparison();
        ResumeData original = resume(4L, "Original", "{\"name\":\"Ana\",\"skills\":[]}");
        when(comparisons.findByIdAndUser(8L, "user-1")).thenReturn(Optional.of(comparison));
        when(comparisons.findRecentlyTailoredByUser(eq("user-1"), any())).thenReturn(List.of());
        when(resumes.findByIdAndUser(4L, "user-1")).thenReturn(Optional.of(original));
        when(ai.complete(anyString(), anyString(), eq(7000), eq("deepseek/deepseek-chat"))).thenReturn("{\"language\":\"fr\"}");
        assertThrows(IllegalStateException.class, () -> service.preview(8L, "user-1"));
        verify(resumes, never()).save(any());
        verify(comparisons, never()).save(any());
    }

    private ResumeData resume(Long id, String title, String json) {
        return new ResumeData(id, "user-1", title, json, "ATS", "CVForge", null, null, 0);
    }
    private JobComparisonData comparison() {
        JobComparisonData comparison = new JobComparisonData("user-1", 4L, "Original", "Desenvolvedor para Acme Corp", """
                {"companyName":"Acme Corp","score":82,"keywords":["Databricks","Python"],"resumeSuggestions":["Destacar experiência analítica existente"]}
                """, "pt");
        comparison.setId(8L);
        return comparison;
    }
}
