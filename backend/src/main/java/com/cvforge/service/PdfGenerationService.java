package com.cvforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public byte[] generatePdfFromJson(String jsonData, String layout) {
        try {
            if (jsonData == null || jsonData.length() > 350_000) {
                throw new IllegalArgumentException("Os dados do currículo excedem o limite de 350 KB");
            }
            JsonNode resume = JSON.readTree(jsonData);
            if (resume == null || !resume.isObject()) {
                throw new IllegalArgumentException("Os dados do currículo devem ser um objeto JSON");
            }
            String normalizedLayout = layout == null ? "" : layout.toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("ATS", "EXECUTIVO", "TERMINAL", "MODERNO", "CRIATIVO", "COMPACTO").contains(normalizedLayout)) {
                throw new IllegalArgumentException("Layout inválido: " + layout);
            }
            for (String field : new String[]{"experience", "education", "courses", "projects", "skills", "links"}) {
                JsonNode list = resume.get(field);
                if (list != null && (!list.isArray() || list.size() > 100)) {
                    throw new IllegalArgumentException("Campo de lista inválido: " + field);
                }
            }
            String html = renderHtml(resume, normalizedLayout);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ITextRenderer renderer = new ITextRenderer();
                renderer.setDocumentFromString(html);
                renderer.layout();
                renderer.createPDF(output);
                return output.toByteArray();
            }
        } catch (Exception e) {
            log.error("Falha ao gerar PDF do currículo (layout={}): {}", layout, e.getMessage(), e);
            throw new IllegalArgumentException("Não foi possível gerar o PDF do currículo", e);
        }
    }

    private String renderHtml(JsonNode resume, String layout) {
        String mode = layout == null ? "ATS" : layout.toUpperCase();
        String family = "TERMINAL".equals(mode) ? "monospace" : ("CRIATIVO".equals(mode) || "EXECUTIVO".equals(mode)) ? "Georgia, serif" : "Helvetica, Arial, sans-serif";
        String heading = "EXECUTIVO".equals(mode) ? "#173b35" : "MODERNO".equals(mode) ? "#3159a5" : "CRIATIVO".equals(mode) ? "#8b3d64" : "COMPACTO".equals(mode) ? "#334155" : "#176b54";
        String density = "COMPACTO".equals(mode) ? "font-size:9pt; line-height:1.3;" : "font-size:10pt; line-height:1.45;";
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>");
        html.append("@page { size: A4; margin: 18mm 17mm; } body { font-family:")
                .append(family).append("; color:#18231f; ").append(density).append(" }")
                .append("h1 { color:").append(heading).append("; font-size:24pt; margin:0; }")
                .append("h2 { color:").append(heading).append("; font-size:12pt; border-bottom:1px solid #cad5cf; padding-bottom:3px; margin:18px 0 8px; }")
                .append("h3 { font-size:10.5pt; margin:8px 0 2px; page-break-after:avoid; }")
                .append("p { margin:3px 0; orphans:3; widows:3; } .muted { color:#56655e; } .item { page-break-inside:avoid; break-inside:avoid; margin-bottom:10px; } h2 { page-break-after:avoid; }")
                .append(".layout-executivo h1 { font-size:28pt; font-weight:normal; } .layout-moderno h1 { letter-spacing:.4pt; } .layout-moderno h2 { border-left:3px solid #5274bd; padding-left:7px; } .layout-criativo h1 { color:#8b3d64; } .layout-criativo h2 { color:#8b3d64; } .layout-compacto h2 { margin:11px 0 5px; padding-bottom:2px; } .layout-terminal { background:#101a1d; color:#d8e8de; } .layout-terminal h1,.layout-terminal h2 { color:#7cdbbf; } .layout-terminal .muted { color:#92b9aa; }")
                .append("</style></head><body class=\"layout-").append(mode.toLowerCase(java.util.Locale.ROOT)).append("\">");
        html.append("<h1>").append(escape(value(resume, "name"))).append("</h1>");
        addLine(html, value(resume, "headline"), "<p class=\"muted\"><strong>", "</strong></p>");
        String contact = join(value(resume, "email"), value(resume, "phone"), value(resume, "location"));
        addLine(html, contact, "<p class=\"muted\">", "</p>");
        section(html, "Resumo", value(resume, "summary"));
        JsonNode experience = resume.path("experience");
        if (experience.isArray() && !experience.isEmpty()) {
            html.append("<h2>Experiência</h2>");
            for (JsonNode item : experience) {
                html.append("<div class=\"item\"><h3>").append(escape(value(item, "role")))
                        .append(" — ").append(escape(value(item, "company"))).append("</h3>");
                addLine(html, value(item, "period"), "<p class=\"muted\">", "</p>");
                addRichLine(html, value(item, "description"), "<p>", "</p>");
                html.append("</div>");
            }
        }
        JsonNode education = resume.path("education");
        if (education.isArray() && !education.isEmpty()) {
            html.append("<h2>Formação</h2>");
            for (JsonNode item : education) {
                html.append("<div class=\"item\"><h3>").append(escape(value(item, "course")))
                        .append(" — ").append(escape(value(item, "institution"))).append("</h3>");
                addLine(html, value(item, "period"), "<p class=\"muted\">", "</p>");
                html.append("</div>");
            }
        }
        JsonNode courses = resume.path("courses");
        if (courses.isArray() && !courses.isEmpty()) {
            html.append("<h2>Cursos e certificações</h2>");
            for (JsonNode item : courses) {
                html.append("<div class=\"item\"><h3>").append(escape(value(item, "course"))).append("</h3>");
                addLine(html, value(item, "institution"), "<p class=\"muted\">", "</p>");
                addLine(html, value(item, "period"), "<p class=\"muted\">", "</p>");
                html.append("</div>");
            }
        }
        JsonNode projects = resume.path("projects");
        if (projects.isArray() && !projects.isEmpty()) {
            html.append("<h2>Projetos e portfólio</h2>");
            for (JsonNode item : projects) {
                String name = value(item, "name");
                String description = value(item, "description");
                String link = safeExternalUrl(value(item, "link").isBlank() ? value(item, "url") : value(item, "link"));
                html.append("<div class=\"item\"><h3>").append(escape(name)).append("</h3>");
                addRichLine(html, description, "<p>", "</p>");
                if (!link.isBlank()) html.append("<p><a href=\"").append(escape(link)).append("\">").append(escape(link)).append("</a></p>");
                html.append("</div>");
            }
        }
        addArraySection(html, "Competências", resume.path("skills"));
        addArraySection(html, "Links", resume.path("links"), true);
        return html.append("</body></html>").toString();
    }

    private void section(StringBuilder html, String title, String content) {
        if (content != null && !content.isBlank()) {
            html.append("<h2>").append(title).append("</h2><p>").append(richText(content)).append("</p>");
        }
    }

    private void addArraySection(StringBuilder html, String title, JsonNode values) {
        addArraySection(html, title, values, false);
    }

    private void addArraySection(StringBuilder html, String title, JsonNode values, boolean clickable) {
        if (values.isArray() && !values.isEmpty()) {
            html.append("<h2>").append(title).append("</h2><p>");
            boolean first = true;
            for (JsonNode item : values) {
                if (!first) html.append(" · ");
                String text = item.asText();
                String safeUrl = clickable ? safeExternalUrl(text) : "";
                if (!safeUrl.isBlank()) html.append("<a href=\"").append(escape(safeUrl)).append("\">").append(escape(text)).append("</a>");
                else html.append(escape(text));
                first = false;
            }
            html.append("</p>");
        }
    }

    private String safeExternalUrl(String value) {
        if (value == null || value.isBlank()) return "";
        String candidate = value.trim();
        if (!candidate.matches("(?i)^https?://.*")) {
            if (!candidate.matches("(?i)^(?:www\\.|[a-z0-9-]+\\.)[a-z0-9.-]+(?:/.*)?$")) return "";
            candidate = "https://" + candidate;
        }
        try {
            java.net.URI uri = java.net.URI.create(candidate);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) ? candidate : "";
        } catch (IllegalArgumentException e) { return ""; }
    }

    private void addLine(StringBuilder html, String value, String prefix, String suffix) {
        if (value != null && !value.isBlank()) html.append(prefix).append(escape(value)).append(suffix);
    }

    private void addRichLine(StringBuilder html, String value, String prefix, String suffix) {
        if (value != null && !value.isBlank()) html.append(prefix).append(richText(value)).append(suffix);
    }

    private String richText(String value) {
        StringBuilder result = new StringBuilder();
        for (String line : value.split("\\R", -1)) {
            if (!result.isEmpty()) result.append("<br/>");
            String safe = escape(line);
            safe = safe.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
                    .replaceAll("_(.+?)_", "<em>$1</em>");
            result.append(safe);
        }
        return result.toString();
    }

    private String value(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() ? value.asText() : "";
    }

    private String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!result.isEmpty()) result.append(" · ");
                result.append(value);
            }
        }
        return result.toString();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
