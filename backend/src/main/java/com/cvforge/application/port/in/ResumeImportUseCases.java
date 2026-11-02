package com.cvforge.application.port.in;

import com.cvforge.application.model.ImportedResume;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface ResumeImportUseCases {
    ImportedResume importFile(String originalFilename, String contentType, byte[] bytes);
    ImportedResume importStructured(String title, JsonNode data, List<String> warnings);
}
