package com.parth.mealtrack.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.parth.mealtrack.config.MealtrackProperties;
import java.net.http.HttpRequest;

/** Anthropic Messages API. */
public class ClaudeClient extends AbstractHttpLlmClient {
    static final String DEFAULT_BASE = "https://api.anthropic.com";
    static final String DEFAULT_MODEL = "claude-haiku-5-5";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ClaudeClient(MealtrackProperties.Llm cfg) {
        super(cfg);
    }

    @Override
    protected HttpRequest.Builder request(String system, String userText) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", cfg.model().isBlank() ? DEFAULT_MODEL : cfg.model());
        body.put("max_tokens", 100);
        body.put("system", system);
        body.putArray("messages").addObject().put("role", "user").put("content", userText);
        return HttpRequest.newBuilder(uri(cfg.baseUrl(), DEFAULT_BASE, "/v1/messages"))
                .header("content-type", "application/json")
                .header("x-api-key", cfg.apiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    }

    @Override
    protected String extractText(String responseBody) {
        try {
            JsonNode text = MAPPER.readTree(responseBody).path("content").path(0).path("text");
            if (!text.isTextual()) throw new LlmException("Claude response has no text", false);
            return text.asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Claude response is not valid JSON", false, e);
        }
    }
}
