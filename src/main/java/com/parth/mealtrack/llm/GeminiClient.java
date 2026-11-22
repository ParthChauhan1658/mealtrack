package com.parth.mealtrack.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.parth.mealtrack.config.MealtrackProperties;
import java.net.http.HttpRequest;

/** Google Gemini generateContent API. */
public class GeminiClient extends AbstractHttpLlmClient {
    static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";
    static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GeminiClient(MealtrackProperties.Llm cfg) {
        super(cfg);
    }

    @Override
    protected HttpRequest.Builder request(String system, String userText) {
        String model = cfg.model().isBlank() ? DEFAULT_MODEL : cfg.model();
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", userText);
        body.putObject("generationConfig").put("responseMimeType", "application/json").put("maxOutputTokens", 100);
        return HttpRequest.newBuilder(uri(cfg.baseUrl(), DEFAULT_BASE, "/v1beta/models/" + model + ":generateContent"))
                .header("content-type", "application/json")
                .header("x-goog-api-key", cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    }

    @Override
    protected String extractText(String responseBody) {
        try {
            JsonNode text = MAPPER.readTree(responseBody).path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text");
            if (!text.isTextual()) throw new LlmException("Gemini response has no text", false);
            return text.asText();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Gemini response is not valid JSON", false, e);
        }
    }
}
