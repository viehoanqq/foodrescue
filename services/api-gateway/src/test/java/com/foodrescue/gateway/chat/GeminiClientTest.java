package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class GeminiClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fallsBackToNextModelWhenPrimaryReturns503() {
        List<String> requestedUrls = new ArrayList<>();
        AtomicInteger call = new AtomicInteger(0);
        ExchangeFunction exchange = request -> {
            requestedUrls.add(request.url().toString());
            if (call.getAndIncrement() == 0) {
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":{\"code\":503}}")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}")
                    .build());
        };

        GeminiClient client = new GeminiClient(
                WebClient.builder().exchangeFunction(exchange),
                "https://example.test/v1beta",
                "test-key",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite,gemini-flash-lite-latest",
                10);

        ObjectNode payload = mapper.createObjectNode();
        GeminiClient.GeminiReply reply = client.generateContent(payload).block();

        assertNotNull(reply);
        assertEquals("gemini-3.1-flash-lite", reply.model());
        assertEquals(2, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("/models/gemini-3.5-flash-lite:generateContent"));
        assertTrue(requestedUrls.get(1).contains("/models/gemini-3.1-flash-lite:generateContent"));
    }

    @Test
    void maps503ToChatExceptionWhenAllModelsFail() {
        ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":{\"code\":503}}")
                .build());

        GeminiClient client = new GeminiClient(
                WebClient.builder().exchangeFunction(exchange),
                "https://example.test/v1beta",
                "test-key",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                10);

        ChatException ex = assertThrows(ChatException.class,
                () -> client.generateContent(mapper.createObjectNode()).block());

        assertEquals(503, ex.status());
        assertEquals("AI_UNAVAILABLE", ex.code());
    }
}
