package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodrescue.gateway.chat.ChatDtos.ChatRequest;
import com.foodrescue.gateway.chat.ChatDtos.ChatResponse;
import com.foodrescue.gateway.chat.ChatDtos.Suggestion;
import com.foodrescue.gateway.chat.ChatToolExecutor.UserSession;
import com.foodrescue.gateway.chat.GeminiClient.GeminiReply;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeminiChatServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsBlankAndOversizedMessages() {
        GeminiChatService service = new GeminiChatService(
                mock(GeminiClient.class), mock(ChatToolExecutor.class), mapper);

        ChatException blank = assertThrows(ChatException.class,
                () -> service.chat(null, new ChatRequest("   ", null, null, null)).block());
        assertEquals(400, blank.status());

        ChatException tooLong = assertThrows(ChatException.class,
                () -> service.chat(null, new ChatRequest("a".repeat(1001), null, null, null)).block());
        assertEquals(400, tooLong.status());
    }

    @Test
    void anonymousUserOnlyReceivesPublicStoreTools() throws Exception {
        ChatToolExecutor realExecutor = new ChatToolExecutor(
                WebClient.builder(), mapper, "http://localhost:8082", "http://localhost:8083", "http://localhost:8085");
        GeminiClient gemini = mock(GeminiClient.class);
        List<ObjectNode> capturedPayloads = new ArrayList<>();
        JsonNode finalBody = mapper.readTree("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"Bạn hãy đăng nhập để xem đơn hàng nhé."}]}}]}""");
        when(gemini.generateContent(any())).thenAnswer(inv -> {
            capturedPayloads.add(((ObjectNode) inv.getArgument(0)).deepCopy());
            return Mono.just(new GeminiReply("gemini-3-flash-preview", finalBody));
        });

        GeminiChatService service = new GeminiChatService(gemini, realExecutor, mapper);
        ChatResponse response = service.chat(null, new ChatRequest("Tôi đã tiêu bao nhiêu?", null, null, null)).block();

        assertNotNull(response);
        assertFalse(response.authenticated());
        assertEquals("Bạn hãy đăng nhập để xem đơn hàng nhé.", response.reply());
        String sysPrompt = capturedPayloads.get(0).path("systemInstruction").path("parts").path(0).path("text").asText();
        assertTrue(sysPrompt.contains("CHỈ hỗ trợ các chủ đề liên quan đến FoodRescue"));

        ArrayNode declarations = (ArrayNode) capturedPayloads.get(0).path("tools").path(0).path("functionDeclarations");
        List<String> names = new ArrayList<>();
        declarations.forEach(d -> names.add(d.get("name").asText()));
        assertTrue(names.contains("search_stores"));
        assertFalse(names.contains("analyze_my_orders"));
        assertFalse(names.contains("get_my_cart"));

        // Defense-in-depth: direct execution of customer tool by guest is rejected
        JsonNode denied = realExecutor.execute(
                UserSession.anonymous(null, null), "analyze_my_orders", mapper.createObjectNode(), new ArrayList<>()).block();
        assertEquals("AUTH_REQUIRED", denied.get("errorCode").asText());
    }

    @Test
    void customerMultiTurnToolCallPreservesThoughtSignatureAndSkipsThoughtParts() throws Exception {
        ChatToolExecutor executor = mock(ChatToolExecutor.class);
        UserSession customer = new UserSession(true, "Bearer tok", 5L, "CUSTOMER", "Hoàng", "h@example.com",
                "Q1", 10.77, 106.70, mapper.createObjectNode());
        when(executor.resolveSession("Bearer tok", null, null)).thenReturn(Mono.just(customer));
        when(executor.buildToolDeclarations(customer)).thenReturn(mapper.createArrayNode());
        when(executor.execute(eq(customer), eq("analyze_my_orders"), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<Suggestion> suggestions = inv.getArgument(3);
            suggestions.add(new Suggestion("ORDER", 99L, "Đơn FR-099", "Bánh mì An Phát · 60000 đ", "/me/orders/99"));
            return Mono.just(mapper.readTree("{\"totalOrders\":2,\"totalSpentPaidVnd\":95000}"));
        });

        JsonNode turn1 = mapper.readTree("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"functionCall":{"name":"analyze_my_orders","args":{"month":11,"year":2026},"id":"call_1"},
                   "thoughtSignature":"sig-xyz-123"}
                ]}}]}""");
        JsonNode turn2 = mapper.readTree("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"thought":true,"text":"Internal reasoning should not leak"},
                  {"text":"Tháng 11 bạn có 2 đơn với tổng chi tiêu 95.000 đ."}
                ]}}]}""");

        GeminiClient gemini = mock(GeminiClient.class);
        AtomicInteger calls = new AtomicInteger(0);
        List<ObjectNode> payloads = new ArrayList<>();
        when(gemini.generateContent(any())).thenAnswer(inv -> {
            payloads.add(((ObjectNode) inv.getArgument(0)).deepCopy());
            return Mono.just(new GeminiReply("gemini-3-flash-preview", calls.getAndIncrement() == 0 ? turn1 : turn2));
        });

        GeminiChatService service = new GeminiChatService(gemini, executor, mapper);
        ChatResponse response = service.chat("Bearer tok", new ChatRequest("Tháng này tôi tiêu bao nhiêu?", null, null, null)).block();

        assertNotNull(response);
        assertTrue(response.authenticated());
        assertEquals("Tháng 11 bạn có 2 đơn với tổng chi tiêu 95.000 đ.", response.reply());
        assertEquals(List.of("analyze_my_orders"), response.toolsUsed());
        assertEquals(1, response.suggestions().size());

        // Verify turn 2 payload kept thoughtSignature and functionResponse id
        JsonNode secondPayloadContents = payloads.get(1).get("contents");
        assertEquals(3, secondPayloadContents.size());
        assertEquals("sig-xyz-123", secondPayloadContents.get(1).get("parts").get(0).get("thoughtSignature").asText());
        JsonNode fnResp = secondPayloadContents.get(2).get("parts").get(0).get("functionResponse");
        assertEquals("analyze_my_orders", fnResp.get("name").asText());
        assertEquals("call_1", fnResp.get("id").asText());
        assertEquals(95000, fnResp.get("response").get("result").get("totalSpentPaidVnd").asInt());
    }
}
