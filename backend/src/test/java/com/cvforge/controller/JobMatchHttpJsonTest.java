package com.cvforge.controller;

import com.cvforge.application.model.JobComparisonData;
import com.cvforge.application.model.ImportedResume;
import com.cvforge.application.port.in.ComparisonResumePdfUseCases;
import com.cvforge.application.port.in.JobComparisonUseCases;
import com.cvforge.application.port.in.JobMatchUseCases;
import com.cvforge.application.port.in.ResumeImportUseCases;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.port.in.ProfileTextImportUseCases;
import com.cvforge.application.port.out.PdfRenderingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({JobMatchController.class, ResumeController.class})
@AutoConfigureMockMvc(addFilters = false)
class JobMatchHttpJsonTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private JobMatchUseCases jobMatch;
    @MockitoBean private JobComparisonUseCases comparisons;
    @MockitoBean private ComparisonResumePdfUseCases comparisonResumePdf;
    @MockitoBean private ResumeUseCases resumes;
    @MockitoBean private ResumeImportUseCases resumeImport;
    @MockitoBean private ProfileTextImportUseCases profileImport;
    @MockitoBean private PdfRenderingPort pdf;

    @Test
    void savedComparisonPreservesNestedResultAndDateOnTheWire() throws Exception {
        JobComparisonData saved = comparison();
        when(comparisons.list("reader")).thenReturn(List.of(saved));

        mvc.perform(get("/api/v1/jobs/comparisons").header("Remote-User", "reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result.matchScore").value(81))
                .andExpect(jsonPath("$[0].result.companyName").value("Example Corp"))
                .andExpect(jsonPath("$[0].result.matchingRequirements[0].requirement").value("Java"))
                .andExpect(jsonPath("$[0].createdAt").isString());
    }

    @Test
    void saveAcceptsNestedJsonResult() throws Exception {
        when(comparisons.save(eq("reader"), eq(7L), anyString(), anyString(), any(JsonNode.class), eq("pt")))
                .thenAnswer(invocation -> {
                    JsonNode result = invocation.getArgument(4);
                    assertEquals(81, result.path("matchScore").asInt());
                    assertEquals("Example Corp", result.path("companyName").asText());
                    return comparison();
                });
        mvc.perform(post("/api/v1/jobs/comparisons").header("Remote-User", "reader")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":7,\"profileLabel\":\"Profile\",\"jobText\":\"Role\",\"language\":\"pt\",\"result\":{\"matchScore\":81,\"companyName\":\"Example Corp\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.matchScore").value(81))
                .andExpect(jsonPath("$.result.companyName").value("Example Corp"));
    }

    @Test
    void analysisAndPreviewPreserveJsonNodes() throws Exception {
        when(jobMatch.analyze(any(), eq("reader"))).thenReturn(mapper.readTree("{\"matchScore\":81,\"level\":\"Senior\"}"));
        mvc.perform(post("/api/v1/jobs/analyze").header("Remote-User", "reader")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileText\":\"Profile\",\"jobText\":\"Role\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(81));

        when(comparisonResumePdf.preview(42L, "reader")).thenReturn(
                new ComparisonResumePdfUseCases.ResumePreview("token", "resume.pdf",
                        mapper.readTree("{\"name\":\"Example\",\"skills\":[\"Java\"]}"), "ATS",
                        List.of(), false, 81, List.of()));
        mvc.perform(post("/api/v1/jobs/comparisons/42/resume-preview").header("Remote-User", "reader"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Example"))
                .andExpect(jsonPath("$.data.skills[0]").value("Java"))
                .andExpect(jsonPath("$.matchScore").value(81));
    }

    @Test
    void importedResumePreservesNestedData() throws Exception {
        ObjectNode data = mapper.createObjectNode();
        data.put("name", "Example");
        data.putArray("skills").add("Java");
        when(profileImport.importText("Profile")).thenReturn(new ImportedResume("Profile", data, List.of()));
        mvc.perform(post("/api/v1/resumes/import/text").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Profile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Example"))
                .andExpect(jsonPath("$.data.skills[0]").value("Java"));
    }

    private JobComparisonData comparison() {
        JobComparisonData saved = new JobComparisonData("reader", 7L, "Profile", "Role",
                "{\"matchScore\":81,\"companyName\":\"Example Corp\",\"matchingRequirements\":[{\"requirement\":\"Java\",\"evidence\":\"Experience\"}]}", "pt");
        saved.setId(42L);
        return saved;
    }
}
