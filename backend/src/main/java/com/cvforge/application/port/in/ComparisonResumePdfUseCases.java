package com.cvforge.application.port.in;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface ComparisonResumePdfUseCases {
    ResumePreview preview(Long id, String userId);
    PdfDownload savePreview(Long id, String userId, String token);
    record ResumePreview(String token, String filename, JsonNode data, String layout, List<ResumeChange> changes, boolean cached, int matchScore, List<ResumeUnmetRequirement> unmetRequirements) { }
    record ResumeUnmetRequirement(String requirement, String reason) { }
    record ResumeChange(String section, String item, String before, String after) { }
    record PdfDownload(byte[] bytes, String filename) { }
}
