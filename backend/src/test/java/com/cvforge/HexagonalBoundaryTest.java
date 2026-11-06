package com.cvforge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HexagonalBoundaryTest {
    @Test
    void inboundControllersCannotReachOutboundPortsOrAdapters() throws Exception {
        Path controllers = Path.of("src/main/java/com/cvforge/controller");
        try (var files = Files.walk(controllers)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("com.cvforge.application.port.out."), file + " depends on an outbound port");
                assertFalse(source.contains("com.cvforge.adapter.out."), file + " depends on an outbound adapter");
            }
        }
    }

    @Test
    void applicationCannotDependOnTransportOrConcreteAdapters() throws Exception {
        Path application = Path.of("src/main/java/com/cvforge/application");
        try (var files = Files.walk(application)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("com.cvforge.controller."), file + " depends on a web adapter");
                assertFalse(source.contains("com.cvforge.adapter."), file + " depends on a concrete adapter");
                assertFalse(source.contains("org.springframework."), file + " depends on the Spring framework");
                assertFalse(source.contains("jakarta."), file + " depends on Jakarta framework APIs");
            }
        }
    }
}
