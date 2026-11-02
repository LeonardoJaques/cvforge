package com.cvforge.application.port.in;

import com.fasterxml.jackson.databind.JsonNode;

public interface JobMatchUseCases {
    JsonNode analyze(Request request, String userId);
    record Request(Long resumeId, String profileText, String jobText, String language) { }
}
