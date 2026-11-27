package com.foodrescue.gateway.chat;

import com.foodrescue.gateway.chat.ChatDtos.ChatRequest;
import com.foodrescue.gateway.chat.ChatDtos.ChatResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
public class ChatController {
    private final GeminiChatService chatService;

    public ChatController(GeminiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/api/chat")
    public Mono<ChatResponse> chat(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) ChatRequest request) {
        return chatService.chat(authorization, request);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorBody> handleChatError(ChatException ex, ServerHttpRequest req) {
        return ResponseEntity.status(ex.status()).body(new ErrorBody(
                ex.code(),
                ex.getMessage(),
                OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                req.getURI().getPath()));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorBody> handleUpstreamError(WebClientResponseException ex, ServerHttpRequest req) {
        int status = ex.getStatusCode().value();
        int mapped = (status == 429 || status == 503) ? status : 502;
        return ResponseEntity.status(mapped).body(new ErrorBody(
                "AI_UPSTREAM_ERROR",
                "Trợ lý AI tạm thời không phản hồi (HTTP " + status + "). Vui lòng thử lại sau.",
                OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                req.getURI().getPath()));
    }

    public record ErrorBody(String code, String message, OffsetDateTime timestamp, String path) {}
}
