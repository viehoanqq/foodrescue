package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/** Reactive HTTP client for Google Gemini generateContent REST API with ordered model fallback and safe error mapping. */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final List<String> modelChain;
    private final Duration timeout;

    public GeminiClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-3.5-flash-lite}") String primaryModel,
            @Value("${app.gemini.fallback-model:gemini-3.1-flash-lite,gemini-flash-lite-latest}") String fallbackModels,
            @Value("${app.gemini.timeout-seconds:20}") long timeoutSeconds) {
        this.webClient = webClientBuilder.build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelChain = buildModelChain(primaryModel, fallbackModels);
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
    }

    public Mono<GeminiReply> generateContent(ObjectNode payload) {
        if (apiKey.isBlank()) {
            return Mono.error(new ChatException(503, "AI_NOT_CONFIGURED", "Chưa cấu hình GEMINI_API_KEY cho trợ lý AI"));
        }
        return tryModel(0, payload)
                .onErrorMap(this::mapError);
    }

    private Mono<GeminiReply> tryModel(int index, ObjectNode payload) {
        String model = modelChain.get(index);
        return invokeModel(model, payload)
                .onErrorResume(ex -> {
                    if (index + 1 < modelChain.size() && shouldFallback(ex)) {
                        log.warn("Gemini model {} failed ({}), falling back to {}",
                                model, describeFailure(ex), modelChain.get(index + 1));
                        return tryModel(index + 1, payload);
                    }
                    log.warn("Gemini model {} failed ({})", model, describeFailure(ex));
                    return Mono.error(ex);
                });
    }

    private Mono<GeminiReply> invokeModel(String model, ObjectNode payload) {
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(body -> new GeminiReply(model, body));
    }

    private boolean shouldFallback(Throwable ex) {
        if (ex instanceof WebClientResponseException responseEx) {
            int status = responseEx.getStatusCode().value();
            return status == 429 || status == 503 || status == 404 || status == 500 || status == 502;
        }
        return ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException;
    }

    private ChatException mapError(Throwable ex) {
        if (ex instanceof ChatException chatEx) return chatEx;
        if (ex instanceof WebClientResponseException responseEx) {
            int code = responseEx.getStatusCode().value();
            if (code == 429) {
                return new ChatException(429, "AI_RATE_LIMITED", "Trợ lý AI đang hết lượt miễn phí tạm thời, vui lòng thử lại sau.");
            }
            if (code == 401 || code == 403) {
                return new ChatException(502, "AI_AUTH_ERROR", "Khóa API Gemini không hợp lệ hoặc bị từ chối quyền.");
            }
            if (code == 503) {
                return new ChatException(503, "AI_UNAVAILABLE", "Dịch vụ Gemini đang quá tải tạm thời. Vui lòng thử lại sau.");
            }
            return new ChatException(502, "AI_UPSTREAM_ERROR", "Không thể kết nối tới dịch vụ Gemini (HTTP " + code + ").");
        }
        return new ChatException(503, "AI_UNAVAILABLE", "Trợ lý AI tạm thời không phản hồi. Vui lòng thử lại sau.");
    }

    private String describeFailure(Throwable ex) {
        if (ex instanceof WebClientResponseException responseEx) {
            return "HTTP " + responseEx.getStatusCode().value();
        }
        return ex.getClass().getSimpleName();
    }

    private static List<String> buildModelChain(String primaryModel, String fallbackModels) {
        Set<String> models = new LinkedHashSet<>();
        if (primaryModel != null && !primaryModel.isBlank()) {
            models.add(primaryModel.trim());
        } else {
            models.add("gemini-3.5-flash-lite");
        }
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String item : fallbackModels.split(",")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) models.add(trimmed);
            }
        }
        return new ArrayList<>(models);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://generativelanguage.googleapis.com/v1beta";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record GeminiReply(String model, JsonNode body) {}
}
