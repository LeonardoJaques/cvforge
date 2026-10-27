package com.cvforge.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Health check geral com detalhes de cada serviço */
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("application", "cvforge-api");
        status.put("version", "0.1.0-SNAPSHOT");

        // Verificar conexão com banco de dados
        boolean dbOk = checkDatabase();
        status.put("database", dbOk ? "connected" : "disconnected");

        // Verificar espaço em disco (info básica)
        status.put("diskSpace", getDiskSpaceInfo());

        return ResponseEntity.ok(status);
    }

    /** Health check simplificado (usado por Docker/monitoramento) */
    @GetMapping("/status")
    public ResponseEntity<String> simpleStatus() {
        return ResponseEntity.ok("UP");
    }

    private boolean checkDatabase() {
        try {
            Connection conn = jdbcTemplate.getDataSource().getConnection();
            boolean valid = conn.isValid(2);
            conn.close();
            return valid;
        } catch (SQLException e) {
            return false;
        }
    }

    private Map<String, Object> getDiskSpaceInfo() {
        java.io.File root = new java.io.File("/");
        long total = root.getTotalSpace();
        long free = root.getFreeSpace();
        long used = total - free;

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("totalBytes", total);
        info.put("freeBytes", free);
        info.put("usedBytes", used);
        info.put("usagePercent", total > 0 ? Math.round((used * 100.0) / total) : 0);
        return info;
    }
}
