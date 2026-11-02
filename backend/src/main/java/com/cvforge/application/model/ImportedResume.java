package com.cvforge.application.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Normalized resume returned by file and profile-text import use cases. */
public record ImportedResume(String title, ObjectNode data, List<String> warnings) { }
