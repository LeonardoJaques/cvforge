package com.cvforge.config;

import com.cvforge.application.port.in.ComparisonResumePdfUseCases;
import com.cvforge.application.port.in.JobComparisonUseCases;
import com.cvforge.application.port.in.JobMatchUseCases;
import com.cvforge.application.port.in.ProfileTextImportUseCases;
import com.cvforge.application.port.in.ResumeImportUseCases;
import com.cvforge.application.port.in.ResumePdfUseCases;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.port.out.JobComparisonStore;
import com.cvforge.application.port.out.PdfRenderingPort;
import com.cvforge.application.port.out.ResumeStore;
import com.cvforge.application.port.out.TextCompletionPort;
import com.cvforge.application.service.ComparisonResumePdfApplicationService;
import com.cvforge.application.service.JobComparisonApplicationService;
import com.cvforge.application.service.JobMatchApplicationService;
import com.cvforge.application.service.ProfileTextImportApplicationService;
import com.cvforge.application.service.ResumeApplicationService;
import com.cvforge.application.service.ResumePdfApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root: wires framework independent application services to ports. */
@Configuration
public class ApplicationServicesConfiguration {
    @Bean
    ResumeApplicationService resumeUseCases(ResumeStore resumes) {
        return new ResumeApplicationService(resumes);
    }

    @Bean
    ResumePdfUseCases resumePdfUseCases(ResumeUseCases resumes, PdfRenderingPort pdf) {
        return new ResumePdfApplicationService(resumes, pdf);
    }

    @Bean
    ProfileTextImportUseCases profileTextImportUseCases(TextCompletionPort ai,
            ResumeImportUseCases resumeImports, ObjectMapper mapper) {
        return new ProfileTextImportApplicationService(ai, resumeImports, mapper);
    }

    @Bean
    JobMatchUseCases jobMatchUseCases(ResumeUseCases resumes, TextCompletionPort ai, ObjectMapper mapper) {
        return new JobMatchApplicationService(resumes, ai, mapper);
    }

    @Bean
    JobComparisonApplicationService jobComparisonUseCases(JobComparisonStore comparisons, ObjectMapper mapper) {
        return new JobComparisonApplicationService(comparisons, mapper);
    }

    @Bean
    ComparisonResumePdfUseCases comparisonResumePdfUseCases(JobComparisonStore comparisons, ResumeStore resumes,
            TextCompletionPort ai, PdfRenderingPort pdf, ObjectMapper mapper,
            @Value("${openrouter.resume-model:deepseek/deepseek-chat}") String model) {
        return new ComparisonResumePdfApplicationService(comparisons, resumes, ai, pdf, mapper, model);
    }
}
