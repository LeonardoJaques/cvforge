package com.cvforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class OpenRouterClient {
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    public OpenRouterClient(ObjectMapper mapper,
                            @Value("${openrouter.api-key:}") String apiKey,
                            @Value("${openrouter.model:openrouter/free}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "openrouter/free" : model.trim();
    }

    public String complete(String system, String user, int maxTokens) { return complete(system, user, maxTokens, model); }
    public String complete(String system, String user, int maxTokens, String requestedModel) {
        if (apiKey.isBlank()) throw new IllegalStateException("A análise com IA não está configurada no backend.");
        ObjectNode body = mapper.createObjectNode();
        body.put("model", requestedModel == null || requestedModel.isBlank() ? model : requestedModel.trim());
        body.put("temperature", 0.1);
        body.put("max_tokens", maxTokens);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        final String requestBody;
        try { requestBody = mapper.writeValueAsString(body); }
        catch (IOException e) { throw new IllegalStateException("Não foi possível preparar a solicitação de IA.", e); }

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://jaquesprojetos.com.br/cvforge")
                .header("X-Title", "CVForge")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = sendWithServerErrorRetry(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 401 || response.statusCode() == 403) throw new IllegalStateException("A chave ou as permissões do OpenRouter não autorizam esta solicitação.");
                if (response.statusCode() == 429) throw new IllegalStateException("O limite de solicitações do OpenRouter foi atingido. Tente novamente em alguns instantes.");
                throw new IllegalStateException("O OpenRouter não concluiu a solicitação (HTTP " + response.statusCode() + ").");
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) {
                throw new IllegalStateException("A resposta da IA ficou incompleta. Tente novamente ou reduza o texto da vaga.");
            }
            JsonNode content = choice.path("message").path("content");
            if (content.isTextual()) return content.asText().trim();
            if (content.isArray()) {
                StringBuilder joined = new StringBuilder();
                content.forEach(part -> { if (part.path("text").isTextual()) joined.append(part.path("text").asText()); });
                if (!joined.isEmpty()) return joined.toString().trim();
            }
            if (content.isObject()) {
                JsonNode text = content.path("text");
                if (text.isTextual() && !text.asText().isBlank()) return text.asText().trim();
                JsonNode nestedContent = content.path("content");
                if (nestedContent.isTextual() && !nestedContent.asText().isBlank()) return nestedContent.asText().trim();
                if (!content.isEmpty()) return mapper.writeValueAsString(content).trim();
            }
            throw new IllegalStateException("O OpenRouter retornou uma resposta vazia.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A solicitação ao OpenRouter foi interrompida.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível conectar ao OpenRouter.", e);
        }
    }

    private HttpResponse<String> sendWithServerErrorRetry(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 500 && response.statusCode() < 600) {
            Thread.sleep(500);
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        }
        return response;
    }
}
