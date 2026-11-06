package com.cvforge.application.service;

import com.cvforge.application.port.in.ResumePdfUseCases;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.port.out.PdfRenderingPort;

import java.util.Optional;

/** Application use cases for PDF generation, independent of HTTP response details. */
public class ResumePdfApplicationService implements ResumePdfUseCases {
    private final ResumeUseCases resumes;
    private final PdfRenderingPort pdf;

    public ResumePdfApplicationService(ResumeUseCases resumes, PdfRenderingPort pdf) {
        this.resumes = resumes;
        this.pdf = pdf;
    }

    @Override
    public Optional<PdfDownload> forSavedResume(Long id, String userId) {
        return resumes.getResumeById(id, userId)
                .map(resume -> new PdfDownload(pdf.generatePdfFromJson(resume.jsonData(), resume.layout()),
                        "curriculo_" + id + ".pdf"));
    }

    @Override
    public byte[] fromJson(String jsonData, String layout) {
        return pdf.generatePdfFromJson(jsonData, layout);
    }
}
