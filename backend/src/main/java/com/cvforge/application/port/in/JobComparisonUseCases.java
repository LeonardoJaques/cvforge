package com.cvforge.application.port.in;

import com.cvforge.application.model.JobComparisonData;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Inbound port for saved job-comparison actions. */
public interface JobComparisonUseCases {
    JobComparisonData save(String userId, Long resumeId, String profileLabel, String jobText, JsonNode result, String language);
    List<JobComparisonData> list(String userId);
    boolean delete(Long id, String userId);
}
