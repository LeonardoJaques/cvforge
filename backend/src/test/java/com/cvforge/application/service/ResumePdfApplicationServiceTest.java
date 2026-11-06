package com.cvforge.application.service;

import com.cvforge.application.model.ResumeData;
import com.cvforge.application.port.in.ResumePdfUseCases;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.port.out.PdfRenderingPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumePdfApplicationServiceTest {
    @Test
    void savedResumePdfIsOrchestratedThroughApplicationPorts() {
        ResumeUseCases resumes = mock(ResumeUseCases.class);
        PdfRenderingPort pdf = mock(PdfRenderingPort.class);
        when(resumes.getResumeById(7L, "leonardo")).thenReturn(Optional.of(
                new ResumeData(7L, "leonardo", "CV", "{}", "ATS", "CVForge", null, null, null)));
        when(pdf.generatePdfFromJson("{}", "ATS")).thenReturn(new byte[]{1, 2, 3});

        ResumePdfUseCases useCase = new ResumePdfApplicationService(resumes, pdf);
        Optional<ResumePdfUseCases.PdfDownload> result = useCase.forSavedResume(7L, "leonardo");

        assertTrue(result.isPresent());
        assertArrayEquals(new byte[]{1, 2, 3}, result.orElseThrow().bytes());
        assertEquals("curriculo_7.pdf", result.orElseThrow().filename());
        verify(pdf).generatePdfFromJson("{}", "ATS");
    }

    @Test
    void missingResumeDoesNotCallPdfAdapter() {
        ResumeUseCases resumes = mock(ResumeUseCases.class);
        PdfRenderingPort pdf = mock(PdfRenderingPort.class);
        when(resumes.getResumeById(9L, "leonardo")).thenReturn(Optional.empty());

        assertTrue(new ResumePdfApplicationService(resumes, pdf).forSavedResume(9L, "leonardo").isEmpty());
        verifyNoInteractions(pdf);
    }
}
