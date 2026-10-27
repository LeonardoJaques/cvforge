package com.cvforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMatchServiceOutputLimitTest {

    @Mock private ResumeService resumes;
    @Mock private OpenRouterClient openRouter;

    @Test
    void requestsSixThousandCompletionTokensForJobAnalysis() {
        when(openRouter.complete(anyString(), anyString(), eq(6000))).thenReturn(
                "{\"matchScore\":80,\"level\":\"Sênior\",\"summary\":\"Perfil compatível.\","
                        + "\"matchingRequirements\":[],\"missingRequirements\":[],\"keywords\":[],"
                        + "\"resumeSuggestions\":[],\"interviewQuestions\":[],\"disclaimer\":\"Revise a análise.\"}");

        JobMatchService service = new JobMatchService(resumes, openRouter, new ObjectMapper());
        service.analyze(new JobMatchService.Request(null, "Perfil de teste", "Vaga de teste", "pt"), "test-user");

        verify(openRouter).complete(anyString(), anyString(), eq(6000));
    }
}
