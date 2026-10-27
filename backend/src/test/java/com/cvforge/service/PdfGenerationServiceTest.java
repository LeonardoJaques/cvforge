package com.cvforge.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfGenerationServiceTest {
    private final PdfGenerationService service = new PdfGenerationService();

    @Test
    void exportsAllSixLayoutsAndPreservesEducationAndFormattedText() throws Exception {
        String json = """
                {"name":"Ana Silva","summary":"Liderança **técnica**\\n• Arquitetura escalável","experience":[{"role":"Arquiteta","company":"Acme","period":"2022 — atual","description":"**Liderou** migração\\n• Reduziu custos"}],"education":[{"course":"MBA em Projetos","institution":"PUC Minas","period":"Previsão 2027"}],"courses":[{"course":"Scrum Master","institution":"Scrum.org","period":"Concluído"}],"projects":[{"name":"Portal de dados","description":"Projeto de demonstração","link":"https://example.com/portfolio"}],"skills":["Java"]}
                """;
        for (String layout : new String[]{"ATS", "EXECUTIVO", "TERMINAL", "MODERNO", "CRIATIVO", "COMPACTO"}) {
            byte[] pdf = service.generatePdfFromJson(json, layout);
            assertTrue(pdf.length > 1000, "PDF vazio para layout " + layout);
            try (var document = Loader.loadPDF(pdf)) {
                assertTrue(document.getNumberOfPages() >= 1, "PDF sem páginas para layout " + layout);
                String text = new PDFTextStripper().getText(document);
                assertTrue(text.contains("MBA em Projetos"), "Formação ausente no layout " + layout);
                assertTrue(text.contains("Previsão 2027"), "Status da formação ausente no layout " + layout);
                assertTrue(text.contains("Liderou"), "Texto formatado ausente no layout " + layout);
                assertTrue(text.contains("Scrum Master"), "Curso ausente no layout " + layout);
                assertTrue(text.contains("Portal de dados"), "Projeto ausente no layout " + layout);
                assertTrue(text.contains("https://example.com/portfolio"), "Link do projeto ausente no layout " + layout);
                assertEquals(595.28, document.getPage(0).getMediaBox().getWidth(), 1.0, "PDF não está em A4 no layout " + layout);
                assertEquals(841.89, document.getPage(0).getMediaBox().getHeight(), 1.0, "PDF não está em A4 no layout " + layout);
            }
        }
    }

    @Test
    void paginatesLongResumeOnA4AndKeepsPortfolioLinkClickable() throws Exception {
        StringBuilder jobs = new StringBuilder("[");
        for (int i = 0; i < 18; i++) {
            if (i > 0) jobs.append(',');
            jobs.append("{\"role\":\"Role ").append(i).append("\",\"company\":\"Company\",\"period\":\"2020 — 2024\",\"description\":\"")
                    .append("Led a cross functional team and delivered measurable improvements across multiple services. ".repeat(10)).append("\"}");
        }
        jobs.append(']');
        String json = "{\"name\":\"Long Resume\",\"experience\":" + jobs + ",\"projects\":[{\"name\":\"Portfolio\",\"link\":\"https://example.com/work\"}]}";
        byte[] pdf = service.generatePdfFromJson(json, "ATS");
        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1, "Currículo longo deve continuar em páginas A4 adicionais");
            for (var page : document.getPages()) {
                assertEquals(595.28, page.getMediaBox().getWidth(), 1.0);
                assertEquals(841.89, page.getMediaBox().getHeight(), 1.0);
            }
            java.util.List<PDAnnotationLink> links = new java.util.ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                for (var annotation : document.getPage(pageIndex).getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link) links.add(link);
                }
            }
            assertTrue(links.stream().anyMatch(link -> link.getAction() instanceof PDActionURI action && "https://example.com/work".equals(action.getURI())), "Link do portfólio deve ser clicável no PDF");
        }
    }
}
