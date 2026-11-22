package com.parth.mealtrack.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.parth.mealtrack.config.MealtrackProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Talks to a local stub server, so no real API key or network is needed. */
class LlmHttpClientTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    static final String ANSWER = "{\\\"mealsServed\\\":120,\\\"date\\\":null,\\\"confidence\\\":\\\"HIGH\\\"}";

    HttpServer server;
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicReference<String> responseBody = new AtomicReference<>("");
    final AtomicReference<String> seenKeyHeader = new AtomicReference<>();
    final AtomicReference<String> seenRequestBody = new AtomicReference<>();
    final AtomicReference<String> seenPathAndQuery = new AtomicReference<>();
    volatile long delayMs = 0;
    final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requests.incrementAndGet();
            seenKeyHeader.set(firstNonNull(ex.getRequestHeaders().getFirst("x-api-key"),
                    ex.getRequestHeaders().getFirst("x-goog-api-key")));
            seenRequestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            seenPathAndQuery.set(ex.getRequestURI().toString());
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), bytes.length);
            try (var os = ex.getResponseBody()) {
                os.write(bytes);
            } catch (Exception ignored) {
                // the client may already have timed out and gone away
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String firstNonNull(String a, String b) { return a != null ? a : b; }

    private MealtrackProperties.Llm cfg(String key, int timeoutMs) {
        return new MealtrackProperties.Llm("x", key, "", "http://127.0.0.1:" + server.getAddress().getPort(),
                timeoutMs, 2, 1);
    }

    private void claudeAnswers() {
        responseBody.set("{\"content\":[{\"type\":\"text\",\"text\":\"" + ANSWER + "\"}]}");
    }

    private void geminiAnswers() {
        responseBody.set("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + ANSWER + "\"}]}}]}");
    }

    @Test
    void claudeSuccessSendsKeyInHeaderAndMessageAsDelimitedData() {
        claudeAnswers();
        var p = new ClaudeClient(cfg("secret-key", 2000)).parse("aaj 120 bachche", TODAY);
        assertEquals(120, p.mealsServed());
        assertEquals("secret-key", seenKeyHeader.get());
        assertEquals("/v1/messages", seenPathAndQuery.get());
        assertTrue(seenRequestBody.get().contains("<message>"));
        assertTrue(seenRequestBody.get().contains("aaj 120 bachche"));
        assertTrue(seenRequestBody.get().contains("claude-haiku-5-5"));
    }

    @Test
    void geminiSuccessKeepsKeyOutOfTheUrl() {
        geminiAnswers();
        var p = new GeminiClient(cfg("secret-key", 2000)).parse("served 120", TODAY);
        assertEquals(120, p.mealsServed());
        assertEquals("secret-key", seenKeyHeader.get());
        assertFalse(seenPathAndQuery.get().contains("secret-key"));
        assertTrue(seenPathAndQuery.get().endsWith(":generateContent"));
    }

    @Test
    void serverErrorIsRetryable() {
        status.set(503);
        var e = assertThrows(LlmException.class, () -> new ClaudeClient(cfg("k", 2000)).parse("x", TODAY));
        assertTrue(e.isRetryable());
    }

    @Test
    void rateLimitIsRetryable() {
        status.set(429);
        assertTrue(assertThrows(LlmException.class, () -> new GeminiClient(cfg("k", 2000)).parse("x", TODAY)).isRetryable());
    }

    @Test
    void badKeyIsNotRetryable() {
        status.set(401);
        var e = assertThrows(LlmException.class, () -> new ClaudeClient(cfg("wrong", 2000)).parse("x", TODAY));
        assertFalse(e.isRetryable());
    }

    @Test
    void slowProviderTimesOutAndIsRetryable() {
        claudeAnswers();
        delayMs = 1500;
        long start = System.nanoTime();
        var e = assertThrows(LlmException.class, () -> new ClaudeClient(cfg("k", 200)).parse("x", TODAY));
        assertTrue(e.isRetryable());
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1200, "should give up at the timeout, not wait for the reply");
    }

    @Test
    void missingKeyFailsFastWithoutCallingTheProvider() {
        var e = assertThrows(LlmException.class, () -> new ClaudeClient(cfg("", 2000)).parse("x", TODAY));
        assertFalse(e.isRetryable());
        assertEquals(0, requests.get());
    }

    @Test
    void garbageFromProviderIsNotRetryable() {
        responseBody.set("{\"content\":[{\"type\":\"text\",\"text\":\"sorry, I cannot help\"}]}");
        var e = assertThrows(LlmException.class, () -> new ClaudeClient(cfg("k", 2000)).parse("x", TODAY));
        assertFalse(e.isRetryable());
    }

    @Test
    void unexpectedEnvelopeIsNotRetryable() {
        responseBody.set("{\"unexpected\":true}");
        assertFalse(assertThrows(LlmException.class, () -> new GeminiClient(cfg("k", 2000)).parse("x", TODAY)).isRetryable());
    }

    @Test
    void connectionRefusedIsRetryable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        var cfg = new MealtrackProperties.Llm("x", "k", "", "http://127.0.0.1:" + port, 500, 2, 1);
        assertTrue(assertThrows(LlmException.class, () -> new ClaudeClient(cfg).parse("x", TODAY)).isRetryable());
    }
}
