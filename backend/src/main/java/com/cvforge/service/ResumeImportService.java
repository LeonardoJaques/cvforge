package com.cvforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeImportService {
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PDF_PAGES = 25;
    private static final int MAX_OCR_PAGES = 10;
    private static final int MAX_EXTRACTED_TEXT = 150_000;
    private static final Pattern EMAIL = Pattern.compile("(?iu)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d .()/-]{6,}\\d)(?!\\w)");
    private static final String MONTH = "(?:jan(?:uary|eiro)?|feb(?:ruary)?|fev(?:ereiro)?|mar(?:ch|ço|co)?|abr(?:il)?|apr(?:il)?|mai(?:o)?|may|jun(?:e|ho)?|jul(?:y|ho)?|aug(?:ust)?|ago(?:sto)?|set(?:embro)?|sep(?:t|tember)?|out(?:ubro)?|oct(?:ober)?|nov(?:ember|embro)?|dez(?:ember|embro)?)";
    private static final String DATE_VALUE = "(?:\\b(?:19|20)\\d{2}\\b|\\b" + MONTH + "\\.?\\s+(?:de\\s+)?(?:19|20)\\d{2}\\b)";
    private static final Pattern DATE_RANGE = Pattern.compile("(?iu)" + DATE_VALUE + "\\s*(?:-|–|—|−|to|até)\\s*(?:" + DATE_VALUE + "|atua[il]\\w*|presente|present|currently|current|ongoing|hoje)");
    private static final Pattern LINK = Pattern.compile("(?iu)(?:https?://|www\\.)[^\\s<>]+|(?:linkedin\\.com|github\\.com)/[^\\s<>]+");
    private static final Pattern INLINE_PROJECT_HEADING = Pattern.compile("(?iu)(?:projetos?\\s*(?:e|&|and)\\s*portf[oó]lio|projects?\\s*(?:and|&)\\s*portfolio)");
    private static final Pattern EDUCATION_STATUS = Pattern.compile("(?iu)\\s*(?:[—–-]\\s*)?(conclu[ií]d[oa]|em andamento|cursando|previs[aã]o\\s*(?:de\\s*)?(?:19|20)\\d{2}|expected\\s*(?:19|20)\\d{2}|completed|(?:19|20)\\d{2})\\s*$");
    private static final Pattern EDUCATION_START = Pattern.compile("(?iu)^(?:p[oó]s(?:-gradua[cç][aã]o)?|mba\\b|gradua[cç][aã]o|bacharel(?:ado)?|licenciatura|tecn[oó]logo|mestrado|doutorado|especializa[cç][aã]o|curso\\b|certifica[cç][aã]o)");
    private static final Set<String> SUMMARY_HEADINGS = Set.of("resumo", "resumo profissional", "perfil", "perfil profissional", "objetivo", "sobre", "summary", "professional summary", "profile", "about", "objective");
    private static final Set<String> EXPERIENCE_HEADINGS = Set.of("experiencia", "experiencia profissional", "historico profissional", "experiencia de trabalho", "experience", "work experience", "professional experience", "employment history", "work history");
    private static final Set<String> EDUCATION_HEADINGS = Set.of("formacao", "formacao academica", "educacao", "educacao academica", "education", "academic background");
    private static final Set<String> COURSE_HEADINGS = Set.of("cursos", "cursos complementares", "cursos e certificacoes", "cursos e certificados", "certificacoes", "certificados", "treinamentos", "cursos e treinamentos", "other courses", "other courses certifications", "courses", "courses and certifications", "certifications", "training", "professional development");
    private static final Set<String> PROJECT_HEADINGS = Set.of("projetos", "projetos e portfolio", "portfolio", "portfolio profissional", "projetos selecionados", "projects", "selected projects", "projects and portfolio");
    private static final Set<String> SKILLS_HEADINGS = Set.of("competencias", "habilidades", "competencias tecnicas", "skills", "technical skills", "core skills");

    private final ObjectMapper mapper;

    public ResumeImportService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo PDF ou JSON.");
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("O arquivo precisa ter até 10 MB.");
        String filename = safeTitle(file.getOriginalFilename());
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (original.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(file.getContentType())) {
                ObjectNode data = parsePdf(bytes);
                if (data.path("name").asText().isBlank()) {
                    String nameFromFilename = nameFromFilename(filename);
                    if (!nameFromFilename.isBlank()) data.put("name", nameFromFilename);
                }
                return new ImportResult(filename, data, List.of("Revise os campos extraídos antes de salvar."));
            }
            if (original.endsWith(".json") || "application/json".equalsIgnoreCase(file.getContentType()) || "text/json".equalsIgnoreCase(file.getContentType())) {
                JsonNode root = mapper.readTree(bytes);
                ObjectNode data = normalizeJson(root);
                return new ImportResult(filename, data, List.of("Revise os campos importados antes de salvar."));
            }
            throw new IllegalArgumentException("Formato não aceito. Envie um PDF ou arquivo JSON.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível ler o arquivo. Confirme se ele não está protegido ou corrompido.", e);
        }
    }

    private ObjectNode parsePdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') {
            throw new IllegalArgumentException("O arquivo selecionado não parece ser um PDF válido.");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new IllegalArgumentException("Este PDF está protegido por senha.");
            if (document.getNumberOfPages() > MAX_PDF_PAGES) throw new IllegalArgumentException("O PDF precisa ter no máximo 25 páginas.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                text = recognizeScannedPdf(document);
            }
            if (text.length() > MAX_EXTRACTED_TEXT) throw new IllegalArgumentException("O texto extraído é muito longo para importar.");
            return normalizePdfText(text);
        }
    }

    private String recognizeScannedPdf(PDDocument document) {
        if (document.getNumberOfPages() > MAX_OCR_PAGES) {
            throw new IllegalArgumentException("Este PDF escaneado tem mais de 10 páginas. Divida o arquivo e importe cada parte.");
        }
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder recognized = new StringBuilder();
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            try {
                BufferedImage image = renderer.renderImageWithDPI(page, 110, ImageType.RGB);
                ProcessBuilder tesseract = new ProcessBuilder("tesseract", "stdin", "stdout", "-l", "por+eng", "--oem", "1", "--psm", "6");
                tesseract.environment().put("OMP_THREAD_LIMIT", "2");
                Process process = tesseract.redirectError(ProcessBuilder.Redirect.DISCARD).start();
                try (ByteArrayOutputStream png = new ByteArrayOutputStream(); var input = process.getOutputStream()) {
                    ImageIO.write(image, "png", png);
                    png.writeTo(input);
                }
                image.flush();
                if (!process.waitFor(Duration.ofSeconds(45).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalArgumentException("A leitura OCR deste PDF excedeu o tempo limite. Tente um arquivo menor.");
                }
                String pageText = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (process.exitValue() != 0) throw new IllegalArgumentException("Não foi possível reconhecer o texto deste PDF escaneado.");
                recognized.append(pageText).append('\n');
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (IOException e) {
                throw new IllegalArgumentException("O reconhecimento OCR não está disponível para este PDF.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalArgumentException("A leitura do PDF foi interrompida. Tente importar novamente.", e);
            }
        }
        if (recognized.toString().isBlank()) throw new IllegalArgumentException("Não consegui reconhecer texto neste PDF escaneado. Tente um PDF mais nítido.");
        return recognized.toString();
    }

    private ObjectNode normalizeJson(JsonNode input) {
        if (input == null || !input.isObject()) throw new IllegalArgumentException("O JSON precisa conter um objeto de currículo.");
        JsonNode root = input;
        if (root.path("jsonData").isTextual()) {
            try { root = mapper.readTree(root.path("jsonData").asText()); }
            catch (IOException e) { throw new IllegalArgumentException("O campo jsonData não contém JSON válido.", e); }
        } else if (root.path("data").isObject()) {
            root = root.path("data");
        } else if (root.path("resume").isObject()) {
            root = root.path("resume");
        }
        JsonNode basics = root.path("basics");
        ObjectNode out = mapper.createObjectNode();
        out.put("name", limit(first(root, basics, "name", "fullName", "full_name", "nome"), 160));
        out.put("headline", limit(first(root, basics, "headline", "label", "professionalTitle", "cargo", "title"), 160));
        out.put("email", limit(first(root, basics, "email", "e-mail"), 160));
        out.put("phone", limit(first(root, basics, "phone", "telefone", "mobile"), 60));
        out.put("location", limit(location(firstNode(root, basics, "location", "localizacao", "address")), 120));
        out.put("summary", limit(first(root, basics, "summary", "profile", "objective", "resumo", "about"), 1500));

        ArrayNode experience = out.putArray("experience");
        JsonNode jobs = firstArray(root, "experience", "experiences", "work", "employment");
        for (JsonNode item : jobs) {
            if (!item.isObject()) continue;
            ObjectNode job = experience.addObject();
            job.put("role", limit(first(item, item, "role", "position", "title", "cargo"), 140));
            job.put("company", limit(first(item, item, "company", "employer", "name", "organization"), 140));
            job.put("period", limit(period(item), 80));
            job.put("description", limit(joinText(firstNode(item, item, "description", "summary", "highlights", "responsibilities")), 1200));
        }

        ArrayNode education = out.putArray("education");
        JsonNode studies = firstArray(root, "education", "educations", "formacao");
        for (JsonNode item : studies) {
            if (!item.isObject()) continue;
            ObjectNode study = education.addObject();
            study.put("course", limit(first(item, item, "course", "studyType", "area", "degree", "title", "curso"), 160));
            study.put("institution", limit(first(item, item, "institution", "school", "organization", "name", "instituicao"), 160));
            study.put("period", limit(period(item), 80));
        }

        ArrayNode courses = out.putArray("courses");
        JsonNode rawCourses = firstArray(root, "courses", "certifications", "certificates", "trainings", "cursos", "certificacoes");
        for (JsonNode item : rawCourses) {
            ObjectNode course = courses.addObject();
            if (item.isTextual()) {
                course.put("course", limit(item.asText(), 160));
                course.put("institution", "");
                course.put("period", "");
            } else if (item.isObject()) {
                course.put("course", limit(first(item, item, "course", "name", "title", "certificate", "curso"), 160));
                course.put("institution", limit(first(item, item, "institution", "issuer", "organization", "school"), 160));
                course.put("period", limit(period(item), 80));
            } else courses.remove(courses.size() - 1);
        }

        ArrayNode projects = out.putArray("projects");
        JsonNode rawProjects = firstArray(root, "projects", "portfolio", "projetos", "selectedProjects");
        for (JsonNode item : rawProjects) {
            ObjectNode project = projects.addObject();
            if (item.isTextual()) {
                project.put("name", limit(item.asText(), 160));
                project.put("description", "");
                project.put("link", "");
            } else if (item.isObject()) {
                project.put("name", limit(first(item, item, "name", "title", "project", "projeto"), 160));
                project.put("description", limit(joinText(firstNode(item, item, "description", "summary", "highlights")), 1200));
                project.put("link", limit(first(item, item, "link", "url", "href", "website", "repository"), 500));
            } else projects.remove(projects.size() - 1);
        }

        ArrayNode skills = out.putArray("skills");
        JsonNode rawSkills = firstArray(root, "skills", "competencies", "habilidades");
        for (JsonNode skill : rawSkills) {
            if (skill.isTextual()) addText(skills, skill.asText());
            else if (skill.isObject()) {
                addText(skills, first(skill, skill, "name", "skill", "label"));
                JsonNode keywords = skill.path("keywords");
                if (keywords.isArray()) for (JsonNode word : keywords) if (word.isTextual()) addText(skills, word.asText());
            }
        }

        ArrayNode links = out.putArray("links");
        JsonNode rawLinks = firstArray(root, "links", "profiles", "social");
        for (JsonNode link : rawLinks) {
            if (link.isTextual()) addText(links, link.asText());
            else if (link.isObject()) addText(links, first(link, link, "url", "href", "link"));
        }
        String photo = first(root, basics, "photo", "image", "picture", "avatar");
        if (photo.matches("(?i)^data:image/(jpeg|png|webp);base64,[a-z0-9+/=]+$") && photo.length() <= 240_000) out.put("photo", photo);
        return out;
    }

    public ImportResult importStructured(String title, JsonNode data, List<String> warnings) {
        ObjectNode normalized = normalizeJson(data);
        String normalizedTitle = title == null || title.isBlank() ? "Currículo importado" : limit(title, 200);
        return new ImportResult(normalizedTitle, normalized, warnings == null ? List.of() : warnings);
    }

    private ObjectNode normalizePdfText(String text) {
        ObjectNode out = mapper.createObjectNode();
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).map(this::cleanLine).toList();
        String flattened = String.join("\n", lines);
        Matcher email = EMAIL.matcher(flattened);
        out.put("email", email.find() ? email.group().substring(0, Math.min(email.group().length(), 160)) : "");
        Matcher phone = PHONE.matcher(flattened);
        out.put("phone", phone.find() ? phone.group().trim().substring(0, Math.min(phone.group().trim().length(), 60)) : "");
        Matcher link = LINK.matcher(flattened);
        ArrayNode links = out.putArray("links");
        while (link.find() && links.size() < 20) addText(links, link.group().replaceAll("[),.;]+$", ""));

        int firstSection = lines.size();
        for (int i = 0; i < lines.size(); i++) if (section(lines.get(i)) != null) { firstSection = i; break; }
        int nameIndex = -1;
        for (int i = 0; i < Math.min(firstSection, 8); i++) {
            String line = lines.get(i);
            if (line.length() <= 80 && !EMAIL.matcher(line).find() && !PHONE.matcher(line).find() && !LINK.matcher(line).find() && line.matches("[\\p{L}\\p{M} .’'-]{2,80}")) {
                nameIndex = i;
                break;
            }
        }
        out.put("name", nameIndex >= 0 ? limit(lines.get(nameIndex), 160) : "");
        out.put("headline", nameIndex >= 0 && nameIndex + 1 < firstSection ? limit(lines.get(nameIndex + 1), 160) : "");
        out.put("location", "");

        List<String> summary = new ArrayList<>(), experience = new ArrayList<>(), education = new ArrayList<>(), courses = new ArrayList<>(), projects = new ArrayList<>(), skills = new ArrayList<>();
        List<String> active = null;
        for (String line : lines) {
            Section detected = section(line);
            if (detected != null) {
                active = switch (detected) { case SUMMARY -> summary; case EXPERIENCE -> experience; case EDUCATION -> education; case COURSES -> courses; case PROJECTS -> projects; case SKILLS -> skills; };
                continue;
            }
            Matcher inlineProjectsHeading = INLINE_PROJECT_HEADING.matcher(line);
            if (inlineProjectsHeading.find()) {
                active = projects;
                String beforeHeading = cleanProjectText(line.substring(0, inlineProjectsHeading.start()));
                String afterHeading = cleanProjectText(line.substring(inlineProjectsHeading.end()));
                if (!beforeHeading.isBlank()) projects.add(beforeHeading);
                if (!afterHeading.isBlank()) projects.add(afterHeading);
                continue;
            }
            if (active != null) active.add(line);
        }
        out.put("summary", limit(String.join(" ", summary), 1500));
        if (summary.isEmpty()) out.put("summary", limit(lines.stream().filter(line -> !EMAIL.matcher(line).find() && !PHONE.matcher(line).find()).skip(Math.max(0, nameIndex + 2L)).limit(3).reduce((a, b) -> a + " " + b).orElse(""), 1500));
        ArrayNode jobs = out.putArray("experience");
        for (List<String> block : splitEntries(experience)) {
            if (block.isEmpty()) continue;
            String period = "";
            List<String> content = new ArrayList<>();
            for (String line : block) {
                Matcher date = DATE_RANGE.matcher(line);
                if (date.find()) { period = date.group().trim(); String rest = (line.substring(0, date.start()) + " " + line.substring(date.end())).trim(); if (!rest.isBlank()) content.add(rest); }
                else content.add(line);
            }
            if (content.isEmpty()) continue;
            ObjectNode job = jobs.addObject();
            String first = content.get(0);
            String second = content.size() > 1 ? content.get(1) : "";
            boolean companyAppearsFirst = first.contains("|") && !second.contains("|");
            job.put("role", limit(companyAppearsFirst ? second : first, 140));
            job.put("company", limit(companyAppearsFirst ? first : second, 140));
            job.put("period", limit(period, 80));
            job.put("description", limit(String.join(" ", content.subList(Math.min(2, content.size()), content.size())), 1200));
        }
        ArrayNode studies = out.putArray("education");
        for (EducationEntry entry : parseEducationEntries(education)) {
            ObjectNode study = studies.addObject();
            study.put("course", limit(entry.course(), 160));
            study.put("institution", limit(entry.institution(), 160));
            study.put("period", limit(entry.period(), 80));
        }
        ArrayNode courseArray = out.putArray("courses");
        for (EducationEntry entry : parseEducationEntries(courses)) {
            ObjectNode course = courseArray.addObject();
            course.put("course", limit(entry.course(), 160));
            course.put("institution", limit(entry.institution(), 160));
            course.put("period", limit(entry.period(), 80));
        }
        ArrayNode projectArray = out.putArray("projects");
        for (ProjectEntry entry : parseProjects(projects)) {
            ObjectNode project = projectArray.addObject();
            project.put("name", limit(entry.name(), 160));
            project.put("description", limit(entry.description(), 1200));
            project.put("link", limit(entry.link(), 500));
        }
        ArrayNode skillArray = out.putArray("skills");
        for (String line : skills) for (String value : line.split("[,;|•·]")) addText(skillArray, value);
        return out;
    }

    private List<List<String>> splitEntries(List<String> lines) {
        if (lines.isEmpty()) return List.of();
        List<List<String>> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        boolean seenDate = false;
        for (String line : lines) {
            boolean hasDate = DATE_RANGE.matcher(line).find();
            if (hasDate && seenDate && !current.isEmpty()) { result.add(current); current = new ArrayList<>(); seenDate = false; }
            current.add(line);
            if (hasDate) seenDate = true;
        }
        if (!current.isEmpty()) result.add(current);
        if (result.isEmpty()) return List.of();
        return result;
    }

    private List<EducationEntry> parseEducationEntries(List<String> lines) {
        if (lines.isEmpty()) return List.of();
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            boolean startsCourse = EDUCATION_START.matcher(line).find()
                    || line.matches("(?iu)^.{2,160}\\s+(?:—|–|\\||:)\\s+.{2,160}$")
                    && current.stream().anyMatch(previous -> previous.matches("(?iu)^.{2,160}\\s+(?:—|–|\\||:)\\s+.{2,160}$"));
            boolean hasDate = DATE_RANGE.matcher(line).find();
            if (!current.isEmpty() && (startsCourse || hasDate && current.stream().anyMatch(s -> DATE_RANGE.matcher(s).find()))) {
                blocks.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
        }
        if (!current.isEmpty()) blocks.add(current);

        List<EducationEntry> entries = new ArrayList<>();
        for (List<String> block : blocks) {
            String course = "", institution = "", period = "";
            for (String source : block) {
                String line = source;
                Matcher status = EDUCATION_STATUS.matcher(line);
                if (status.find()) {
                    period = status.group(1).trim();
                    line = line.substring(0, status.start()).trim();
                }
                Matcher date = DATE_RANGE.matcher(line);
                if (date.find()) {
                    period = date.group().trim();
                    line = (line.substring(0, date.start()) + " " + line.substring(date.end())).trim();
                }
                if (line.isBlank()) continue;
                String[] pair = line.split("\\s+(?:—|–|\\||:)\\s+", 2);
                if (pair.length == 2) {
                    course = pair[0].trim();
                    institution = pair[1].trim();
                } else if (course.isBlank()) course = line;
                else if (institution.isBlank()) institution = line;
                else institution = institution + " " + line;
            }
            if (!course.isBlank() || !institution.isBlank()) entries.add(new EducationEntry(course, institution, period));
        }
        return entries;
    }

    private record EducationEntry(String course, String institution, String period) {}

    private List<ProjectEntry> parseProjects(List<String> lines) {
        String content = String.join(" ", lines);
        List<ProjectMarker> markers = new ArrayList<>();
        Matcher colon = Pattern.compile(":").matcher(content);
        while (colon.find()) {
            if (content.startsWith("//", colon.end())) continue;
            ProjectTitle title = projectTitleBeforeColon(content, colon.start());
            if (title != null && (markers.isEmpty() || title.start() > markers.get(markers.size() - 1).start())) {
                markers.add(new ProjectMarker(title.start(), colon.end(), title.name()));
            }
        }
        if (!markers.isEmpty()) {
            List<ProjectEntry> result = new ArrayList<>();
            for (int i = 0; i < markers.size(); i++) {
                ProjectMarker marker = markers.get(i);
                int end = i + 1 < markers.size() ? markers.get(i + 1).start() : content.length();
                String details = cleanProjectText(content.substring(marker.descriptionStart(), end));
                Matcher linkMatcher = LINK.matcher(details);
                String link = linkMatcher.find() ? linkMatcher.group().replaceAll("[),.;]+$", "") : "";
                if (!link.isBlank()) details = details.replace(linkMatcher.group(), " ");
                result.add(new ProjectEntry(marker.name(), cleanProjectText(details), link));
            }
            return result;
        }

        List<ProjectEntry> result = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = LINK.matcher(line);
            if (matcher.find()) {
                String link = matcher.group().replaceAll("[),.;]+$", "");
                String inlineName = (line.substring(0, matcher.start()) + " " + line.substring(matcher.end())).trim();
                List<String> text = new ArrayList<>(pending);
                if (!inlineName.isBlank()) text.add(inlineName);
                if (text.isEmpty()) text.add(link);
                String name = text.get(0);
                String description = String.join(" ", text.subList(1, text.size()));
                result.add(new ProjectEntry(name, description, link));
                pending.clear();
            } else pending.add(line);
        }
        if (!pending.isEmpty()) {
            result.add(new ProjectEntry(pending.get(0), String.join(" ", pending.subList(1, pending.size())), ""));
        }
        return result;
    }

    private ProjectTitle projectTitleBeforeColon(String content, int colonIndex) {
        int from = Math.max(0, colonIndex - 100);
        String prefix = content.substring(from, colonIndex);
        Matcher words = Pattern.compile("\\S+").matcher(prefix);
        List<String> tokens = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        while (words.find()) { tokens.add(words.group()); starts.add(words.start()); }
        int first = tokens.size();
        while (first > 0 && first >= tokens.size() - 7 && isProjectTitleToken(tokens.get(first - 1))) first--;
        if (first == tokens.size()) return null;
        String name = String.join(" ", tokens.subList(first, tokens.size())).replaceAll("^[^\\p{L}\\p{N}(]+|[^\\p{L}\\p{N})]+$", "").trim();
        if (name.isBlank() || name.length() > 70) return null;
        return new ProjectTitle(from + starts.get(first), colonIndex + 1, name);
    }

    private boolean isProjectTitleToken(String token) {
        if (token.endsWith(".")) return false;
        String normalized = token.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N})]+$", "");
        if (normalized.isBlank()) return false;
        if (Set.of("and", "of", "the", "for", "to", "de", "da", "do", "e").contains(normalized.toLowerCase(Locale.ROOT))) return true;
        int firstLetter = normalized.codePoints().filter(Character::isLetter).findFirst().orElse(-1);
        return firstLetter >= 0 && Character.isUpperCase(firstLetter);
    }

    private String cleanProjectText(String value) {
        return value.replaceAll("^[\\s«»=—–-]+", "").replaceAll("\\s*[—–]\\s*=m\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private record ProjectTitle(int start, int descriptionStart, String name) {}
    private record ProjectMarker(int start, int descriptionStart, String name) {}

    private record ProjectEntry(String name, String description, String link) {}

    private Section section(String line) {
        String key = normalizeHeading(line);
        if (SUMMARY_HEADINGS.contains(key)) return Section.SUMMARY;
        if (EXPERIENCE_HEADINGS.contains(key)) return Section.EXPERIENCE;
        if (EDUCATION_HEADINGS.contains(key)) return Section.EDUCATION;
        if (COURSE_HEADINGS.contains(key)) return Section.COURSES;
        if (PROJECT_HEADINGS.contains(key)) return Section.PROJECTS;
        if (SKILLS_HEADINGS.contains(key)) return Section.SKILLS;
        return null;
    }

    private String normalizeHeading(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", "").trim();
    }

    private String cleanLine(String value) { return value.replaceAll("^[\\s•●▪|–—-]+", "").replaceAll("\\s+", " ").trim(); }

    private String safeTitle(String filename) {
        if (filename == null || filename.isBlank()) return "Currículo importado";
        String safe = filename.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceFirst("(?i)\\.(pdf|json)$", "").trim();
        return limit(safe.isBlank() ? "Currículo importado" : safe, 200);
    }

    private String nameFromFilename(String filename) {
        String candidate = filename
                .replaceFirst("(?iu)[ _-]+(?:resume|curr[ií]culo|curriculum(?: vitae)?|cv)(?:[ _-].*)?$", "")
                .replaceAll("[_-]+", " ").trim();
        if (candidate.matches("(?iu)[\\p{L}\\p{M}]+(?:[ .’'-][\\p{L}\\p{M}]+){1,5}")) return candidate;
        return "";
    }

    private String first(JsonNode primary, JsonNode secondary, String... keys) {
        for (String key : keys) {
            JsonNode value = primary.path(key);
            if (value.isValueNode() && !value.isNull() && !value.asText().isBlank()) return value.asText().trim();
            value = secondary.path(key);
            if (value.isValueNode() && !value.isNull() && !value.asText().isBlank()) return value.asText().trim();
        }
        return "";
    }

    private JsonNode firstNode(JsonNode primary, JsonNode secondary, String... keys) {
        for (String key : keys) {
            JsonNode value = primary.path(key);
            if (!value.isMissingNode() && !value.isNull()) return value;
            value = secondary.path(key);
            if (!value.isMissingNode() && !value.isNull()) return value;
        }
        return mapper.getNodeFactory().nullNode();
    }

    private JsonNode firstArray(JsonNode root, String... keys) {
        for (String key : keys) if (root.path(key).isArray()) return root.path(key);
        return mapper.createArrayNode();
    }

    private String location(JsonNode node) {
        if (node.isTextual()) return node.asText().trim();
        if (!node.isObject()) return "";
        List<String> parts = new ArrayList<>();
        for (String key : new String[]{"city", "region", "state", "country", "countryCode"}) if (node.path(key).isTextual() && !node.path(key).asText().isBlank()) parts.add(node.path(key).asText().trim());
        return String.join(", ", parts);
    }

    private String period(JsonNode item) {
        String existing = first(item, item, "period", "date");
        if (!existing.isBlank()) return existing;
        String start = first(item, item, "startDate", "start", "from");
        String end = first(item, item, "endDate", "end", "to");
        if (start.isBlank()) return end;
        return start + " — " + (end.isBlank() ? "Atual" : end);
    }

    private String joinText(JsonNode value) {
        if (value.isTextual()) return value.asText();
        if (value.isArray()) { List<String> parts = new ArrayList<>(); value.forEach(item -> { if (item.isTextual()) parts.add(item.asText()); }); return String.join("; ", parts); }
        return "";
    }

    private void addText(ArrayNode array, String value) {
        String clean = value == null ? "" : value.trim();
        if (!clean.isBlank() && array.size() < 100) array.add(limit(clean, 240));
    }

    private String limit(String value, int length) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= length ? clean : clean.substring(0, length);
    }

    private enum Section { SUMMARY, EXPERIENCE, EDUCATION, COURSES, PROJECTS, SKILLS }

    public record ImportResult(String title, ObjectNode data, List<String> warnings) { }
}
