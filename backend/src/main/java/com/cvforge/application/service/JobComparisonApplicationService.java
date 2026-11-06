package com.cvforge.application.service;

import com.cvforge.application.model.JobComparisonData;
import com.cvforge.application.port.in.JobComparisonUseCases;
import com.cvforge.application.port.out.JobComparisonStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class JobComparisonApplicationService implements JobComparisonUseCases {
    private final JobComparisonStore comparisons;
    private final ObjectMapper mapper;
    public JobComparisonApplicationService(JobComparisonStore comparisons, ObjectMapper mapper) {
        this.comparisons=comparisons; this.mapper=mapper;
    }
    @Override
    public JobComparisonData save(String userId, Long resumeId, String profileLabel, String jobText, JsonNode result, String language) {
        if (result==null || !result.isObject() || jobText==null || jobText.isBlank())
            throw new IllegalArgumentException("A análise e a descrição da vaga são obrigatórias.");
        if (jobText.length()>25_000) throw new IllegalArgumentException("A descrição da vaga excede o limite permitido.");
        String safeLanguage="en".equalsIgnoreCase(language)?"en":"pt";
        String label=profileLabel==null||profileLabel.isBlank()?"Perfil":profileLabel.trim();
        try {
            return comparisons.save(new JobComparisonData(userId,resumeId,label.substring(0,Math.min(100,label.length())),
                    jobText.trim(),mapper.writeValueAsString(result),safeLanguage));
        } catch(Exception e) { throw new IllegalStateException("Não foi possível salvar a comparação.",e); }
    }
    @Override
    public List<JobComparisonData> list(String userId) { return comparisons.findByUser(userId); }
    @Override
    public boolean delete(Long id,String userId) {
        var found=comparisons.findByIdAndUser(id,userId);
        if(found.isEmpty())return false;
        comparisons.delete(found.get());
        return true;
    }
}
