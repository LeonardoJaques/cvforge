package com.cvforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ResumeImportServiceTest {
    private ResumeImportService service;

    @BeforeEach
    void setUp() { service = new ResumeImportService(new ObjectMapper()); }

    @Test
    void normalizesJsonResumeIntoEditableCvForgeModel() {
        String json = """
                {"basics":{"name":"Ana Silva","label":"Engenheira","email":"ana@example.com","location":{"city":"Recife","region":"PE"}},
                 "work":[{"name":"Acme","position":"Desenvolvedora","startDate":"2021","endDate":"2024","highlights":["Liderou migração"]}],
                 "education":[{"institution":"UFPE","studyType":"Bacharelado","area":"Computação","startDate":"2017","endDate":"2020"}],
                 "skills":[{"name":"Java","keywords":["Spring"]}],"courses":["Azure Fundamentals"],"projects":[{"title":"Portfolio","description":"Case study","url":"https://example.com/work"}],"profiles":[{"url":"https://linkedin.com/in/ana"}]}
                """;
        var result = service.importFile(new MockMultipartFile("file", "ana.json", "application/json", json.getBytes()));

        assertEquals("ana", result.title());
        assertEquals("Ana Silva", result.data().path("name").asText());
        assertEquals("Recife, PE", result.data().path("location").asText());
        assertEquals("2021 — 2024", result.data().path("experience").get(0).path("period").asText());
        assertEquals("Java", result.data().path("skills").get(0).asText());
        assertTrue(result.data().path("skills").toString().contains("Spring"));
        assertEquals("https://linkedin.com/in/ana", result.data().path("links").get(0).asText());
        assertEquals("Azure Fundamentals", result.data().path("courses").get(0).path("course").asText());
        assertEquals("Portfolio", result.data().path("projects").get(0).path("name").asText());
        assertEquals("Case study", result.data().path("projects").get(0).path("description").asText());
        assertEquals("https://example.com/work", result.data().path("projects").get(0).path("link").asText());
    }

    @Test
    void extractsContactAndSectionsFromTextPdf() throws Exception {
        byte[] pdf = createTextPdf("Ana Silva\nSoftware Engineer\nana@example.com\n+55 81 99999-8888\nSUMMARY\nExperience with Java platforms.\nSKILLS\nJava, Spring, PostgreSQL");
        var result = service.importFile(new MockMultipartFile("file", "ana.pdf", "application/pdf", pdf));

        assertEquals("Ana Silva", result.data().path("name").asText());
        assertEquals("ana@example.com", result.data().path("email").asText());
        assertTrue(result.data().path("summary").asText().contains("Java platforms"));
        assertEquals(3, result.data().path("skills").size());
    }

    @Test
    void extractsMonthNameAndCurrentPeriodDespiteCommonOcrTypo() throws Exception {
        byte[] pdf = createTextPdf("Leonardo Jaques\nEXPERIENCE\nMutant | Remoto — São Paulo / Vivo\nSolution Architect / Tech Lead\nJul 2024 — Atuaimente\nArquitetura de sistemas e liderança técnica.");
        var result = service.importFile(new MockMultipartFile("file", "Leonardo_Jaques_Curriculo.pdf", "application/pdf", pdf));

        assertEquals(1, result.data().path("experience").size());
        assertEquals("Jul 2024 — Atuaimente", result.data().path("experience").get(0).path("period").asText());
        assertEquals("Solution Architect / Tech Lead", result.data().path("experience").get(0).path("role").asText());
        assertEquals("Mutant | Remoto — São Paulo / Vivo", result.data().path("experience").get(0).path("company").asText());
    }

    @Test
    void importsEachEducationCourseAndKeepsCompletionStatus() throws Exception {
        byte[] pdf = createTextPdf("Ana Silva\nFORMAÇÃO ACADÊMICA\nPós-Graduação em Engenharia de Software — PUC Minas Previsão 2027\nMBA em Gestão de Projetos — Universidade Castelo Branco Concluído\nCURSOS E CERTIFICAÇÕES\nScrum Master — Scrum.org Concluído\nPower BI — Escola de Dados 2023");
        var result = service.importFile(new MockMultipartFile("file", "ana.pdf", "application/pdf", pdf));

        assertEquals(2, result.data().path("education").size());
        assertEquals("Pós-Graduação em Engenharia de Software", result.data().path("education").get(0).path("course").asText());
        assertEquals("PUC Minas", result.data().path("education").get(0).path("institution").asText());
        assertEquals("Previsão 2027", result.data().path("education").get(0).path("period").asText());
        assertEquals("MBA em Gestão de Projetos", result.data().path("education").get(1).path("course").asText());
        assertEquals("Universidade Castelo Branco", result.data().path("education").get(1).path("institution").asText());
        assertEquals("Concluído", result.data().path("education").get(1).path("period").asText());
        assertEquals(2, result.data().path("courses").size());
        assertEquals("Scrum Master", result.data().path("courses").get(0).path("course").asText());
        assertEquals("Scrum.org", result.data().path("courses").get(0).path("institution").asText());
        assertEquals("Concluído", result.data().path("courses").get(0).path("period").asText());
    }

    @Test
    void extractsPortfolioProjectAndActiveLinkFromPdfSection() throws Exception {
        byte[] pdf = createTextPdf("Ana Silva\nPROJETOS E PORTFÓLIO\nPlataforma de dados\nAnálise e visualização de indicadores\nhttps://example.com/projeto\nFORMAÇÃO ACADÊMICA\nEngenharia — UFPE");
        var result = service.importFile(new MockMultipartFile("file", "ana.pdf", "application/pdf", pdf));

        assertEquals(1, result.data().path("projects").size());
        assertEquals("Plataforma de dados", result.data().path("projects").get(0).path("name").asText());
        assertTrue(result.data().path("projects").get(0).path("description").asText().contains("Análise e visualização"));
        assertEquals("https://example.com/projeto", result.data().path("projects").get(0).path("link").asText());
        assertEquals(1, result.data().path("education").size());
    }

    @Test
    void importsAllFiveProjectsWhenPdfTextRunsIntoInlinePortfolioHeading() throws Exception {
        String extractedText = "Ana Silva\nRio Card: Escrita de microsserviços integrados no ecossistema financeiro usando Java e Vue.js, além de integrações mobile. PROJETOS & PORTFÓLIO = HidroGeo (Open Source): Ferramenta para monitoramento pluviométrico no RJ. Credit Application System: Sistema transacional para análise e solicitação de crédito escrito em Kotlin, com Virtual Threads. DDD Project Fullstack: Aplicação demonstrativa em TypeScript de Domain-Driven Design. Lab Java Quarkus: Estudo de microsserviços cloud native focados em performance de memória.";
        byte[] pdf = createTextPdf(extractedText);
        var result = service.importFile(new MockMultipartFile("file", "portfolio.pdf", "application/pdf", pdf));

        var projects = result.data().path("projects");
        assertEquals(5, projects.size(), "Todos os títulos devem entrar na lista de projetos");
        assertEquals("Rio Card", projects.get(0).path("name").asText());
        assertEquals("HidroGeo (Open Source)", projects.get(1).path("name").asText());
        assertEquals("Credit Application System", projects.get(2).path("name").asText());
        assertEquals("DDD Project Fullstack", projects.get(3).path("name").asText());
        assertEquals("Lab Java Quarkus", projects.get(4).path("name").asText());
        assertTrue(projects.get(0).path("description").asText().contains("microsserviços"));
        assertTrue(projects.get(1).path("description").asText().contains("monitoramento pluviométrico"));
        assertTrue(projects.get(2).path("description").asText().contains("solicitação de crédito"));
        assertEquals(0, result.data().path("experience").size(), "O trecho do portfólio não deve ser classificado como experiência");
    }

    @Test
    void rejectsBlankPdfAfterOcrFindsNoText() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.importFile(file));
        assertTrue(error.getMessage().contains("reconhecer texto"));
    }

    @Test
    void recognizesTextFromScannedPdf() throws Exception {
        BufferedImage image = new BufferedImage(1000, 1400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 40));
        graphics.drawString("Ana Silva", 90, 130);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 30));
        graphics.drawString("Software Engineer", 90, 190);
        graphics.drawString("ana@example.com", 90, 250);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 34));
        graphics.drawString("SUMMARY", 90, 360);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 28));
        graphics.drawString("Experience with Java platforms and backend development.", 90, 415);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 34));
        graphics.drawString("SKILLS", 90, 520);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 28));
        graphics.drawString("Java, Spring, PostgreSQL", 90, 575);
        graphics.dispose();

        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            var ximage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(ximage, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        var result = service.importFile(new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf));

        assertTrue(result.data().path("name").asText().contains("Ana Silva"));
        assertEquals("ana@example.com", result.data().path("email").asText());
        assertTrue(result.data().path("summary").asText().contains("Java platforms"));
        assertTrue(result.data().path("skills").toString().contains("PostgreSQL"));
    }

    private byte[] createTextPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.newLineAtOffset(50, 750);
                for (String line : text.split("\\n")) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -16);
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
